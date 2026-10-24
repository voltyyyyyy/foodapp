package sg.foodbridge.navigator

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.heading
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
fun providerBookmarkId(provider: JSONObject) = "provider:${provider.getString("id")}"

@Composable fun SavedProviderCard(provider: JSONObject, s: UiState, model: AppViewModel) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable { model.providerDetail(provider) }) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Provider", color = Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                ListingBookmarkButton(saved = providerBookmarkId(provider) in s.saved,
                    onClick = { model.removeOrSave(providerBookmarkId(provider)) })
            }
            Text(provider.getString("name"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ProviderBadges(provider.types())
            Text(collectionLocationLabel(provider.optString("location_type")), color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
private fun factText(value: Any?): String = when (value) {
    null, JSONObject.NULL -> "Not listed"
    is Boolean -> if (value) "Yes" else "No"
    is JSONArray -> (0 until value.length()).joinToString(", ") { factText(value.opt(it)) }
    is JSONObject -> value.keys().asSequence().joinToString("; ") { "${it.replace('_', ' ')}: ${factText(value.opt(it))}" }
    else -> value.toString()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ProviderBadges(types: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        types.forEach { type -> Surface(color = Mint, shape = RoundedCornerShape(8.dp)) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(foodSupportIcon(type), null, Modifier.size(16.dp), tint = Teal); Spacer(Modifier.width(6.dp))
                Text(type, color = Teal, style = MaterialTheme.typography.labelMedium)
            }
        } }
    }
}

@Composable private fun ProviderHeading(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(22.dp), tint = Teal)
        Text(title, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
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
    val context = LocalContext.current
    var linkError by remember(name) { mutableStateOf<String?>(null) }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }.clickable(role = Role.Button, onClickLabel = if (expanded) "Collapse $name" else "Expand $name") { expanded = !expanded }.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(foodSupportIcon(name), null, tint = Teal)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name, fontWeight = FontWeight.Bold)
                    programme.text("frequency")?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
                }
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (expanded) "Collapse $name" else "Expand $name")
            }
            if (expanded) Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (distributionFocus) {
                    ProgrammeFact("Frequency", programme.text("frequency") ?: "Not listed", Icons.Outlined.Repeat)
                    ProgrammeFact("Listed days", factText(programme.opt("distribution_days_raw")).takeUnless { it.isBlank() || it == "Not listed" } ?: "Days unconfirmed. Ask the provider.", Icons.Outlined.CalendarMonth)
                    ProgrammeFact("Listed dates", factText(programme.opt("distribution_dates_raw")).takeUnless { it.isBlank() || it == "Not listed" } ?: "Dates unconfirmed. Ask the provider.", Icons.Outlined.Event)
                }
                ProgrammeFact("Who it supports", factText(programme.opt("recipients")), Icons.Outlined.Groups)
                ProgrammeFact("Delivery / collection", factText(programme.opt("delivery_methods")), Icons.Outlined.LocalShipping)
                ProgrammeFact("Service regions", factText(programme.opt("regions")), Icons.Outlined.LocationOn)
                ProgrammeFact("Areas", factText(programme.opt("estate_group_raw")), Icons.Outlined.NearMe)
                ProgrammeFact("Dietary options", factText(programme.opt("dietary_preferences")), Icons.Outlined.RestaurantMenu)
                if (!distributionFocus) {
                    ProgrammeFact("Listed days", factText(programme.opt("distribution_days_raw")), Icons.Outlined.CalendarMonth)
                    ProgrammeFact("Listed dates", factText(programme.opt("distribution_dates_raw")), Icons.Outlined.Event)
                }
                programme.text("notes")?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
                listOf("application_url" to "Apply on provider website", "source" to "Programme source").forEach { (field, label) ->
                    programme.text(field)?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { url ->
                        TextButton(onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                .onFailure { linkError = "No browser available. Try the provider contact instead." }
                        }) { Icon(Icons.Outlined.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(label) }
                    }
                }
                linkError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable fun DirectoryDistributionInfo(provider: JSONObject) {
    val context = LocalContext.current
    val facts = provider.optJSONObject("directory_facts") ?: return
    val reviewed = facts.optJSONObject("reviewed_programmes")
    if (reviewed != null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (reviewed.length() == 0) IconFact("Current programme details are not confirmed. Ask the provider.", Icons.Outlined.Info)
            reviewed.keys().asSequence().toList().forEach { name ->
                ProgrammeCard(name, reviewed.getJSONObject(name), false, distributionFocus = true)
            }
            facts.optJSONObject("research_review")?.text("reviewed_at")?.let {
                Text("Sources checked ${formatTime(it)}. Food availability is not confirmed.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            if (facts.optJSONObject("programmes") != null) ExpandableSection("Older FoodConnect details", Icons.Outlined.History) {
                Text("Older directory facts may differ from the provider's current information.", color = Muted, style = MaterialTheme.typography.bodySmall)
                DirectoryDistributionInfo(JSONObject(provider.toString()).put("directory_facts", JSONObject(facts.toString()).apply { remove("reviewed_programmes") }))
            }
        }
        return
    }
    val programmes = facts.optJSONObject("programmes") ?: return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ProviderHeading("Provider programmes", Icons.Outlined.LocalShipping)
        Text("FoodConnect directory details. Ask the provider to confirm your arrangements.", color = Muted, style = MaterialTheme.typography.bodySmall)
        provider.types().filter { programmes.optJSONObject(it)?.optBoolean("listed") == true }.forEach { name ->
            ProgrammeCard(name, programmes.getJSONObject(name), false, distributionFocus = true)
        }
        Text("Directory source updated ${facts.optString("source_page_last_updated", "date unknown")}. Frequency does not confirm dates or food availability.", color = Muted, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(provider.optString("source", "https://foodconnect.gov.sg/directory/")))) } }) { Text("View directory source") }
    }
}

@Composable fun ProvidersScreen(s: UiState, model: AppViewModel) {
    val query = s.providerQuery
    val category = s.providerCategory
    val listState = rememberLazyListState(s.providerListIndex, s.providerListOffset)
    var previousFilter by remember { mutableStateOf(query to category) }
    LaunchedEffect(query, category) {
        if (previousFilter != (query to category)) { listState.scrollToItem(0); previousFilter = query to category }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { (index, offset) ->
            model.update { it.copy(providerListIndex = index, providerListOffset = offset) }
        }
    }
    var menu by remember { mutableStateOf(false) }
    val categories = listOf("All") + s.providers.flatMap { it.types() }.distinct().sorted()
    val filtered = s.providers.filter { provider ->
        (category == "All" || category in provider.types()) &&
            listOf(provider.optString("name"), provider.optString("description"), provider.types().joinToString()).any { it.contains(query, ignoreCase = true) }
    }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Providers", style = MaterialTheme.typography.headlineMedium)
                IconFact("Ask providers about eligibility and availability.", Icons.Outlined.Info)
                OutlinedTextField(query, { value -> model.update { it.copy(providerQuery = value, providerListIndex = 0, providerListOffset = 0) } }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Search providers") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, shape = RoundedCornerShape(14.dp))
                Box {
                    OutlinedButton(onClick = { menu = true }) { Text(if (category == "All") "All support types" else category); Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.ExpandMore, null) }
                    DropdownMenu(menu, { menu = false }) { categories.forEach { value -> DropdownMenuItem(text = { Text(if (value == "All") "All support types" else value) }, onClick = { model.update { it.copy(providerCategory = value, providerListIndex = 0, providerListOffset = 0) }; menu = false }) } }
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
                IconFact(accessMethodLabel(provider.optString("access_method", if (provider.optString("verification_status") == "provider_reviewed") "contact_to_confirm" else "unverified")), Icons.Outlined.Groups)
                IconFact(collectionLocationLabel(provider.optString("location_type")), Icons.Outlined.LocationOn)
                provider.optJSONObject("directory_facts")?.optJSONArray("aggregate_regions")?.takeIf { it.length() > 0 }?.let { regions ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.LocationOn, null, Modifier.size(16.dp), tint = Muted)
                        Text("Listed in ${factText(regions)}", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }

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
    val s by model.state.collectAsState()
    var actionError by remember { mutableStateOf<String?>(null) }
    fun open(uri: Uri, action: String = Intent.ACTION_VIEW, failureMessage: String = "No app is available to open this contact. Use the details below.") {
        runCatching { context.startActivity(Intent(action, uri)) }.onFailure { actionError = failureMessage }
    }
    val address = provider.text("address")
    val linkedServices = s.services.filter { it.json.optString("provider_id") == provider.getString("id") && it.json.optBoolean("active", true) }
    var chooseDestination by remember(provider.getString("id")) { mutableStateOf(false) }
    if (chooseDestination) AlertDialog(onDismissRequest = { chooseDestination = false },
        title = { Text("Choose service to review") },
        text = { Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            linkedServices.forEach { service -> TextButton(onClick = {
                chooseDestination = false; model.detail(service)
            }) { Column(Modifier.fillMaxWidth()) { Text(service.name); Text(service.address, color = Muted, style = MaterialTheme.typography.bodySmall) } } }
        } }, confirmButton = { TextButton(onClick = { chooseDestination = false }) { Text("Cancel") } })
    Page {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(provider.getString("name"), style = MaterialTheme.typography.headlineMedium)
            ProviderBadges(provider.types())
            if (provider.optString("location_type") !in listOf("collection_site", "confirmed_collection_site")) CollectionPointWarning()
            else Text(collectionLocationLabel(provider.optString("location_type")), fontWeight = FontWeight.Bold, color = Teal)
            IconFact("Can I use this? ${provider.text("eligibility") ?: "Ask the provider about eligibility."}", Icons.Outlined.Groups)
            IconFact(accessMethodDetail(provider.optString("access_method", "unverified")), Icons.Outlined.Assignment)
            IconFact(if (linkedServices.isEmpty()) "Collection / meal times not confirmed. Ask the provider." else "${linkedServices.size} linked service location${if (linkedServices.size == 1) "" else "s"}; review the location and recorded session below.", Icons.Outlined.CalendarMonth)
            if (linkedServices.isEmpty() && address == null) Text("No address confirmed for directions.", color = Muted, style = MaterialTheme.typography.bodySmall)
            actionError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            IconFact("Contact the provider before going. Food is not guaranteed.", Icons.Outlined.Info)
            if (provider.optString("verification_status") != "provider_reviewed") IconFact("Directory details need confirmation.", Icons.Outlined.FactCheck)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProviderHeading("Contact", Icons.Outlined.ContactPhone)
            if (provider.text("contact_source") == null) Text("FoodConnect contact details · confirm they are current.", color = Muted, style = MaterialTheme.typography.bodySmall)
            provider.text("phone")?.let { phone -> Primary("Call $phone", Icons.Outlined.Call) { open(Uri.parse("tel:$phone"), Intent.ACTION_DIAL) } }
            provider.text("website")?.let { url -> OutlinedButton(onClick = { open(Uri.parse(url)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Outlined.Language, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Website") } }
            provider.text("email")?.let { email -> TextButton(onClick = { open(Uri.fromParts("mailto", email, null), Intent.ACTION_SENDTO) }) { Icon(Icons.Outlined.Email, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(email) } }
            actionError?.let { Text(it, color = Muted) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ProviderHeading("Service locations", Icons.Outlined.LocationOn)
            when {
                s.loading -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Loading service locations…", color = Muted) }
                linkedServices.isEmpty() && s.error != null -> { Text("Service locations could not be checked. Ask the provider or retry loading.", color = Muted); TextButton(onClick = model::refresh) { Text("Retry loading locations") } }
                linkedServices.isEmpty() -> Text("No linked service locations are confirmed. Ask the provider where support is offered.", color = Muted)
            }
            linkedServices.forEach { service ->
                Surface(shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Review ${service.name}") { model.detail(service) }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(service.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(service.type, color = Teal)
                        IconFact(service.address, Icons.Outlined.LocationOn)
                        IconFact(collectionLocationLabel(service.json.optString("location_role")), Icons.Outlined.Info)
                        IconFact(nextSessionLabel(service), Icons.Outlined.CalendarMonth)
                        Availability(service.availability)
                        Text("Review support and eligibility", color = Teal, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            ListingNavigateButton(enabled = linkedServices.isNotEmpty() || address != null,
                label = when {
                    linkedServices.isEmpty() && address != null -> "Directions to provider address"
                    linkedServices.size > 1 -> "Choose service to review"
                    else -> "Review service location"
                }) {
                when {
                    linkedServices.size == 1 -> model.detail(linkedServices.first())
                    linkedServices.size > 1 -> chooseDestination = true
                    address != null -> open(Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(address + ", Singapore")}"),
                        failureMessage = "No map or browser is available. Use the provider address shown in this listing.")
                }
            }
        }
        ExpandableSection("Eligibility & how to apply", Icons.Outlined.Groups) {
            Text(accessMethodDetail(provider.optString("access_method", if (provider.optString("verification_status") == "provider_reviewed") "contact_to_confirm" else "unverified")), fontWeight = FontWeight.SemiBold)
            Text(provider.text("eligibility") ?: "Ask the provider about eligibility and how to apply.")
        }
        provider.text("address")?.let { address ->
            ExpandableSection("Provider address", Icons.Outlined.LocationOn) {
                Text(address)
                Text(provider.text("location_notes") ?: "Collection is not confirmed here. Contact the provider first.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        ExpandableSection("Support programmes", Icons.Outlined.LocalShipping) { DirectoryDistributionInfo(provider) }
        ExpandableSection("Sources & checks", Icons.Outlined.FactCheck) {
            Text("Source checked ${formatTime(provider.optString("verified_at"))}", color = Muted, style = MaterialTheme.typography.bodySmall)
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
        ReportInformationButton(model)
    }
}
