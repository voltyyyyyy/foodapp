package sg.foodbridge.navigator

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun SavedScreen(s: UiState, model: AppViewModel) { Page {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Saved", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text("Your saved support, on this device.", color = Muted)
    }
    val services = s.services.filter { it.id in s.saved }
    if (s.saved.isEmpty()) Empty("Nothing saved yet", "Save a service or provider to find it here.", Icons.Outlined.BookmarkBorder) {
        Primary("Find food support", Icons.Outlined.Search) { model.tab("Find") }
    }
    services.forEach { service ->
        // Small internal spacing keeps the reminder attached to the listing it affects.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ServiceCard(service, s, model)
            ReminderControls(service, s, model)
        }
    }
    s.providers.filter { providerBookmarkId(it) in s.saved }.forEach { SavedProviderCard(it, s, model) }
    val listedIds = s.services.map { it.id }.toSet() + s.providers.map(::providerBookmarkId)
    (s.saved - listedIds).forEach { id ->
        val provider = id.startsWith("provider:")
        Panel {
            Text(if (provider) "Provider details unavailable" else "Service details unavailable", fontWeight = FontWeight.Bold)
            Text(if (provider) "This saved provider is not in the latest directory available to this device."
                else "This saved service is not in the latest approved information available to this device. Try refreshing before removing it.", color = Muted)
            OutlinedButton(onClick = { model.removeOrSave(id) }) { Text("Remove bookmark") }
        }
    }
    if (services.any { it.id in s.reminders }) {
        ExpandableSection("Shared reminder settings", Icons.Outlined.NotificationsNone) {
            Text("One time applies to every enabled service reminder for this account. Changing it recalculates all of them.", color = Muted)
            DailyReminderTime(s, model)
            if (!s.notificationsAllowed) NotificationSettings(model)
            Text("Reminders use recorded sessions and do not reserve food. Android may delay them. Signing out stops reminders; an expired session hides listing details until you sign in.", color = Muted)
        }
    }
    s.lastUpdate?.let { Text("Downloaded ${formatTime(it)}", color = Muted, style = MaterialTheme.typography.bodySmall) }
    TextButton(onClick = { model.update { it.copy(page = "reportHistory") } }) {
        Icon(Icons.Outlined.ReceiptLong, null); Spacer(Modifier.width(8.dp)); Text("Your report receipts")
    }
} }

/** Also usable by the shell so removals outside Saved have the same recovery. */
@Composable fun BookmarkUndo(s: UiState, model: AppViewModel) {
    if (s.undoBookmarkId != null) Surface(color = Mint, modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (s.undoBookmarkReminder != null) "Removed from Saved. Its reminder was also turned off." else "Removed from Saved.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = model::undoBookmark) { Text("Undo") }
                TextButton(onClick = model::dismissBookmarkUndo) { Text("Dismiss") }
            }
        }
    }
}

@Composable fun NotificationSettings(model: AppViewModel) {
    val context = LocalContext.current
    val store = remember(context) { ReminderStore(context) }
    val notRequested = Build.VERSION.SDK_INT >= 33 && !store.permissionRequested() &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { model.notificationState() }
    Panel(Peach) {
        Text(if (notRequested) "Allow reminder notifications" else "Reminder notifications are off", fontWeight = FontWeight.Bold)
        Text(if (notRequested) "Android needs your permission before FoodBridge can show reminders."
            else "Allow notifications in Android settings. Your reminder choices are kept.")
        OutlinedButton(onClick = {
            if (notRequested) {
                store.markPermissionRequested()
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else runCatching {
                context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
            }.onFailure { model.error("Open Android Settings, then Apps, FoodBridge, Notifications to allow reminders.") }
        }) { Text(if (notRequested) "Allow notifications" else "Open notification settings") }
    }
}

@Composable fun DailyReminderTime(s: UiState, model: AppViewModel) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        val time = runCatching { LocalTime.parse(s.reminderTime) }.getOrDefault(LocalTime.of(8, 0))
        TimePickerDialog(context, { _, hour, minute ->
            model.reminderTime("%02d:%02d".format(Locale.ROOT, hour, minute))
        }, time.hour, time.minute, false).show()
    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Icon(Icons.Outlined.Schedule, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
        Text("Shared reminder time: ${s.reminderTime} · Singapore")
    }
}

@Composable fun ReminderControls(service: Service, s: UiState, model: AppViewModel) {
    val context = LocalContext.current
    val store = remember(context) { ReminderStore(context) }
    val enabled = service.id in s.reminders
    val preview = remember(service.json.toString(), s.reminderTime, enabled, s.clock) { store.nextReminder(service) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        model.notificationState()
        if (!granted) model.error("Notifications are off. Your reminder choice is kept; allow notifications in Android settings to receive it.")
    }
    val onChange: (Boolean) -> Unit = { value ->
        model.reminder(service, value)
        if (value && model.state.value.account != null && Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            store.markPermissionRequested()
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .toggleable(value = enabled, role = Role.Switch, onValueChange = onChange), verticalAlignment = Alignment.CenterVertically) {
                Text("Session-day reminder for ${service.name}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp)); Switch(checked = enabled, onCheckedChange = null)
            }
            if (enabled) {
                if (!s.notificationsAllowed) Text("Paused: notifications are off. Your reminder choice is kept.", color = MaterialTheme.colorScheme.error)
                preview.nextAt?.let { next ->
                    Text("Next qualifying time: ${next.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy, h:mm a", Locale.ENGLISH))} · Singapore", fontWeight = FontWeight.SemiBold)
                }
                Text(preview.reason, color = Muted, style = MaterialTheme.typography.bodySmall)
                if (preview.todayCutoffPassed) Text("Today's shared time has passed. Enabling now skips today; choose a later time if a recorded session will still be open.", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text("Changing the shared time affects all enabled service reminders.", color = Muted, style = MaterialTheme.typography.bodySmall)
                DailyReminderTime(s, model)
                if (!s.notificationsAllowed) NotificationSettings(model)
            }
        }
    }
}
