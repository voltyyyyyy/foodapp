package sg.foodbridge.navigator

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
        Text("Report incorrect information")
    }
}

@Composable private fun DetailHeading(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(22.dp), tint = Teal)
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable fun ListingBookmarkButton(saved: Boolean, onClick: () -> Unit) {
    IconToggleButton(checked = saved, onCheckedChange = { onClick() }, modifier = Modifier.size(48.dp)) {
        Icon(if (saved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
            if (saved) "Remove saved listing" else "Save listing", tint = Teal, modifier = Modifier.size(26.dp))
    }
}

@Composable fun ListingNavigateButton(enabled: Boolean = true, label: String = "Navigate", onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
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
                Text("Contact the provider to confirm where to go. An office or coordination address may not be a place to collect food.",
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
private fun scheduleForDate(service: Service, date: LocalDate): List<JSONObject> {
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

@Composable private fun DistributionCalendar(service: Service) {
    val today = LocalDate.now(singaporeZone)
    var selected by remember(service.id) { mutableStateOf(today) }
    var weekStart by remember(service.id) { mutableStateOf(today.minusDays((today.dayOfWeek.value - 1).toLong())) }
    var weeklyExpanded by remember(service.id) { mutableStateOf(false) }
    val recurring = service.json.optJSONArray("distribution_schedules")?.objects() ?: emptyList()
    val exceptions = service.json.optJSONObject("distribution_exceptions")
    val sessions = scheduleForDate(service, selected)
    val override = exceptions?.has(selected.toString()) == true
    val unknown = recurring.isEmpty() && !override && sessions.isEmpty()
    val days = (0L..6L).map { weekStart.plusDays(it) }
    val monthFormat = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailHeading("Distribution schedule", Icons.Outlined.CalendarMonth)
        Text("Singapore time. Scheduled sessions do not guarantee food availability.", color = Muted, style = MaterialTheme.typography.bodySmall)
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    days.forEach { date ->
                        val hasSession = scheduleForDate(service, date).isNotEmpty()
                        val isSelected = date == selected
                        Surface(shape = RoundedCornerShape(12.dp), color = if (isSelected) Teal else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.weight(1f).semantics { contentDescription = "${date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH))}, ${if (hasSession) "recorded session" else "no recorded session"}" }.selectable(selected = isSelected, role = Role.Tab, onClick = { selected = date })) {
                            Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(date.dayOfWeek.name.take(1), color = if (isSelected) androidx.compose.ui.graphics.Color.White else Muted, style = MaterialTheme.typography.labelMedium)
                                Text(date.dayOfMonth.toString(), color = if (isSelected) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                Text(if (hasSession) "•" else " ", color = if (isSelected) androidx.compose.ui.graphics.Color.White else Teal, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Text("Dots indicate recorded sessions.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(selected.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)), fontWeight = FontWeight.Bold)
        if (override) Text("Date-specific schedule", color = Teal, style = MaterialTheme.typography.labelMedium)
        when {
            unknown -> Text("Schedule not confirmed. Contact the provider to ask about dates and hours.", color = Muted)
            sessions.isEmpty() -> Text("No session is recorded for this date. Confirm any alternative arrangements with the provider.", color = Muted)
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
                Text("Regular collection / meal sessions"); Icon(if (weeklyExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
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
    val mode = s.routeMode
    var evidenceExpanded by remember(service.id) { mutableStateOf(false) }
    var calendarExpanded by remember(service.id) { mutableStateOf(false) }
    var contactError by remember(service.id) { mutableStateOf<String?>(null) }
    fun open(uri: Uri, action: String = Intent.ACTION_VIEW) {
        runCatching { context.startActivity(Intent(action, uri)) }.onFailure { contactError = "No app can open this contact. Use the details shown below." }
    }
    val phone = service.json.detailText("phone")
    val site = service.json.detailText("website")
    Page {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(service.type, color = Teal, style = MaterialTheme.typography.labelLarge)
            Text(service.name, style = MaterialTheme.typography.headlineMedium)
            if (service.organisationName != service.name) Text(service.organisationName, color = Muted)
            if (service.demo) Text("Demo listing — fictional service", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            if (service.json.optString("location_role") !in listOf("collection_site", "confirmed_collection_site")) CollectionPointWarning()
            ListingNavigateButton(enabled = !s.loading,
                label = directionsButtonLabel(service.json.optString("location_role")).replace("Get directions", "Navigate")) { model.route(mode) }
            Text(service.address, color = Muted, style = MaterialTheme.typography.bodyMedium)
            Text(collectionLocationLabel(service.json.optString("location_role")), color = Teal, style = MaterialTheme.typography.bodySmall)
            Text("Contact the provider before travelling. Food availability is not guaranteed.", color = Muted, style = MaterialTheme.typography.bodySmall)
            service.json.detailText("description")?.let { Text(it, color = Muted) }
            ReportInformationButton(model)
        }
        if (!service.json.optBoolean("active", true)) Panel(Peach) { Text("This service is no longer active", fontWeight = FontWeight.Bold); Text("Do not travel here for support. Contact the provider or choose another service.") }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailHeading("Before you go", Icons.Outlined.Info)
            Availability(service.availability)
            AvailabilityEvidence(service)
            Text(operatingLabel(service), color = Muted)
            Text("Contact the provider before travelling. Opening hours and available food can change.", color = Muted, style = MaterialTheme.typography.bodyMedium)
            service.evidence?.let { Text("Latest availability observation: ${formatTime(it.optString("observed_at"))}", color = Muted, style = MaterialTheme.typography.bodySmall) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailHeading("Contact", Icons.Outlined.ContactPhone)
            if (phone != null) { Primary("Call provider", Icons.Outlined.Call) { open(Uri.parse("tel:$phone"), Intent.ACTION_DIAL) }; Text(phone, color = Muted) }
            if (site != null) OutlinedButton(onClick = { open(Uri.parse(site)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Outlined.Language, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Provider website") }
            if (phone == null && site == null) Text("Contact information not confirmed", color = Muted)
            contactError?.let { Text(it, color = Muted) }
        }
        HorizontalDivider()
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DetailHeading("Support details", Icons.Outlined.VolunteerActivism)
            DetailFact("Application / referral", accessMethodDetail(service.json.optString("access_method", "contact_to_confirm")), Icons.Outlined.Groups)
            DetailFact("Who can receive support / how to apply", service.json.detailText("eligibility") ?: "Eligibility not confirmed. Ask the provider how to apply.", Icons.Outlined.Groups)
            service.json.detailText("delivery_information")?.let { DetailFact("Delivery / collection arrangements", it, Icons.Outlined.LocalShipping) }
            DetailFact("Confirmed dietary options", service.strings("diets").joinToString().ifBlank { "Not confirmed. Ask about dietary requirements and allergies." }, Icons.Outlined.RestaurantMenu)
            DetailFact("Wheelchair access", if (service.json.isNull("wheelchair")) "Not confirmed" else if (service.json.optBoolean("wheelchair")) "Accessible according to the listing" else "Not recorded as accessible", Icons.Outlined.Accessible)
            val reasons = service.strings("explanations")
            if (reasons.isNotEmpty()) {
                Text("Why it matched your search", fontWeight = FontWeight.Bold)
                reasons.forEach { reason -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = Teal); Text(reason, color = Teal, style = MaterialTheme.typography.bodySmall) } }
            }
        }
        HorizontalDivider()
        val directoryProvider = s.providers.firstOrNull { it.optString("id") == service.json.optString("provider_id") }
        if (directoryProvider != null) DirectoryDistributionInfo(directoryProvider)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DetailHeading("Operating hours", Icons.Outlined.Schedule)
            Text("These hours do not confirm collection or meal sessions.", color = Muted)
            val hours = service.json.optJSONArray("schedules")?.objects().orEmpty()
            if (hours.isEmpty()) Text("Operating hours not confirmed", color = Muted)
            hours.forEach { row ->
                val days = row.optJSONArray("days")?.let { a -> (0 until a.length()).map { listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[a.getInt(it)] }.joinToString(", ") }.orEmpty()
                Text("$days · ${clock(row.optString("start"))} – ${clock(row.optString("end"))}${if (row.optString("end") < row.optString("start")) " (next day)" else ""}")
            }
            service.json.optJSONObject("exceptions")?.let { overrides ->
                overrides.keys().asSequence().sorted().forEach { date ->
                    val rows = overrides.optJSONArray(date)?.objects().orEmpty()
                    Text("$date: ${if (rows.isEmpty()) "Closed" else rows.joinToString { row -> "${clock(row.optString("start"))} – ${clock(row.optString("end"))}" }}", color = Muted)
                }
            }
        }
        val hasHours = (service.json.optJSONArray("distribution_schedules")?.length() ?: 0) > 0 || (service.json.optJSONObject("distribution_exceptions")?.length() ?: 0) > 0
        if (hasHours || directoryProvider == null) DistributionCalendar(service)
        else {
            TextButton(onClick = { calendarExpanded = !calendarExpanded }) {
                Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                Text(if (calendarExpanded) "Hide calendar" else "View calendar (sessions not confirmed)")
            }
            if (calendarExpanded) DistributionCalendar(service)
        }
        HorizontalDivider()
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailHeading("Location & directions", Icons.Outlined.LocationOn)
            Text(collectionLocationLabel(service.json.optString("location_role")), fontWeight = FontWeight.Bold, color = Teal)
            Text(service.address)
            Text("Singapore ${service.json.optString("postal")}", color = Muted)
            if (service.json.optString("location_role") == "coordination_site") Text("This is a meal-delivery coordination site, not a confirmed walk-in collection point. ${accessMethodDetail(service.json.optString("access_method", "contact_to_confirm"))}", color = Muted, style = MaterialTheme.typography.bodyMedium)
            if (!service.distance.isNaN()) Text("%.1f km from your search address (straight-line distance)".format(service.distance), color = Muted, style = MaterialTheme.typography.bodySmall)

        }
        HorizontalDivider()
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().clickable { evidenceExpanded = !evidenceExpanded }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.FactCheck, null, Modifier.size(22.dp), tint = Teal); Spacer(Modifier.width(10.dp))
                Text("Sources & verification", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(if (evidenceExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, "Toggle source details")
            }
            Text("Record checked ${formatTime(service.json.optString("verified_at"))}", color = Muted, style = MaterialTheme.typography.bodySmall)
            if (evidenceExpanded) {
                service.evidence?.let { evidence ->
                    DetailFact("Availability evidence", evidence.optString("source"), Icons.Outlined.Info)
                    Text("Observed: ${formatTime(evidence.optString("observed_at"))}\nValid until: ${formatTime(evidence.optString("valid_until"))}\nConfidence: ${evidence.detailText("confidence") ?: "Not confirmed"}", color = Muted, style = MaterialTheme.typography.bodySmall)
                } ?: Text("Availability evidence not confirmed", color = Muted)
                Text("Record source: ${service.json.optString("source")}", color = Muted, style = MaterialTheme.typography.bodySmall)
                service.json.detailText("source")?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { url -> TextButton(onClick = { open(Uri.parse(url)) }) { Text("Open source") } }
            }
        }
        ReportInformationButton(model)
        if (service.demo) DemoNotice()
    }
}
