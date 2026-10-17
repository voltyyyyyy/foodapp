package sg.foodbridge.navigator

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Local, account-scoped reminders. Inexact alarms need no exact-alarm permission. */
class ReminderStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("foodbridge_reminders", Context.MODE_PRIVATE)
    private val repository by lazy { Repository(context) }
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val zone = ZoneId.of("Asia/Singapore")
    private fun account() = SessionStore(context).read().optJSONObject("account")?.optString("id")
    private fun key(account: String, service: String) = "$account/$service"
    private fun rows() = prefs.all.mapNotNull { (key, value) -> runCatching { key to JSONObject(value as String) }.getOrNull() }
    fun allowed(): Boolean {
        channel()
        return (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            notifications.areNotificationsEnabled() && notifications.getNotificationChannel("saved_support").importance != NotificationManager.IMPORTANCE_NONE
    }
    private fun channel() { notifications.createNotificationChannel(NotificationChannel("saved_support", "Saved support reminders", NotificationManager.IMPORTANCE_DEFAULT)) }
    private fun alarm(key: String) = PendingIntent.getBroadcast(context, 0,
        Intent(context, ReminderReceiver::class.java).setAction("sg.foodbridge.REMINDER").setData(Uri.parse("foodbridge://reminder/$key")).putExtra("reminder_key", key),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun expiryAlarm() = PendingIntent.getBroadcast(context, 1,
        Intent(context, ReminderReceiver::class.java).setAction("sg.foodbridge.REMINDER_EXPIRY"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun expiry() {
        alarms.cancel(expiryAlarm())
        val session = SessionStore(context).read()
        val until = runCatching { java.time.OffsetDateTime.parse(session.getString("expires_at")).toInstant().toEpochMilli() }.getOrNull() ?: return
        val deadline = minOf(until, session.optLong("last_activity") + 30 * 60_000L)
        if (deadline > System.currentTimeMillis() && times().isNotEmpty())
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, expiryAlarm())
    }
    fun times(): Map<String, String> {
        val account = account() ?: return emptyMap()
        return rows().filter { it.first.startsWith("$account/") }.associate { it.second.getJSONObject("service").getString("id") to it.second.getString("time") }
    }
    fun set(service: Service, enabled: Boolean, time: String) {
        val account = account() ?: return
        val key = key(account, service.id)
        if (!enabled) { remove(service.id); return }
        LocalTime.parse(time)
        val row = JSONObject().put("account", account).put("service", service.json).put("time", time)
        prefs.edit().putString(key, row.toString()).apply()
        resume()
    }
    fun remove(service: String) {
        val key = key(account() ?: return, service)
        alarms.cancel(alarm(key)); notifications.cancel(key, 0)
        prefs.edit().remove(key).apply()
    }
    fun clear() { times().keys.forEach(::remove) }
    fun pause() { rows().forEach { alarms.cancel(alarm(it.first)) }; alarms.cancel(expiryAlarm()); notifications.cancelAll() }
    fun resume() {
        pause()
        if (!repository.sessionValid() || !allowed()) return
        val current = account() ?: return
        rows().filter { it.second.optString("account") == current }.forEach { schedule(it.first, it.second) }
        expiry()
    }
    fun updateServices(services: List<Service>) {
        val current = account() ?: return
        rows().filter { it.second.optString("account") == current }.forEach { (key, row) ->
            val fresh = services.firstOrNull { it.id == row.getJSONObject("service").getString("id") }
            // Withdrawn records may keep their bookmark, but never retain a reminder.
            if (fresh == null || !fresh.json.optBoolean("active", true)) remove(row.getJSONObject("service").getString("id"))
            else prefs.edit().putString(key, row.put("service", fresh.json).toString()).apply()
        }
        resume()
    }
    private fun sessions(service: JSONObject, date: LocalDate): List<JSONObject> {
        val overrides = service.optJSONObject("distribution_exceptions")
        val rows = if (overrides?.has(date.toString()) == true) overrides.optJSONArray(date.toString()) else service.optJSONArray("distribution_schedules")
        return rows?.objects().orEmpty().filter { row ->
            row.optJSONArray("days")?.let { days -> (0 until days.length()).any { days.optInt(it) == date.dayOfWeek.value - 1 } } == true
        }
    }
    private fun schedule(key: String, row: JSONObject) {
        alarms.cancel(alarm(key))
        if (!repository.sessionValid() || row.optString("account") != account() || !allowed()) return
        val service = row.getJSONObject("service")
        if (!service.optBoolean("active", true)) return
        val now = ZonedDateTime.now(zone)
        val time = LocalTime.parse(row.getString("time"))
        // Check a full year so dated sessions can be reminded without treating office hours as sessions.
        val next = (0L..366L).map { now.toLocalDate().plusDays(it).atTime(time).atZone(zone) }.firstOrNull { candidate ->
            candidate.isAfter(now) && sessions(service, candidate.toLocalDate()).any { session ->
                val end = LocalTime.parse(session.getString("end"))
                val start = LocalTime.parse(session.getString("start"))
                candidate.isBefore(candidate.toLocalDate().plusDays(if (end < start) 1 else 0).atTime(end).atZone(zone))
            }
        } ?: return
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), alarm(key))
    }
    fun deliver(key: String) {
        val row = prefs.getString(key, null)?.let(::JSONObject) ?: return
        if (!repository.sessionValid() || row.optString("account") != account() || !allowed()) { pause(); return }
        val service = row.getJSONObject("service")
        val now = ZonedDateTime.now(zone)
        // A delayed alarm must still refer to a session on today's date, before its recorded end.
        val upcoming = sessions(service, now.toLocalDate()).any { session ->
            val end = LocalTime.parse(session.getString("end")); val start = LocalTime.parse(session.getString("start"))
            now.isBefore(now.toLocalDate().plusDays(if (end < start) 1 else 0).atTime(end).atZone(zone))
        }
        if (upcoming && service.optBoolean("active", true)) {
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW).setData(Uri.parse("foodbridge://service/${service.getString("id")}"))
                .putExtra("service_id", service.getString("id")), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            notifications.notify(key, 0, Notification.Builder(context, "saved_support").setSmallIcon(R.drawable.ic_foodbridge)
                .setContentTitle("Check your saved support")
                .setContentText("A recorded session is scheduled today. Open FoodBridge and contact the provider before travelling.")
                .setStyle(Notification.BigTextStyle().bigText("A recorded session is scheduled today, based on your last local update. Food availability is not guaranteed. Open FoodBridge to check ${service.optString("name")} and contact the provider."))
                .setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(open).setAutoCancel(true).build())
        }
        schedule(key, row)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = ReminderStore(context)
        if (intent.action == "sg.foodbridge.REMINDER") intent.getStringExtra("reminder_key")?.let(store::deliver)
        else store.resume()
    }
}
