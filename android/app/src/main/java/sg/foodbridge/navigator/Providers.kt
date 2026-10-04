package sg.foodbridge.navigator

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import org.json.JSONArray

private fun JSONObject.text(key: String) = optString(key).takeUnless { it.isBlank() || it == "null" }
private fun JSONObject.types() = optJSONArray("support_types")?.let { array -> (0 until array.length()).map { array.getString(it) } } ?: emptyList()
private fun factText(value: Any?): String = when (value) {
    null, JSONObject.NULL -> "Not listed"
    is Boolean -> if (value) "Yes" else "No"
    is JSONArray -> (0 until value.length()).joinToString(", ") { factText(value.opt(it)) }
    is JSONObject -> value.keys().asSequence().joinToString("; ") { "${it.replace('_', ' ')}: ${factText(value.opt(it))}" }
    else -> value.toString()
}

private fun supportIcon(type: String): ImageVector = when (type) {
    "Cooked Meals" -> Icons.Outlined.Restaurant
    "Food Rations" -> Icons.Outlined.ShoppingBag
    "Food Vouchers" -> Icons.Outlined.LocalOffer
    "Fresh Produce" -> Icons.Outlined.Eco
    else -> Icons.Outlined.VolunteerActivism
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ProviderBadges(types: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        types.forEach { type -> Surface(color = Mint, shape = RoundedCornerShape(8.dp)) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(supportIcon(type), null, Modifier.size(16.dp), tint = Teal); Spacer(Modifier.width(6.dp))
                Text(type, color = Teal, style = MaterialTheme.typography.labelMedium)
            }
        } }
    }
}

@Composable private fun ProviderHeading(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(22.dp), tint = Teal)
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable private fun ProgrammeFact(label: String, value: String, icon: ImageVector) {
    if (value.isBlank() || value == "Not listed") return
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.padding(top = 4.dp).size(19.dp), tint = Muted)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, color = Muted, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable private fun ProgrammeCard(name: String, programme: JSONObject, initiallyExpanded: Boolean, distributionFocus: Boolean = false) {
    var expanded by remember(name, programme.toString()) { mutableStateOf(initiallyExpanded) }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(supportIcon(name), null, tint = Teal)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name, fontWeight = FontWeight.Bold)
                    programme.text("frequency")?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
                }
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (expanded) "Collapse $name" else "Expand $name")
            }
            if (expanded) Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (distributionFocus) {
                    ProgrammeFact("Frequency", programme.text("frequency") ?: "Not specified in the directory", Icons.Outlined.Repeat)
                    ProgrammeFact("Listed days", factText(programme.opt("distribution_days_raw")).takeUnless { it.isBlank() || it == "Not listed" } ?: "Not specified. Ask the provider to confirm.", Icons.Outlined.CalendarMonth)
                    ProgrammeFact("Listed dates", factText(programme.opt("distribution_dates_raw")).takeUnless { it.isBlank() || it == "Not listed" } ?: "No specific dates listed. Ask the provider to confirm.", Icons.Outlined.Event)
                }
                ProgrammeFact("Who it supports", factText(programme.opt("recipients")), Icons.Outlined.Groups)
                ProgrammeFact("Delivery or collection", factText(programme.opt("delivery_methods")), Icons.Outlined.LocalShipping)
                ProgrammeFact("Listed service regions", factText(programme.opt("regions")), Icons.Outlined.LocationOn)
                ProgrammeFact("Listed areas", factText(programme.opt("estate_group_raw")), Icons.Outlined.NearMe)
                ProgrammeFact("Listed dietary options", factText(programme.opt("dietary_preferences")), Icons.Outlined.RestaurantMenu)
                if (!distributionFocus) {
                    ProgrammeFact("Listed days", factText(programme.opt("distribution_days_raw")), Icons.Outlined.CalendarMonth)
                    ProgrammeFact("Listed dates", factText(programme.opt("distribution_dates_raw")), Icons.Outlined.Event)
                }
            }
        }
    }
}

@Composable fun DirectoryDistributionInfo(provider: JSONObject) {
    val context = LocalContext.current
    val facts = provider.optJSONObject("directory_facts") ?: return
    val programmes = facts.optJSONObject("programmes") ?: return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ProviderHeading("Directory distribution details", Icons.Outlined.LocalShipping)
        Text("FoodConnect lists these arrangements for ${provider.getString("name")}. They may cover several programmes or locations; confirm your collection or delivery arrangements with the provider.", color = Muted, style = MaterialTheme.typography.bodySmall)
        provider.types().filter { programmes.optJSONObject(it)?.optBoolean("listed") == true }.forEachIndexed { index, name ->
            ProgrammeCard(name, programmes.getJSONObject(name), index == 0, distributionFocus = true)
        }
        Text("Directory page last updated ${facts.optString("source_page_last_updated", "date not stated")}. Listed frequency does not establish exact opening hours or live food availability.", color = Muted, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(provider.optString("source", "https://foodconnect.gov.sg/directory/")))) } }) { Text("View directory source") }
    }
}

@Composable fun ProvidersScreen(s: UiState, model: AppViewModel) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var menu by remember { mutableStateOf(false) }
    val categories = listOf("All") + s.providers.flatMap { it.types() }.distinct().sorted()
    val filtered = s.providers.filter { provider ->
        (category == "All" || category in provider.types()) &&
            listOf(provider.optString("name"), provider.optString("description"), provider.types().joinToString()).any { it.contains(query, ignoreCase = true) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Food support providers", style = MaterialTheme.typography.headlineMedium)
                Text("Explore Singapore's FoodConnect directory, including delivery, vouchers and referrals.", color = Muted)
                Text("General directory. Contact each provider to confirm eligibility, dietary needs and availability.", color = Muted, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("Search providers or support") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, shape = RoundedCornerShape(14.dp))
                Box {
                    OutlinedButton(onClick = { menu = true }) { Text(if (category == "All") "All support types" else category); Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.ExpandMore, null) }
                    DropdownMenu(menu, { menu = false }) { categories.forEach { value -> DropdownMenuItem(text = { Text(if (value == "All") "All support types" else value) }, onClick = { category = value; menu = false }) } }
                }
                if (s.providerLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (s.providerOffline) Text("Offline · saved provider directory", color = Muted, style = MaterialTheme.typography.bodySmall)
                s.providerError?.let { Text(it, color = Muted); TextButton(onClick = model::loadProviders) { Text("Retry") } }
                Text("${filtered.size} providers", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        items(filtered, key = { it.getString("id") }) { provider ->
            Column(Modifier.fillMaxWidth().clickable { model.providerDetail(provider) }.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(provider.getString("name"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Icon(Icons.Outlined.ChevronRight, "View provider", tint = Muted)
                }
                ProviderBadges(provider.types())
                provider.optJSONObject("directory_facts")?.optJSONArray("aggregate_regions")?.takeIf { it.length() > 0 }?.let { regions ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.LocationOn, null, Modifier.size(16.dp), tint = Muted)
                        Text("Listed in ${factText(regions)}", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(if (provider.optString("verification_status") == "provider_reviewed") "Provider sources reviewed · confirm before requesting" else "Directory listing · details need confirmation", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
        }
        if (filtered.isEmpty() && !s.providerLoading && s.providerError == null) item {
            Text(if (s.providers.isEmpty()) "The directory is being prepared. Try again shortly." else "No providers match. Try another name or support type.", color = Muted)
        }
    }
}

@Composable fun ProviderDetailScreen(provider: JSONObject, model: AppViewModel) {
    val context = LocalContext.current
    var sourceDetails by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    fun open(uri: Uri, action: String = Intent.ACTION_VIEW) {
        runCatching { context.startActivity(Intent(action, uri)) }.onFailure { actionError = "No app is available to open this contact. Use the details below." }
    }
    Page {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(provider.getString("name"), style = MaterialTheme.typography.headlineMedium)
            ProviderBadges(provider.types())
            ReportInformationButton(model)
            if (provider.optString("verification_status") != "provider_reviewed") Text("This listing comes from FoodConnect. Current programme and contact details need confirmation.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProviderHeading("Contact", Icons.Outlined.ContactPhone)
            if (provider.text("contact_source") == null) Text("These contact details are from FoodConnect. Confirm they are still current.", color = Muted, style = MaterialTheme.typography.bodySmall)
            provider.text("phone")?.let { phone -> Primary("Call provider", Icons.Outlined.Call) { open(Uri.parse("tel:$phone"), Intent.ACTION_DIAL) }; Text(phone, color = Muted) }
            provider.text("website")?.let { url -> OutlinedButton(onClick = { open(Uri.parse(url)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Outlined.Language, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Provider website") } }
            provider.text("email")?.let { email -> TextButton(onClick = { open(Uri.fromParts("mailto", email, null), Intent.ACTION_SENDTO) }) { Icon(Icons.Outlined.Email, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(email) } }
            actionError?.let { Text(it, color = Muted) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProviderHeading("Getting support", Icons.Outlined.VolunteerActivism)
            Text(provider.text("eligibility") ?: "Contact the provider to ask how to apply and whether you are eligible.")
            Text("Confirm current availability, dietary needs and any collection arrangements with the provider.", color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        provider.text("address")?.let { address ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProviderHeading("Address", Icons.Outlined.LocationOn)
                Text(address)
                Text(provider.text("location_notes") ?: "This address may be an office. Food collection is only available if the provider confirms it.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        DirectoryDistributionInfo(provider)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { sourceDetails = !sourceDetails }) { Icon(Icons.Outlined.FactCheck, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Sources and review notes"); Spacer(Modifier.width(8.dp)); Icon(if (sourceDetails) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null) }
            if (sourceDetails) {
                Text("Reviewed ${formatTime(provider.optString("verified_at"))}", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text(provider.optString("verification_notes"), color = Muted, style = MaterialTheme.typography.bodyMedium)
                Text(provider.getString("description"), color = Muted, style = MaterialTheme.typography.bodyMedium)
                provider.text("source")?.let { url -> TextButton(onClick = { open(Uri.parse(url)) }) { Text("FoodConnect directory source") } }
                provider.text("provider_source")?.let { url -> TextButton(onClick = { open(Uri.parse(url)) }) { Text("Provider source") } }
                provider.text("contact_source")?.takeIf { it != provider.text("provider_source") }?.let { url -> TextButton(onClick = { open(Uri.parse(url)) }) { Text("Contact source") } }
                provider.optJSONObject("directory_facts")?.let { facts ->
                    Text("FoodConnect page last updated ${facts.optString("source_page_last_updated", "Not stated")}", color = Muted, style = MaterialTheme.typography.bodySmall)
                    listOf("directory_contact" to "Original listed contact", "directory_email" to "Original listed email", "directory_website" to "Original listed website").forEach { (key, label) ->
                        val value = factText(facts.opt(key))
                        if (value.isNotBlank() && value != "Not listed") Text("$label: $value", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable fun ProviderStaffDetail(item: JSONObject, s: UiState, model: AppViewModel) {
    var name by remember(item) { mutableStateOf(item.getString("name")) }
    var description by remember(item) { mutableStateOf(item.getString("description")) }
    var eligibility by remember(item) { mutableStateOf(item.optString("eligibility")) }
    var notes by remember(item) { mutableStateOf(item.getString("verification_notes")) }
    var phone by remember(item) { mutableStateOf(item.text("phone").orEmpty()) }
    var email by remember(item) { mutableStateOf(item.text("email").orEmpty()) }
    var website by remember(item) { mutableStateOf(item.text("website").orEmpty()) }
    var active by remember(item) { mutableStateOf(item.optBoolean("active", true)) }
    Page {
        Text("Review provider", style = MaterialTheme.typography.headlineMedium)
        Text("${item.optString("state")} · ${item.optString("source")}", color = Muted)
        OutlinedTextField(name, { name = it }, label = { Text("Provider name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(description, { description = it }, label = { Text("Support description") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        OutlinedTextField(eligibility, { eligibility = it }, label = { Text("Getting support") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        OutlinedTextField(phone, { phone = it }, label = { Text("Approved phone (optional)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it }, label = { Text("Public contact email (optional)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(website, { website = it }, label = { Text("Provider website (optional)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(notes, { notes = it }, label = { Text("Source review notes") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Toggle("Provider listing is active", active) { active = it }
        Text("Review contact details and programme arrangements against the provider's current sources before publishing.", color = Muted)
        Primary("Save provider", Icons.Outlined.Check, !s.loading) {
            model.staffAction("/api/admin/providers/${item.getString("id")}", "PATCH", JSONObject().put("version", item.getInt("version")).put("proposed", JSONObject()
                .put("name", name).put("description", description).put("eligibility", eligibility).put("verification_notes", notes)
                .put("phone", phone.ifBlank { null } ?: JSONObject.NULL).put("email", email.ifBlank { null } ?: JSONObject.NULL)
                .put("website", website.ifBlank { null } ?: JSONObject.NULL).put("active", active)))
        }
        if (item.optString("state") == "PENDING") Primary("Publish provider", Icons.Outlined.Publish, !s.loading) {
            model.staffAction("/api/admin/providers/${item.getString("id")}/publish", body = JSONObject())
        }
    }
}
