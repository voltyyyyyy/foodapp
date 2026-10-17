package sg.foodbridge.navigator

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Account-scoped cached reminders; only an authenticated owner can change settings. */
class ReminderStore(private val context: Context) {
    companion object { private val requestLock = Any() }
    private val prefs = context.getSharedPreferences("foodbridge_reminders", Context.MODE_PRIVATE)
    private val sessions = SessionStore(context)
    private val repository by lazy { Repository(context) }
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val zone = ZoneId.of("Asia/Singapore")
    private fun signedInAccount() = sessions.read().optJSONObject("account")?.optString("id")
    private fun owner() = sessions.read().optString("reminder_owner").takeIf { it.isNotBlank() }
    private fun key(account: String, service: String) = "$account/$service"
    private fun rows() = prefs.all.filterKeys { it.startsWith("enabled:") }.mapNotNull { (key, value) ->
        runCatching { key.removePrefix("enabled:") to JSONObject(value as String) }.getOrNull()
    }
    fun hasOwner() = owner() != null
    fun hasEnabled() = owner()?.let { id -> rows().any { it.second.optString("account") == id } } == true
    fun dailyTime(): String = signedInAccount()?.let { prefs.getString("daily_time:$it", "08:00") } ?: "08:00"
    private fun dailyTime(account: String): LocalTime = runCatching { LocalTime.parse(prefs.getString("daily_time:$account", "08:00")) }.getOrDefault(LocalTime.of(8, 0))
    fun setDailyTime(time: String) {
        if (!repository.sessionValid()) return
        val account = signedInAccount() ?: return
        LocalTime.parse(time)
        prefs.edit().putString("daily_time:$account", time).apply()
        resume()
    }
    fun allowed(): Boolean {
        channel()
        return (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            notifications.areNotificationsEnabled() && notifications.getNotificationChannel("saved_support").importance != NotificationManager.IMPORTANCE_NONE
    }
    private fun channel() { notifications.createNotificationChannel(NotificationChannel("saved_support", "Saved support reminders", NotificationManager.IMPORTANCE_DEFAULT)) }
    private fun alarm(key: String, date: LocalDate? = null) = PendingIntent.getBroadcast(context, 0,
        Intent(context, ReminderReceiver::class.java).setAction("sg.foodbridge.REMINDER").setData(Uri.parse("foodbridge://reminder/$key"))
            .putExtra("reminder_key", key).putExtra("reminder_date", date?.toString()), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun expiryAlarm() = PendingIntent.getBroadcast(context, 1, Intent(context, ReminderReceiver::class.java).setAction("sg.foodbridge.REMINDER_EXPIRY"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun expiry() {
        alarms.cancel(expiryAlarm())
        if (!hasEnabled()) return
        val session = sessions.read()
        val until = runCatching { OffsetDateTime.parse(session.getString("expires_at")).toInstant().toEpochMilli() }.getOrNull() ?: return
        val deadline = minOf(until, session.optLong("last_activity") + 30 * 60_000L)
        if (deadline > System.currentTimeMillis()) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, expiryAlarm())
    }
    fun times(): Map<String, String> {
        val account = signedInAccount() ?: return emptyMap()
        val time = dailyTime(account).toString()
        return rows().filter { it.second.optString("account") == account }.associate { it.second.getJSONObject("service").getString("id") to time }
    }
    fun set(service: Service, enabled: Boolean) {
        if (!repository.sessionValid()) return
        val account = signedInAccount() ?: return
        if (!enabled) { remove(service.id); return }
        val snapshot = JSONObject(service.json.toString()).apply { listOf("distance_km", "travel_time", "explanations", "status_at").forEach(::remove) }
        prefs.edit().putString("enabled:${key(account, service.id)}", JSONObject().put("account", account).put("service", snapshot).toString()).apply()
        // Keep enabled preferences even without permission or a confirmed schedule.
        activate(account)
    }
    fun remove(service: String) {
        val key = key(signedInAccount() ?: return, service)
        alarms.cancel(alarm(key)); notifications.cancel(key, 0)
        prefs.edit().remove("enabled:$key").apply()
    }
    fun clear() {
        val account = signedInAccount() ?: return
        times().keys.forEach(::remove)
        prefs.edit().remove("daily_time:$account").apply()
        // Keep today's request records so clearing/re-enabling cannot duplicate a notification.
        resume()
    }
    private fun cancelScheduled() { rows().forEach { alarms.cancel(alarm(it.first)) }; alarms.cancel(expiryAlarm()); notifications.cancelAll() }
    fun activate(account: String) {
        if (signedInAccount() != account || !repository.sessionValid()) return
        if (owner() != account) cancelScheduled()
        sessions.update { it.put("reminder_owner", account) }
        resume()
    }
    /** Explicit sign-out and known revocation clear the active owner; account switches cancel old alarms. */
    fun deactivate() = synchronized(requestLock) { cancelScheduled(); sessions.update { it.remove("reminder_owner") } }
    /** Ordinary expiry removes identifying notifications but continues future generic cached reminders. */
    fun sessionExpired() { resume() }
    fun resume() {
        cancelScheduled()
        val current = owner() ?: return
        if (signedInAccount()?.let { it != current } == true) { deactivate(); return }
        if (!allowed()) return
        // Retain request dates even when the clock moves forward and back; the same
        // account/service/Singapore date must never become eligible for a second request.
        rows().filter { it.second.optString("account") == current }.forEach { schedule(it.first, it.second) }
        expiry()
    }
    fun updateServices(services: List<Service>) {
        val current = signedInAccount() ?: return
        rows().filter { it.second.optString("account") == current }.forEach { (key, row) ->
            val fresh = services.firstOrNull { it.id == row.getJSONObject("service").getString("id") }
            if (fresh == null || !fresh.json.optBoolean("active", true)) remove(row.getJSONObject("service").getString("id"))
            else prefs.edit().putString("enabled:$key", row.put("service", fresh.json).toString()).apply()
        }
        resume()
    }
    private data class SessionWindow(val start: ZonedDateTime, val end: ZonedDateTime, val label: String)
    private fun onDate(service: JSONObject, date: LocalDate): List<SessionWindow> {
        val overrides = service.optJSONObject("distribution_exceptions")
        val values = if (overrides?.has(date.toString()) == true) overrides.optJSONArray(date.toString()) else service.optJSONArray("distribution_schedules")
        return values?.objects().orEmpty().mapNotNull { row -> runCatching {
            if (row.optJSONArray("days")?.let { days -> (0 until days.length()).any { days.optInt(it) == date.dayOfWeek.value - 1 } } != true) return@runCatching null
            val start = LocalTime.parse(row.getString("start")); val end = LocalTime.parse(row.getString("end"))
            if (start == end) return@runCatching null
            SessionWindow(date.atTime(start).atZone(zone), date.plusDays(if (end < start) 1L else 0L).atTime(end).atZone(zone), row.optString("label", "Collection / meal session"))
        }.getOrNull() }
    }
    private fun qualifying(service: JSONObject, instant: ZonedDateTime): List<SessionWindow> {
        val date = instant.toLocalDate()
        val overrides = service.optJSONObject("distribution_exceptions")
        // Explicit closure also closes any session that would otherwise continue from yesterday.
        if (overrides?.has(date.toString()) == true && overrides.optJSONArray(date.toString())?.length() == 0) return emptyList()
        return (onDate(service, date.minusDays(1)) + onDate(service, date)).filter { it.end.isAfter(instant) && it.start.toLocalDate() <= date }.sortedBy { it.start }
    }
    private fun schedule(key: String, row: JSONObject) {
        alarms.cancel(alarm(key))
        val account = row.optString("account")
        if (account != owner() || !allowed()) return
        val service = row.getJSONObject("service")
        if (!service.optBoolean("active", true)) return
        val now = ZonedDateTime.now(zone)
        val time = dailyTime(account)
        val next = (0L..366L).asSequence().map { now.toLocalDate().plusDays(it).atTime(time).atZone(zone) }.firstOrNull { candidate ->
            candidate.isAfter(now) && !prefs.contains("request:$key/${candidate.toLocalDate()}") && qualifying(service, candidate).isNotEmpty()
        } ?: return
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), alarm(key, next.toLocalDate()))
    }
    private fun claim(key: String, date: LocalDate, account: String, serviceId: String): Boolean = synchronized(requestLock) {
        val requestKey = "request:$key/$date"
        if (prefs.contains(requestKey)) false else prefs.edit().putString(requestKey,
            JSONObject().put("account", account).put("service", serviceId).put("singapore_date", date.toString())
                .put("requested_at", Instant.now().toString()).put("state", "REQUESTED").toString()).commit()
    }
    fun deliver(key: String, expectedDate: String?) {
        val row = prefs.getString("enabled:$key", null)?.let(::JSONObject) ?: return
        val account = row.getString("account")
        if (account != owner()) return
        if (signedInAccount()?.let { it != account } == true) { deactivate(); return }
        if (!allowed()) { resume(); return }
        val service = row.getJSONObject("service")
        val now = ZonedDateTime.now(zone)
        val windows = qualifying(service, now)
        if (expectedDate != now.toLocalDate().toString() || windows.isEmpty() || !service.optBoolean("active", true)) { schedule(key, row); return }
        if (claim(key, now.toLocalDate(), account, service.getString("id"))) {
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW).setData(Uri.parse("foodbridge://reminder/$key"))
                .putExtra("service_id", service.getString("id")).putExtra("reminder_account_id", account), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val genericTitle = "FoodBridge reminder"
            val lastUpdate = runCatching { OffsetDateTime.parse(service.optString("local_updated_at")).atZoneSameInstant(zone)
                .format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)) }.getOrNull()
            val freshness = lastUpdate?.let { "Last local update: $it Singapore time." } ?: "Last local update time not recorded."
            val genericText = "A saved service has a session scheduled today. Sign in to view details"
            val genericDetails = "$genericText\nContact the provider before travelling.\n$freshness"
            val generic = Notification.Builder(context, "saved_support").setSmallIcon(R.drawable.ic_foodbridge)
                .setContentTitle(genericTitle).setContentText(genericText).setStyle(Notification.BigTextStyle().bigText(genericDetails))
                .setContentIntent(open).setAutoCancel(true).build()
            val notification = if (repository.sessionValid() && signedInAccount() == account) {
                val session = sessions.read()
                val deadline = runCatching { minOf(OffsetDateTime.parse(session.getString("expires_at")).toInstant().toEpochMilli(), session.optLong("last_activity") + 30 * 60_000L) }.getOrDefault(System.currentTimeMillis())
                val format = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)
                val timing = windows.joinToString("\n") { "${it.label}: ${it.start.format(format)} to ${it.end.format(format)} (Singapore)" }
                val provider = service.optString("organisation_name", service.optString("name"))
                val location = "${collectionLocationLabel(service.optString("location_role"))}: ${service.optString("address")}, Singapore ${service.optString("postal")}"
                val freshness = lastUpdate?.let { "Last local update: $it Singapore time." } ?: "Last local update time not recorded."
                val details = "${service.optString("name")} · ${service.optString("support_type")}\nProvider: $provider\n$timing\n$location\n$freshness Contact the provider before travelling. Food availability is not guaranteed."
                Notification.Builder(context, "saved_support").setSmallIcon(R.drawable.ic_foodbridge)
                    .setContentTitle(service.optString("name")).setContentText("$timing - ${service.optString("address")}")
                    .setStyle(Notification.BigTextStyle().bigText(details)).setVisibility(Notification.VISIBILITY_PRIVATE)
                    .setPublicVersion(generic).setTimeoutAfter((deadline - System.currentTimeMillis()).coerceAtLeast(1L))
                    .setContentIntent(open).setAutoCancel(true).build()
            } else generic
            synchronized(requestLock) {
                if (owner() == account && signedInAccount()?.let { it != account } != true) {
                    val safe = if (repository.sessionValid() && signedInAccount() == account) notification else generic
                    try { notifications.notify(key, 0, safe) } catch (_: SecurityException) { /* Keep enabled settings when permission changes during delivery. */ }
                }
            }
        }
        schedule(key, row)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = ReminderStore(context)
        if (intent.action == "sg.foodbridge.REMINDER") intent.getStringExtra("reminder_key")?.let { store.deliver(it, intent.getStringExtra("reminder_date")) }
        else store.resume()
    }
}
