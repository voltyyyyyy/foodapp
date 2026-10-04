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
    var token: String? = null // Staff sessions are deliberately memory-only.
    var lastUpdate: String? = null
    private val bookmarksKey = stringSetPreferencesKey("bookmarks")
    private val dietsKey = stringSetPreferencesKey("diets")
    private val cacheKey = stringPreferencesKey("approved_cache")
    private val providerCacheKey = stringPreferencesKey("provider_cache")
    suspend fun mapboxRoute(origin: Pair<Double, Double>, service: Service, mode: String): JSONObject = withContext(Dispatchers.IO) {
        val profile = if (mode == "drive") "driving" else "walking"
        val url = okhttp3.HttpUrl.Builder().scheme("https").host("api.mapbox.com")
            .addPathSegments("directions/v5/mapbox/$profile")
            .addPathSegment("${origin.second},${origin.first};${service.longitude},${service.latitude}")
            .addQueryParameter("access_token", BuildConfig.MAPBOX_ACCESS_TOKEN)
            .addQueryParameter("geometries", "geojson").addQueryParameter("overview", "full")
            .addQueryParameter("steps", "true").build()
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Route unavailable. Check your connection and try again.")
            val data = JSONObject(response.body?.string() ?: "{}")
            if (data.optString("code") != "Ok") throw IOException("No route found for this travel option.")
            val route = data.getJSONArray("routes").getJSONObject(0)
            val geometry = JSONArray()
            route.getJSONObject("geometry").getJSONArray("coordinates").let { points ->
                for (i in 0 until points.length()) points.getJSONArray(i).let { point -> geometry.put(JSONArray().put(point.getDouble(1)).put(point.getDouble(0))) }
            }
            if (geometry.length() < 2) throw IOException("Route geometry unavailable.")
            val instructions = JSONArray()
            route.optJSONArray("legs")?.objects()?.forEach { leg -> leg.optJSONArray("steps")?.objects()?.forEach { step ->
                step.optJSONObject("maneuver")?.optString("instruction")?.takeIf { it.isNotBlank() }?.let(instructions::put)
            } }
            JSONObject().put("available", true).put("mode", mode).put("distance_km", route.getDouble("distance") / 1000)
                .put("duration_minutes", route.getDouble("duration") / 60).put("geometry", geometry)
                .put("instructions", instructions).put("attribution", "Mapbox Directions")
        }
    }
    suspend fun providers(): Pair<List<JSONObject>, Boolean> {
        return try {
            val response = request("/api/providers")
            context.store.edit { it[providerCacheKey] = response }
            JSONObject(response).getJSONArray("providers").objects().filterNot { it.optBoolean("demo") } to false
        } catch (error: Exception) {
            val cached = context.store.data.first()[providerCacheKey] ?: throw error
            JSONObject(cached).getJSONArray("providers").objects().filterNot { it.optBoolean("demo") } to true
        }
    }
    suspend fun request(path: String, method: String = "GET", body: JSONObject? = null): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(BuildConfig.API_URL.trimEnd('/') + path)
        token?.let { builder.header("Authorization", "Bearer $it") }
        if (method != "GET") builder.method(method, (body ?: JSONObject()).toString().toRequestBody("application/json".toMediaType()))
        client.newCall(builder.build()).execute().use { response ->
            val content = response.body?.string() ?: "{}"
            if (!response.isSuccessful) {
                val detail = runCatching { JSONObject(content).opt("detail")?.toString() ?: "Request failed" }.getOrDefault("Request failed")
                throw IOException(if (response.code == 422) "Check your input: $detail" else detail)
            }
            content
        }
    }
    suspend fun all(): Pair<List<Service>, Boolean> {
        return try {
            val response = request("/api/services")
            context.store.edit { it[cacheKey] = response }
            lastUpdate = JSONObject(response).optString("last_update")
            JSONObject(response).getJSONArray("services").objects().map(::Service).filterNot { it.demo } to false
        } catch (error: Exception) {
            val cached = context.store.data.first()[cacheKey] ?: throw error
            lastUpdate = JSONObject(cached).optString("last_update")
            JSONObject(cached).getJSONArray("services").objects().map { it.put("cached", true); Service(it) }.filterNot { it.demo } to true
        }
    }
    suspend fun bookmarks() = context.store.data.first()[bookmarksKey] ?: emptySet()
    suspend fun diets() = context.store.data.first()[dietsKey] ?: emptySet()
    suspend fun saveBookmarks(ids: Set<String>) { context.store.edit { it[bookmarksKey] = ids } }
    suspend fun saveDiets(diets: Set<String>) { context.store.edit { it[dietsKey] = diets } }
    suspend fun clearLocal() { context.store.edit { it.clear() } }
}
