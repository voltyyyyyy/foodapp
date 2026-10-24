package sg.foodbridge.navigator

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val singaporeZone = ZoneId.of("Asia/Singapore")
private fun JSONObject.detailText(key: String) = optString(key).takeUnless { it.isBlank() || it == "null" }
private fun clock(value: String): String = runCatching {
    java.time.LocalTime.parse(value).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
}.getOrDefault(value)

fun reportCategoryLabel(category: String) = when (category) {
    "Address" -> "Address or location"
    "Contact" -> "Phone number, email or website"
    "Hours" -> "Opening hours or distribution schedule"
    "Service details" -> "Support details or requirements"
    "Permanently closed" -> "Provider or service permanently closed"
    "Food unavailable" -> "Food currently unavailable"
    else -> category
}

@Composable fun ReportInformationButton(model: AppViewModel) {
    OutlinedButton(onClick = model::openReport, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(12.dp)) {
        Icon(Icons.Outlined.Flag, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp))
        Text("Report an issue")
    }
}

@Composable private fun DetailHeading(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(22.dp), tint = Teal)
        Text(title, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable fun ListingBookmarkButton(saved: Boolean, onClick: () -> Unit) {
    IconToggleButton(checked = saved, onCheckedChange = { onClick() }, modifier = Modifier.size(48.dp)) {
        Icon(if (saved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
            if (saved) "Remove saved listing" else "Save listing", tint = Teal, modifier = Modifier.size(26.dp))
    }
}

@Composable fun ListingNavigateButton(enabled: Boolean = true, label: String = "Navigate", onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(18.dp)) {
        Icon(Icons.Outlined.NearMe, null, Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable fun CollectionPointWarning() {
    Surface(color = Peach, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.WarningAmber, "Warning", Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurface)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Not a confirmed collection point", fontWeight = FontWeight.Bold)
                Text("Ask the provider where to go before travelling.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable private fun DetailFact(label: String, value: String, icon: ImageVector) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.padding(top = 3.dp).size(20.dp), tint = Muted)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = Muted, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// Date-specific overrides replace the recurring schedule, including an empty (closed) override.
fun scheduleForDate(service: Service, date: LocalDate): List<JSONObject> {
    val exceptions = service.json.optJSONObject("distribution_exceptions")
    fun rows(day: LocalDate): List<JSONObject> {
        val values = if (exceptions?.has(day.toString()) == true) exceptions.optJSONArray(day.toString())
            else service.json.optJSONArray("distribution_schedules")
        return values?.objects().orEmpty().filter { row ->
            val days = row.optJSONArray("days")
            days?.let { a -> (0 until a.length()).any { a.optInt(it) == day.dayOfWeek.value - 1 } } == true
        }
    }
    val current = rows(date)
    if (exceptions?.has(date.toString()) == true && current.isEmpty()) return emptyList()
    val continuing = rows(date.minusDays(1)).filter { it.optString("end") < it.optString("start") }
        .map { JSONObject(it.toString()).put("calendar_continuation", true) }
    return (current + continuing).sortedBy { if (it.optBoolean("calendar_continuation")) "00:00" else it.optString("start") }
}

fun readableSearchTime(value: String): String = runCatching {
    val time = runCatching { java.time.OffsetDateTime.parse(value).atZoneSameInstant(singaporeZone).toLocalDateTime() }
        .getOrElse { java.time.LocalDateTime.parse(value) }
    time.format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)) + " SGT"
}.getOrDefault("Time not confirmed")

fun nextSessionLabel(service: Service): String {
    val now = java.time.ZonedDateTime.now(singaporeZone)
    val hasSchedule = (service.json.optJSONArray("distribution_schedules")?.length() ?: 0) > 0 ||
        (service.json.optJSONObject("distribution_exceptions")?.length() ?: 0) > 0
    if (!hasSchedule) return "Collection / meal times not confirmed"
    for (offset in 0L..366L) {
        val date = now.toLocalDate().plusDays(offset)
        scheduleForDate(service, date).forEach { row ->
            val start = runCatching { java.time.LocalTime.parse(row.optString("start")) }.getOrNull() ?: return@forEach
            val end = runCatching { java.time.LocalTime.parse(row.optString("end")) }.getOrNull() ?: return@forEach
            val continuing = row.optBoolean("calendar_continuation")
            if (start == end) return@forEach
            val starts = date.atTime(if (continuing) java.time.LocalTime.MIDNIGHT else start).atZone(singaporeZone)
            val ends = date.plusDays(if (end < start && !continuing) 1L else 0L).atTime(end).atZone(singaporeZone)
            if (ends.isAfter(now)) return "${if (starts.isBefore(now)) "Recorded session now" else "Next recorded session"}: ${date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))}, ${clock(if (continuing) "00:00" else row.optString("start"))} – ${clock(row.optString("end"))}${if (end < start && !continuing) " (next day)" else ""} SGT · food not guaranteed"
        }
    }
    return "No upcoming session recorded in the next year. Ask the provider."
}

@Composable fun ServiceDecisionSummary(service: Service) {
    IconFact("Can I use this? ${service.json.detailText("eligibility") ?: "Eligibility unconfirmed. Ask the provider."}", Icons.Outlined.Groups)
    IconFact(accessMethodDetail(service.json.optString("access_method", "contact_to_confirm")), Icons.Outlined.Assignment)
    IconFact(nextSessionLabel(service), Icons.Outlined.CalendarMonth)
    IconFact("Where do I go? ${collectionLocationLabel(service.json.optString("location_role"))}", Icons.Outlined.LocationOn)
}

@Composable private fun DistributionCalendar(service: Service) {
    val today = LocalDate.now(singaporeZone)
    val initial = runCatching { service.json.optString("status_at").take(10).let(LocalDate::parse) }.getOrDefault(today)
    var selected by remember(service.id, initial) { mutableStateOf(initial) }
    var weekStart by remember(service.id, initial) { mutableStateOf(initial.minusDays((initial.dayOfWeek.value - 1).toLong())) }
    var weeklyExpanded by remember(service.id) { mutableStateOf(false) }
    var dateList by remember(service.id) { mutableStateOf(false) }
    val recurring = service.json.optJSONArray("distribution_schedules")?.objects() ?: emptyList()
    val exceptions = service.json.optJSONObject("distribution_exceptions")
    val sessions = scheduleForDate(service, selected)
    val override = exceptions?.has(selected.toString()) == true
    val unknown = recurring.isEmpty() && !override && sessions.isEmpty()
    val days = (0L..6L).map { weekStart.plusDays(it) }
    val monthFormat = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailHeading("Distribution schedule", Icons.Outlined.CalendarMonth)
        Text("Singapore time · food is not guaranteed.", color = Muted, style = MaterialTheme.typography.bodySmall)
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (days.first().month == days.last().month) days.first().format(monthFormat)
                    else "${days.first().format(monthFormat)} – ${days.last().format(monthFormat)}", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { weekStart = weekStart.minusWeeks(1); selected = weekStart }, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.ChevronLeft, "Previous week") }
                    TextButton(onClick = { selected = today; weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong()) }, modifier = Modifier.weight(1f)) { Text("Today") }
                    IconButton(onClick = { weekStart = weekStart.plusWeeks(1); selected = weekStart }, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.ChevronRight, "Next week") }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    days.forEach { date ->
                        val hasSession = scheduleForDate(service, date).isNotEmpty()
                        val isSelected = date == selected
                        Surface(shape = RoundedCornerShape(12.dp), color = if (isSelected) Teal else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.widthIn(min = 56.dp).heightIn(min = 64.dp).semantics { contentDescription = "${date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH))}, ${if (hasSession) "recorded session" else "no recorded session"}" }.selectable(selected = isSelected, role = Role.Tab, onClick = { selected = date })) {
                            Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(date.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)), color = if (isSelected) androidx.compose.ui.graphics.Color.White else Muted, style = MaterialTheme.typography.labelMedium)
                                Text(date.dayOfMonth.toString(), color = if (isSelected) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                Text(if (hasSession) "•" else " ", color = if (isSelected) androidx.compose.ui.graphics.Color.White else Teal, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Text("Swipe dates to see the full week. Dots indicate recorded sessions.", color = Muted, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { dateList = !dateList }) { Text(if (dateList) "Hide date list" else "Show dates as a list") }
                if (dateList) days.forEach { date ->
                    TextButton(onClick = { selected = date }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { stateDescription = if (date == selected) "Selected" else "Not selected" }) {
                        Text("${date.format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", Locale.ENGLISH))} · ${if (scheduleForDate(service, date).isEmpty()) "no session recorded" else "recorded session"}")
                    }
                }
            }
        }
        Text(selected.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)), fontWeight = FontWeight.Bold)
        if (override) Text("Date-specific schedule", color = Teal, style = MaterialTheme.typography.labelMedium)
        when {
            unknown -> Text("Schedule unconfirmed. Ask the provider for dates and times.", color = Muted)
            sessions.isEmpty() -> Text("No session listed. Ask the provider about other arrangements.", color = Muted)
            else -> sessions.forEach { session ->
                val overnight = session.optString("end") < session.optString("start")
                val continuation = session.optBoolean("calendar_continuation")
                Surface(color = Mint, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${if (continuation) "12:00 AM" else clock(session.optString("start"))} – ${clock(session.optString("end"))}${if (overnight && !continuation) " (next day)" else ""}", color = Teal, fontWeight = FontWeight.Bold)
                        Text(session.detailText("label") ?: "Distribution")
                        if (continuation) Text("Continues from the previous day", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (recurring.isNotEmpty()) {
            TextButton(onClick = { weeklyExpanded = !weeklyExpanded }, contentPadding = PaddingValues(0.dp)) {
                Text("Weekly sessions"); Icon(if (weeklyExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
            }
            if (weeklyExpanded) recurring.forEach { row ->
                val dayLabels = row.optJSONArray("days")?.let { a -> (0 until a.length()).map { listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[a.getInt(it)] }.joinToString(", ") }.orEmpty()
                DetailFact(dayLabels, "${clock(row.optString("start"))} – ${clock(row.optString("end"))}${if (row.optString("end") < row.optString("start")) " (next day)" else ""}\n${row.optString("label")}", Icons.Outlined.Schedule)
            }
        }
    }
}

@Composable fun DetailScreen(service: Service, s: UiState, model: AppViewModel) {
    val context = LocalContext.current
    var contactError by remember(service.id) { mutableStateOf<String?>(null) }
    fun open(uri: Uri, action: String = Intent.ACTION_VIEW) {
        runCatching { context.startActivity(Intent(action, uri)) }.onFailure { contactError = "No app can open this contact. Use the details below." }
    }
    val directoryProvider = s.providers.firstOrNull { it.optString("id") == service.json.optString("provider_id") }
    val ownPhone = service.json.detailText("phone")
    val ownSite = service.json.detailText("website")
    val phone = ownPhone ?: directoryProvider?.detailText("phone")
    val site = ownSite ?: directoryProvider?.detailText("website")
    val email = directoryProvider?.detailText("email")
    val hours = service.json.optJSONArray("schedules")?.objects().orEmpty()
    val hasSessions = (service.json.optJSONArray("distribution_schedules")?.length() ?: 0) > 0 ||
        (service.json.optJSONObject("distribution_exceptions")?.length() ?: 0) > 0
    Page {
        if (s.detailLoading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Checking current listing…", color = Muted) }
        s.detailError?.let { message -> Banner(message, Icons.Outlined.WarningAmber, Peach); TextButton(onClick = { model.detail(service) }) { Text("Retry checking listing") } }
        if (s.detailWithdrawn) Banner("This listing is no longer published. Saved information is shown for reference; do not travel based on it.", Icons.Outlined.WarningAmber, Peach)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IconFact(service.type, foodSupportIcon(service.type))
            Text(service.name, style = MaterialTheme.typography.headlineMedium)
            if (service.organisationName != service.name) Text(service.organisationName, color = Muted)
            if (service.demo) Text("Demo · do not travel here", color = MaterialTheme.colorScheme.error)
            if (!service.json.optBoolean("active", true)) Banner("Inactive service · do not travel here for support.", Icons.Outlined.WarningAmber, Peach)
            if (service.json.optString("location_role") !in listOf("collection_site", "confirmed_collection_site")) CollectionPointWarning()
            Availability(service.availability)
            IconFact(accessMethodLabel(service.json.optString("access_method", "contact_to_confirm")), Icons.Outlined.Groups)
            IconFact(service.address, Icons.Outlined.LocationOn)
            IconFact("Contact the provider before going. Food is not guaranteed.", Icons.Outlined.Info)
            service.json.detailText("status_at")?.let { IconFact("Support checked for ${readableSearchTime(it)}. Route estimates depart now.", Icons.Outlined.Event) }
            ServiceDecisionSummary(service)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DetailHeading("Contact", Icons.Outlined.ContactPhone)
            if (((ownPhone == null && phone != null) || (ownSite == null && site != null) || email != null) && directoryProvider != null) Text("Contact from ${directoryProvider.optString("name")} (linked provider)", color = Muted, style = MaterialTheme.typography.bodySmall)
            if (phone != null) Primary("Call $phone", Icons.Outlined.Call) { open(Uri.parse("tel:$phone"), Intent.ACTION_DIAL) }
            if (site != null) OutlinedButton(onClick = { open(Uri.parse(site)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.Language, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(if (ownSite == null) "Provider website" else "Website")
            }
            email?.let { TextButton(onClick = { open(Uri.fromParts("mailto", it, null), Intent.ACTION_SENDTO) }) { Icon(Icons.Outlined.Email, null); Spacer(Modifier.width(8.dp)); Text("Provider email: $it") } }
            if (phone == null && site == null && email == null) Text("Contact details not confirmed", color = Muted)
            directoryProvider?.let { provider -> TextButton(onClick = { model.providerDetail(provider) }) { Text("View ${provider.optString("name")} and programmes"); Icon(Icons.Outlined.ChevronRight, null) } }
            ListingNavigateButton(enabled = !s.detailLoading && !s.detailWithdrawn && service.json.optBoolean("active", true), label = directionsButtonLabel(service.json.optString("location_role"))) { model.route(s.routeMode) }
            contactError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailHeading("Good to know", Icons.Outlined.VolunteerActivism)
            DetailFact("Dietary options", service.strings("diets").joinToString().ifBlank { "Not confirmed. Ask about diets and allergies." }, Icons.Outlined.RestaurantMenu)
            DetailFact("Wheelchair access", if (!service.json.has("wheelchair") || service.json.isNull("wheelchair")) "Not confirmed" else if (service.json.optBoolean("wheelchair")) "Listed as accessible" else "Not listed as accessible", Icons.Outlined.Accessible)
            IconFact(operatingLabel(service), Icons.Outlined.Schedule)
            if (!hasSessions) IconFact("Collection / meal times not confirmed", Icons.Outlined.EventBusy)
        }
        ExpandableSection("Eligibility & how to apply", Icons.Outlined.Groups) {
            Text(accessMethodDetail(service.json.optString("access_method", "contact_to_confirm")))
            Text(service.json.detailText("eligibility") ?: "Eligibility unconfirmed. Ask the provider how to apply.")
            service.json.detailText("delivery_information")?.let { DetailFact("Delivery / collection", it, Icons.Outlined.LocalShipping) }
        }
        service.json.detailText("description")?.let { description ->
            ExpandableSection("About this support", Icons.Outlined.Info) { Text(description, color = Muted) }
        }
        ExpandableSection("Collection & meal times", Icons.Outlined.CalendarMonth) { DistributionCalendar(service) }
        ExpandableSection("Operating hours", Icons.Outlined.Schedule) {
            Text("Opening hours do not confirm meal or collection times.", color = Muted)
            if (hours.isEmpty()) Text("Hours not confirmed", color = Muted)
            hours.forEach { row ->
                val days = row.optJSONArray("days")?.let { a -> (0 until a.length()).map { listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[a.getInt(it)] }.joinToString(", ") }.orEmpty()
                DetailFact(days, "${clock(row.optString("start"))} – ${clock(row.optString("end"))}${if (row.optString("end") < row.optString("start")) " (next day)" else ""}", Icons.Outlined.Schedule)
            }
            service.json.optJSONObject("exceptions")?.let { overrides ->
                overrides.keys().asSequence().sorted().forEach { date ->
                    val rows = overrides.optJSONArray(date)?.objects().orEmpty()
                    Text("$date: ${if (rows.isEmpty()) "Closed" else rows.joinToString { row -> "${clock(row.optString("start"))} – ${clock(row.optString("end"))}" }}", color = Muted)
                }
            }
        }
        if (directoryProvider != null) ExpandableSection("Provider programmes", Icons.Outlined.LocalShipping) { DirectoryDistributionInfo(directoryProvider) }
        ExpandableSection("Location details", Icons.Outlined.LocationOn) {
            Text(collectionLocationLabel(service.json.optString("location_role")), color = Teal)
            Text(service.address)
            service.json.detailText("postal")?.let { Text("Singapore $it", color = Muted) }
            if (service.json.optString("location_role") == "coordination_site") Text("Coordinates delivery. Walk-in collection is not confirmed.", color = Muted)
            if (!service.distance.isNaN()) Text("%.1f km from your search · straight-line distance".format(service.distance), color = Muted)
        }
        val reasons = service.strings("explanations")
        if (reasons.isNotEmpty()) ExpandableSection("Why this matched", Icons.Outlined.CheckCircle) {
            reasons.forEach { IconFact(it, Icons.Outlined.Check) }
        }
        ExpandableSection("Sources & checks", Icons.Outlined.FactCheck) {
            IconFact("Source checked ${formatTime(service.json.optString("verified_at"))}", Icons.Outlined.FactCheck)
            service.json.detailText("imported_at")?.let { IconFact("Imported ${formatTime(it)}", Icons.Outlined.History) }
            service.evidence?.let { evidence ->
                DetailFact("Availability source", evidence.optString("source").ifBlank { "Not confirmed" }, Icons.Outlined.Info)
                Text("Availability observed ${formatTime(evidence.optString("observed_at"))}\nValid until ${formatTime(evidence.optString("valid_until"))}\nConfidence: ${evidence.detailText("confidence") ?: "Not confirmed"}", color = Muted, style = MaterialTheme.typography.bodySmall)
            } ?: Text("Availability evidence not confirmed", color = Muted)
            service.json.detailText("source")?.let { source ->
                Text(source, color = Muted, style = MaterialTheme.typography.bodySmall)
                if (source.startsWith("https://") || source.startsWith("http://")) TextButton(onClick = { open(Uri.parse(source)) }) { Text("Open source") }
            }
        }
        ReportInformationButton(model)
    }
}
