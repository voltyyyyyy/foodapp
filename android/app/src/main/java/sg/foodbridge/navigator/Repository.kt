package sg.foodbridge.navigator

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

val Context.store by preferencesDataStore("foodbridge_local")

fun accessMethodLabel(value: String) = when (value) {
    "referral_required" -> "Referral required"
    "application_required" -> "Application required"
    "provider_assessment" -> "Provider assessment required"
    "unverified" -> "Current access requirements unverified"
    else -> "Contact provider to confirm access"
}

fun accessMethodDetail(value: String) = when (value) {
    "referral_required" -> "Referral required. A social worker, caseworker, or partner agency must refer or apply on your behalf. Contact the provider to confirm the current process."
    "application_required" -> "An application is required. Use the provider's application process and confirm current eligibility directly."
    "provider_assessment" -> "The provider assesses eligibility. Contact them to ask about current intake and assessment steps."
    "unverified" -> "This food-support listing or its access requirements have not been independently confirmed as current. Contact the provider before relying on it."
    else -> "Referral or application requirements are not confirmed. Contact the provider before travelling or relying on this listing."
}

fun collectionLocationLabel(value: String) = when (value) {
    "collection_site", "confirmed_collection_site" -> "Confirmed collection/service location"
    "coordination_site" -> "Coordination site — not a confirmed walk-in collection point"
    else -> "No confirmed collection-site address"
}

fun directionsButtonLabel(value: String) = when (value) {
    "collection_site", "confirmed_collection_site" -> "Get directions to collection site"
    "coordination_site" -> "Get directions to coordination site"
    else -> "Get directions to the listed address"
}

data class Service(val json: JSONObject) {
    val id get() = json.getString("id")
    val name get() = json.getString("name")
    val organisationName get() = json.optString("organisation_name", name)
    val type get() = json.getString("support_type")
    val address get() = json.getString("address")
    val latitude get() = json.getDouble("latitude")
    val longitude get() = json.getDouble("longitude")
    val distance get() = json.optDouble("distance_km", Double.NaN)
    val demo get() = json.optBoolean("demo")
    val operating: String get() {
        if (!json.optBoolean("cached")) return json.optString("operating_status", "UNKNOWN")
        val local = ZonedDateTime.now(ZoneId.of("Asia/Singapore"))
        val schedules = json.optJSONObject("exceptions")?.optJSONArray(local.toLocalDate().toString()) ?: json.optJSONArray("schedules")
        if (schedules == null || schedules.length() == 0) return "UNKNOWN"
        val day = local.dayOfWeek.value - 1
        val time = local.toLocalTime().toString().take(5)
        return if (schedules.objects().any { schedule ->
            val days = schedule.getJSONArray("days").let { a -> (0 until a.length()).map(a::getInt) }
            val start = schedule.getString("start"); val end = schedule.getString("end")
            if (start < end) day in days && time >= start && time < end
            else (day in days && time >= start) || ((day + 6) % 7 in days && time < end)
        }) "OPEN" else "CLOSED"
    }
    val evidence get() = json.optJSONObject("evidence")
    val availability: String get() {
        val expiry = evidence?.optString("valid_until")
        if (!expiry.isNullOrEmpty() && runCatching { OffsetDateTime.parse(expiry).toInstant() <= Instant.now() }.getOrDefault(true)) return "UNKNOWN"
        return json.optString("availability", "UNKNOWN")
    }
    fun strings(key: String) = json.optJSONArray(key)?.let { array -> (0 until array.length()).map { array.getString(it) } } ?: emptyList()
}
fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

class Repository(private val context: Context) {
    private val client = OkHttpClient.Builder().callTimeout(4500, TimeUnit.MILLISECONDS).build()
    private val authClient = client.newBuilder().callTimeout(20, TimeUnit.SECONDS).build()
    val sessionStore = SessionStore(context)
    var onSessionExpired: ((Boolean) -> Unit)? = null
    @Volatile var accountId: String? = null
    var lastUpdate: String? = null
    private fun bookmarksKey(id: String) = stringSetPreferencesKey("bookmarks_$id")
    private fun dietsKey(id: String) = stringSetPreferencesKey("diets_$id")
    private val cacheKey = stringPreferencesKey("approved_cache")
    private val providerCacheKey = stringPreferencesKey("provider_cache")
    suspend fun providers(): Pair<List<JSONObject>, Boolean> {
        return try {
            val response = request("/api/providers")
            context.store.edit { it[providerCacheKey] = response }
            JSONObject(response).getJSONArray("providers").objects().filterNot { it.optBoolean("demo") } to false
        } catch (error: Exception) {
            if (error is SessionExpired || error is kotlinx.coroutines.CancellationException) throw error
            requireSession()
            val cached = context.store.data.first()[providerCacheKey] ?: throw error
            JSONObject(cached).getJSONArray("providers").objects().filterNot { it.optBoolean("demo") } to true
        }
    }
    suspend fun request(path: String, method: String = "GET", body: JSONObject? = null): String = withContext(Dispatchers.IO) {
        val protected = !path.startsWith("/api/auth/") || path in listOf("/api/auth/me", "/api/auth/change-password")
        if (protected) requireSession()
        val builder = Request.Builder().url(BuildConfig.API_URL.trimEnd('/') + path)
        val token = sessionStore.read().optString("token")
        if (protected && token.isNotBlank()) builder.header("Authorization", "Bearer $token")
        if (method != "GET") builder.method(method, (body ?: JSONObject()).toString().toRequestBody("application/json".toMediaType()))
        (if (path.startsWith("/api/auth/")) authClient else client).newCall(builder.build()).execute().use { response ->
            val content = response.body?.string() ?: "{}"
            if (!response.isSuccessful) {
                if (response.code == 401 && protected) {
                    val revoked = response.header("X-FoodBridge-Session") != "expired"
                    expireSession(token, revoked)
                    throw SessionExpired(revoked)
                }
                val detail = runCatching { JSONObject(content).opt("detail")?.toString() ?: "Request failed" }.getOrDefault("Request failed")
                throw IOException(if (response.code == 422) "Check your input: $detail" else detail)
            }
            content
        }
    }
    suspend fun all(): Pair<List<Service>, Boolean> {
        return try {
            val payload = JSONObject(request("/api/services"))
            lastUpdate = Instant.now().toString()
            payload.put("local_updated_at", lastUpdate)
            val services = payload.getJSONArray("services").objects().onEach { it.put("local_updated_at", lastUpdate) }
            context.store.edit { it[cacheKey] = payload.toString() }
            services.map(::Service).filterNot { it.demo } to false
        } catch (error: Exception) {
            if (error is SessionExpired || error is kotlinx.coroutines.CancellationException) throw error
            requireSession()
            val cached = context.store.data.first()[cacheKey] ?: throw error
            val payload = JSONObject(cached)
            lastUpdate = payload.optString("local_updated_at")
            payload.getJSONArray("services").objects().map { it.put("cached", true).put("local_updated_at", lastUpdate); Service(it) }.filterNot { it.demo } to true
        }
    }
    fun sessionValid(): Boolean {
        val data = sessionStore.read()
        val current = System.currentTimeMillis()
        return data.optString("token").isNotBlank() && data.optJSONObject("account") != null &&
            runCatching { OffsetDateTime.parse(data.getString("expires_at")).toInstant().toEpochMilli() > current }.getOrDefault(false) &&
            current >= data.optLong("last_activity") && current - data.optLong("last_activity") < 30 * 60_000
    }
    fun requireSession() { if (!sessionValid()) { expireSession(); throw SessionExpired() } }
    private fun expireSession(expectedToken: String? = null, revoked: Boolean = false) {
        var ended = false
        sessionStore.update { data ->
            if (expectedToken == null || data.optString("token") == expectedToken) {
                listOf("token", "account", "expires_at", "last_activity").forEach(data::remove)
                ended = true
            }
        }
        if (ended) {
            accountId = null
            if (revoked) ReminderStore(context).deactivate() else ReminderStore(context).sessionExpired()
            onSessionExpired?.invoke(revoked)
        }
    }
    suspend fun bookmarks(): Set<String> { requireSession(); val id = accountId ?: throw SessionExpired(); return context.store.data.first()[bookmarksKey(id)] ?: emptySet() }
    suspend fun diets(): Set<String> { requireSession(); val id = accountId ?: throw SessionExpired(); return context.store.data.first()[dietsKey(id)] ?: emptySet() }
    suspend fun saveBookmarks(ids: Set<String>, id: String) { requireSession(); context.store.edit { it[bookmarksKey(id)] = ids } }
    suspend fun saveDiets(diets: Set<String>, id: String) { requireSession(); context.store.edit { it[dietsKey(id)] = diets } }
    suspend fun clearLocal() { val id = accountId ?: return; context.store.edit { it.remove(bookmarksKey(id)); it.remove(dietsKey(id)) } }
    suspend fun retryRevocations() = withContext(Dispatchers.IO) {
        val data = sessionStore.read()
        val pending = data.optJSONArray("pending_revocations") ?: return@withContext
        val remaining = JSONArray()
        pending.objects().forEach { entry ->
            if (runCatching { OffsetDateTime.parse(entry.getString("expires_at")).toInstant() <= Instant.now() }.getOrDefault(false)) return@forEach
            try {
                val request = Request.Builder().url(BuildConfig.API_URL.trimEnd('/') + "/api/auth/logout")
                    .header("Authorization", "Bearer ${entry.getString("token")}")
                    .post("{}".toRequestBody("application/json".toMediaType())).build()
                client.newCall(request).execute().use { if (!it.isSuccessful) remaining.put(entry) }
            } catch (_: IOException) { remaining.put(entry) }
        }
        // Re-read so a concurrent login or interaction is never overwritten.
        val processed = pending.objects().map { it.optString("token") }.toSet()
        sessionStore.update { latest ->
            val added = latest.optJSONArray("pending_revocations")?.objects().orEmpty().filter { it.optString("token") !in processed }
            added.forEach(remaining::put)
            latest.put("pending_revocations", remaining)
        }
    }
}

class SessionExpired(val revoked: Boolean = false) : IOException("Your session has expired. Sign in again.")
