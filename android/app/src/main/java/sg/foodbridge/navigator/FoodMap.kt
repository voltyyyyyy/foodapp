package sg.foodbridge.navigator

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MapScreen(s: UiState, model: AppViewModel) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var filters by remember { mutableStateOf(false) }
    var permissionAsked by rememberSaveable { mutableStateOf(false) }
    var permissionRevision by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) permissionRevision++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var mapProvider by remember { mutableStateOf<JSONObject?>(null) }
    var mapCenter by remember { mutableStateOf(LatLng(1.3521, 103.8198)) }
    var moved by remember { mutableStateOf(false) }
    var showSuggestions by remember { mutableStateOf(false) }
    var showStart by remember { mutableStateOf(false) }
    var routeDetails by remember { mutableStateOf(false) }
    var savedFocus by remember { mutableStateOf<LatLng?>(null) }
    val routing = s.routeDestination != null
    val selected = (s.results + s.services).find { it.id == selectedId }
    val savedSelection = selected != null && s.results.none { it.id == selected.id }
    val mapServices = when {
        routing -> listOfNotNull(s.routeDestination)
        s.locationCleared -> emptyList()
        savedSelection -> s.results + listOfNotNull(selected)
        s.hasSearched -> s.results
        else -> s.services
    }
    fun hasPermission() = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun locationEnabled() = context.getSystemService(LocationManager::class.java).isLocationEnabled
    fun openSettings(action: String, app: Boolean = false) {
        runCatching { context.startActivity(android.content.Intent(action).apply { if (app) data = android.net.Uri.parse("package:${context.packageName}") }) }
            .onFailure { model.error("Settings could not open. Enter an address to continue.") }
    }
    fun locate() { focus.clearFocus(); showSuggestions = false; showStart = false; savedFocus = null; moved = false; (context as? MainActivity)?.requestCurrentLocation() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) { if (locationEnabled()) locate() else showStart = true }
        else { model.locationFailed("Location access is off. Open app settings or enter a starting address."); showStart = true }
    }
    fun useDevice() {
        if (!locationEnabled()) showStart = true
        else if (hasPermission()) locate()
        else if (permissionAsked && (context as? android.app.Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION) != true) {
            model.locationFailed("Location permission is off. Open app location settings or enter an address."); showStart = true
        } else { permissionAsked = true; permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) }
    }
    fun preview(match: OrganisationMatch) {
        focus.clearFocus(); showSuggestions = false; moved = false
        // A provider can have several programmes/addresses. Never silently pick its first service.
        selectedId = match.service?.id
        mapProvider = match.provider
        savedFocus = match.service?.let { LatLng(it.latitude, it.longitude) }
    }
    fun search() {
        focus.clearFocus(); showSuggestions = false; selectedId = null; mapProvider = null; savedFocus = null; moved = false
        model.search()
    }
    LaunchedEffect(s.locationError) { if (s.locationError != null) showStart = true }
    LaunchedEffect(s.filtersRequested) { if (s.filtersRequested) { filters = true; model.update { it.copy(filtersRequested = false) } } }
    Column(Modifier.fillMaxSize().imePadding()) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (routing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = model::closeRoute) { Icon(Icons.Outlined.ArrowBack, "Close route preview") }
                        Column(Modifier.weight(1f)) {
                            Text("Route preview · depart now", style = MaterialTheme.typography.titleSmall)
                            Text(s.routeDestination!!.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = { showStart = true }) { Icon(Icons.Outlined.EditLocationAlt, "Choose starting location") }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Triple("drive", "Drive", Icons.Outlined.DirectionsCar), Triple("pt", "Public transport", Icons.Outlined.DirectionsTransit), Triple("walk", "Walk", Icons.Outlined.DirectionsWalk)).forEach { (value, label, icon) ->
                            FilterChip(selected = s.routeMode == value, onClick = { model.route(value) }, label = { Text(label) }, leadingIcon = { Icon(icon, null, Modifier.size(20.dp)) }, modifier = Modifier.heightIn(min = 48.dp))
                        }
                    }
                } else {
                    OutlinedTextField(if (s.origin in listOf("Your location", "Selected map area")) "" else s.origin,
                        { savedFocus = null; selectedId = null; mapProvider = null; model.origin(it) },
                        modifier = Modifier.fillMaxWidth().onFocusChanged { showSuggestions = it.isFocused }, singleLine = true,
                        label = { Text("Address or name") },
                        leadingIcon = { IconButton(onClick = ::search) { Icon(Icons.Outlined.Search, "Search as an address", tint = Teal) } },
                        trailingIcon = { if (s.origin.isNotBlank()) IconButton(onClick = { focus.clearFocus(); showSuggestions = false; model.origin(""); selectedId = null; mapProvider = null; savedFocus = null }) { Icon(Icons.Outlined.Close, "Clear search") } },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val count = s.searchDiets.size + listOf(s.type != "All", s.openOnly, s.wheelchair, s.preferred.isNotBlank(), s.radius != 80).count { it }
                        AssistChip(onClick = { focus.clearFocus(); showSuggestions = false; filters = true }, label = { Text("Filters ($count)") }, leadingIcon = { Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp)) })
                        AssistChip(onClick = model::browseProviders, label = { Text("Providers") })
                        AssistChip(onClick = { if (s.hasSearched) model.tab("Find") else model.browseAllSupport() }, label = { Text("View list") }, leadingIcon = { Icon(Icons.Outlined.List, null, Modifier.size(18.dp)) })
                    }
                }
            }
        }
        if (showSuggestions && !routing) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(16.dp)) {
                item { TextButton(onClick = ::useDevice) { Icon(Icons.Outlined.MyLocation, null); Spacer(Modifier.width(8.dp)); Text("Use device location") } }
                item { Text("Search uses an address. For a provider or service name, choose a match below.", color = Muted, style = MaterialTheme.typography.bodySmall) }
                item { SearchSuggestions(s, model, onMatch = ::preview) { focus.clearFocus(); showSuggestions = false } }
                item { Text("Saved service locations", style = MaterialTheme.typography.titleSmall) }
                items(s.services.filter { it.id in s.saved }, key = { it.id }) { service ->
                    TextButton(onClick = { focus.clearFocus(); showSuggestions = false; mapProvider = null; selectedId = service.id; savedFocus = LatLng(service.latitude, service.longitude) }) { Text(service.name) }
                }
                item { TextButton(onClick = { focus.clearFocus(); showSuggestions = false }) { Text("Return to map") } }
            }
        } else {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                FoodMap(mapServices, if (routing) s.route else null, Modifier.fillMaxSize(), focusPoint = savedFocus,
                    origin = s.originLatitude?.let { lat -> s.originLongitude?.let { LatLng(lat, it) } },
                    onCenter = { mapCenter = it; moved = true }) { selectedId = it; mapProvider = null; savedFocus = null }
                if (moved && !routing) FilledTonalButton(onClick = { selectedId = null; mapProvider = null; savedFocus = null; moved = false; model.searchMapArea(mapCenter.latitude, mapCenter.longitude) }, modifier = Modifier.align(Alignment.TopCenter).padding(8.dp), enabled = !s.loading) { Text("Search this area") }
                SmallFloatingActionButton(onClick = ::useDevice, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp), containerColor = MaterialTheme.colorScheme.surface) {
                    if (s.locationLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.MyLocation, "Use device location")
                }
            }
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().heightIn(max = if (routing) 160.dp else 200.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (routing) {
                        val route = s.route
                        if (s.routeLoading || s.locationLoading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Finding route…") }
                        else if (route?.optBoolean("available") == true) Text("%d min · %.1f km · estimate".format(kotlin.math.ceil(route.optDouble("duration_minutes")).toInt(), route.optDouble("distance_km")), style = MaterialTheme.typography.titleMedium, color = Teal)
                        else Text(route?.optString("message") ?: "Choose a starting location for this route.", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { routeDetails = true }) { Text("Route details & navigation app"); Icon(Icons.Outlined.ExpandMore, null) }
                        if (s.originLatitude == null) TextButton(onClick = { showStart = true }) { Text(if (s.locationCleared) "Starting location cleared — set it again" else "Choose starting location") }
                    } else if (selected != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(selected.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { selectedId = null; savedFocus = null }) { Icon(Icons.Outlined.Close, "Close selected service") }
                        }
                        IconFact(collectionLocationLabel(selected.json.optString("location_role")), Icons.Outlined.LocationOn)
                        IconFact(nextSessionLabel(selected), Icons.Outlined.CalendarMonth)
                        Availability(selected.availability)
                        if (savedSelection || !s.hasSearched) Text("Suitability not checked for this search", color = Muted, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { model.detail(selected) }, modifier = Modifier.fillMaxWidth()) { Text("Review support, contact & directions") }
                    } else if (mapProvider != null) {
                        val provider = mapProvider!!
                        Text(provider.optString("name"), style = MaterialTheme.typography.titleMedium)
                        val linked = s.services.filter { it.json.optString("provider_id") == provider.optString("id") && it.json.optBoolean("active", true) }
                        Text("${linked.size} linked service location${if (linked.size == 1) "" else "s"}. Review the provider and choose where to go.", color = Muted)
                        Button(onClick = { model.providerDetail(provider) }, modifier = Modifier.fillMaxWidth()) { Text("View provider & service locations") }
                        TextButton(onClick = { mapProvider = null }) { Text("Close provider") }
                    } else {
                        Text(when { s.locationCleared -> "Starting location cleared for privacy"; s.loading -> "Finding support…"; s.hasSearched -> "${s.results.size} of ${s.totalMatches} matching services"; else -> "All listed service locations" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(when { s.locationCleared -> "Set a starting location again to continue your search."; s.hasSearched && s.error != null -> "Search could not be completed. Retry or change your address."; s.hasSearched -> "Up to ten nearest matches · straight-line distance from ${s.origin}"; else -> "General listings · filters have not been applied. Set a location to match your needs." }, color = Muted, style = MaterialTheme.typography.bodySmall)
                        if (s.hasSearched) Text(listOfNotNull("Within ${s.radius} km", s.type.takeIf { it != "All" }, s.searchDiets.joinToString().takeIf { it.isNotBlank() }, "Wheelchair access".takeIf { s.wheelchair }, "Operating at chosen time".takeIf { s.openOnly }, s.preferred.takeIf { it.isNotBlank() }?.let(::readableSearchTime)).joinToString(" · ").ifBlank { "No dietary or access filters" }, color = Muted, style = MaterialTheme.typography.bodySmall)
                        if (s.hasSearched && !s.loading && s.error == null && s.results.isEmpty()) Text("No matching services. Change filters or browse providers.", color = Muted)
                        Text("Map key: ✓ confirmed service location · ? coordination/unconfirmed address · × reported unavailable · U availability unknown · ! limited availability", color = Muted, style = MaterialTheme.typography.bodySmall)
                        if (s.locationCleared || !s.hasSearched) TextButton(onClick = { showStart = true }) { Text("Set starting location") }
                    }
                }
            }
        }
    }
    if (routeDetails) ModalBottomSheet(onDismissRequest = { routeDetails = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).verticalScroll(rememberScrollState()).padding(20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MapRouteSummary(s, model) { routeDetails = false; showStart = true }
            TextButton(onClick = { routeDetails = false }) { Text("Close route details") }
        }
    }
    if (filters) SearchFiltersSheet(s, model, onDismiss = { filters = false }, onApply = {
        filters = false; selectedId = null; mapProvider = null
        val current = model.state.value
        if (current.originLatitude != null || current.origin.isNotBlank()) model.searchAddress()
    })
    if (showStart) key(permissionRevision) { ModalBottomSheet(onDismissRequest = { showStart = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp).imePadding().navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Choose starting location", style = MaterialTheme.typography.titleLarge)
            Text(s.locationError ?: if (!hasPermission()) "Allow location access in app settings, or enter an address." else if (!locationEnabled()) "Device location is off. Turn it on in location settings, or enter an address." else "Use device location or enter an address.", color = Muted)
            if (!hasPermission()) OutlinedButton(onClick = { openSettings(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true) }, modifier = Modifier.fillMaxWidth()) { Text("Open app location settings") }
            if (!locationEnabled()) OutlinedButton(onClick = { openSettings(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS) }, modifier = Modifier.fillMaxWidth()) { Text("Open device location settings") }
            FilledTonalButton(onClick = ::useDevice, modifier = Modifier.fillMaxWidth()) { Text("Use device location") }
            OutlinedTextField(if (s.origin in listOf("Your location", "Selected map area")) "" else s.origin, model::origin, modifier = Modifier.fillMaxWidth(), label = { Text("Starting address or postal code") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); showStart = false; if (routing) model.searchRouteOrigin() else model.searchAddress() }))
            Button(onClick = { focus.clearFocus(); showStart = false; if (routing) model.searchRouteOrigin() else model.searchAddress() }, enabled = s.origin.trim().length >= 3 && s.origin !in listOf("Your location", "Selected map area"), modifier = Modifier.fillMaxWidth()) { Text("Use this address") }
        }
    } }
}

@Composable private fun MapRouteSummary(s: UiState, model: AppViewModel, onChooseStart: () -> Unit) {
    val destination = s.routeDestination ?: return
    val context = LocalContext.current
    var stepsExpanded by remember(destination.id, s.routeMode) { mutableStateOf(false) }
    val route = s.route
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Route preview · depart now", style = MaterialTheme.typography.titleLarge)
        Text(destination.name, style = MaterialTheme.typography.titleMedium)
        destination.json.optString("status_at").takeIf { it.isNotBlank() }?.let { Text("Support checked for ${readableSearchTime(it)}. This route departs now; it does not plan arrival for that session.", color = Muted) }
        Text("${when (s.routeMode) { "drive" -> "Drive"; "pt" -> "Public transport"; else -> "Walk" }} · from ${s.origin.ifBlank { "starting location needed" }}", color = Muted)
        when {
            s.locationLoading -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Finding your location...", color = Muted, style = MaterialTheme.typography.bodyMedium) }
            s.routeLoading -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Finding your route…", color = Muted, style = MaterialTheme.typography.bodySmall) }
            s.originLatitude == null -> {
                Text(route?.optString("message")?.takeIf { it.isNotBlank() } ?: "Choose where your journey starts.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onChooseStart) { Text("Choose starting location") }
            }
            route?.optBoolean("available") == true -> {
                Text("%d min · %.1f km".format(kotlin.math.ceil(route.getDouble("duration_minutes")).toInt(), route.getDouble("distance_km")), style = MaterialTheme.typography.headlineSmall, color = Teal)
                if (s.routeMode == "pt") Text("Depart now · ${route.optInt("transfers")} transfers", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text("Estimate · ${route.optString("attribution")}", color = Muted, style = MaterialTheme.typography.bodySmall)
                route.optJSONArray("warnings")?.let { warnings ->
                    for (i in 0 until warnings.length()) Text(warnings.getString(i), color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                route.optJSONArray("agencies")?.objects()?.let { agencies ->
                    if (agencies.isNotEmpty()) Text(agencies.joinToString { it.getString("name") }, color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                val journey = route.optJSONArray("journey_steps")?.objects().orEmpty()
                if (s.routeMode == "pt" && journey.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        journey.filter { it.optString("mode") == "TRANSIT" }.forEach { step ->
                            Surface(shape = RoundedCornerShape(8.dp), color = Mint) { Text(step.getString("label"), Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = Teal, style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                    Text("Your journey", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    journey.forEach { step ->
                        val walking = step.optString("mode") == "WALK"
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(if (walking) Icons.Outlined.DirectionsWalk else Icons.Outlined.DirectionsTransit, null, Modifier.size(24.dp), tint = Teal)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row {
                                    Text(step.getString("label"), Modifier.weight(1f), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                    val minutes = kotlin.math.ceil(step.optDouble("duration_minutes")).toInt()
                                    if (minutes > 0) Text("$minutes min", color = Muted, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(step.getString("instruction"), style = MaterialTheme.typography.bodySmall)
                                if (walking) {
                                    Text("${step.optInt("distance_meters")} m", color = Muted, style = MaterialTheme.typography.bodySmall)
                                    var expanded by remember(step.toString()) { mutableStateOf(false) }
                                    TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) { Text(if (expanded) "Hide walking directions" else "Walking directions", style = MaterialTheme.typography.labelMedium) }
                                    if (expanded) step.optJSONArray("instructions")?.let { array -> for (i in 0 until array.length()) Text(array.getString(i), color = Muted, style = MaterialTheme.typography.bodySmall) }
                                } else {
                                    val stops = step.optInt("stop_count")
                                    Text("Towards ${step.optString("headsign")} / $stops stops", color = Muted, style = MaterialTheme.typography.bodySmall)
                                    if (step.optString("departure").isNotBlank()) Text("Departs ${step.optString("departure")} / Arrives ${step.optString("arrival")}", color = Teal, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        HorizontalDivider(color = Color(0xFFEDF0F0))
                    }
                } else {
                    val instructions = route.optJSONArray("instructions")
                    if (instructions != null && instructions.length() > 0) {
                        TextButton(onClick = { stepsExpanded = !stepsExpanded }, contentPadding = PaddingValues(0.dp)) { Text(if (stepsExpanded) "Hide route steps" else "View route steps"); Icon(if (stepsExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null) }
                        if (stepsExpanded) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            for (i in 0 until instructions.length()) Text("${i + 1}. ${instructions.getString(i)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            else -> {
                Text(route?.optString("message") ?: "Choose a travel option to calculate your route.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { model.searchRouteOrigin() }) { Text("Try route again") }
            }
        }
        if (!s.locationLoading && !s.routeLoading && s.originLatitude != null && s.originLongitude != null) {
            Button(onClick = {
                val travelMode = when (s.routeMode) { "drive" -> "driving"; "pt" -> "transit"; else -> "walking" }
                val uri = android.net.Uri.parse("https://www.google.com/maps/dir/").buildUpon()
                    .appendQueryParameter("api", "1")
                    .appendQueryParameter("origin", "${s.originLatitude},${s.originLongitude}")
                    .appendQueryParameter("destination", "${destination.latitude},${destination.longitude}")
                    .appendQueryParameter("travelmode", travelMode).build()
                runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri)) }
                    .onFailure { model.error("No map app or browser is available. Try again or use the destination address below.") }
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Open navigation app")
            }
        }
        IconFact(collectionLocationLabel(destination.json.optString("location_role")), Icons.Outlined.LocationOn)
        IconFact(destination.address, Icons.Outlined.NearMe)
        IconFact(accessMethodLabel(destination.json.optString("access_method", "contact_to_confirm")), Icons.Outlined.Groups)
        Availability(destination.availability)
        IconFact("Contact the provider before going. Food is not guaranteed.", Icons.Outlined.Info)
        ExpandableSection("Eligibility & how to apply", Icons.Outlined.Groups) {
            Text(destination.json.optString("eligibility").ifBlank { "Eligibility unconfirmed. Ask the provider." }, style = MaterialTheme.typography.bodyMedium)
        }
        AvailabilityEvidence(destination)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            destination.json.optString("phone").takeUnless { it.isBlank() || it == "null" }?.let { phone ->
                TextButton(onClick = { runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$phone"))) }.onFailure { model.error("No phone app is available. Call $phone using another phone.") } }) { Icon(Icons.Outlined.Call, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Call") }
            }
            destination.json.optString("website").takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { site ->
                TextButton(onClick = { runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(site))) }.onFailure { model.error("No browser is available. Provider website: $site") } }) { Icon(Icons.Outlined.Language, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Website") }
            }
        }
        TextButton(onClick = { model.detail(destination) }, contentPadding = PaddingValues(0.dp)) { Text("Support details"); Icon(Icons.Outlined.ChevronRight, null) }
    }
}

@Composable fun FoodMap(services: List<Service>, route: JSONObject?, modifier: Modifier,
    origin: LatLng? = null, focusPoint: LatLng? = null, overlay: Boolean = false, topInset: Int = 0, bottomInset: Int = 0, onCenter: ((LatLng) -> Unit)? = null, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val select by rememberUpdatedState(onSelect)
    val center by rememberUpdatedState(onCenter)
    if (BuildConfig.GOOGLE_MAPS_API_KEY.isBlank()) {
        Box(modifier.background(Color(0xFFEAF1EF)), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Map, null, Modifier.size(40.dp), tint = Teal)
                Text("Google Maps setup needed", fontWeight = FontWeight.Bold)
                Text("Search and support details are available. The map will appear once its key is connected.", color = Muted)
            }
        }
        return
    }
    val view = remember(context) { MapView(context).apply { onCreate(null) } }
    val controller = remember(view) { MapController(view, overlay, { select(it) }, { center?.invoke(it) }) }
    DisposableEffect(view, lifecycle) {
        // A view created while the Activity is already resumed needs its initial lifecycle events.
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) view.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.onResume()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); controller.release(); view.onPause(); view.onStop(); view.onDestroy() }
    }
    AndroidView(factory = { view }, modifier = modifier, update = { controller.update(services, route, origin, focusPoint, topInset, bottomInset) })
}

private class MapController(private val view: MapView, private val overlay: Boolean,
    private val select: (String) -> Unit, private val center: (LatLng) -> Unit) {
    private var map: GoogleMap? = null
    private var services: List<Service> = emptyList()
    private var route: JSONObject? = null
    private var origin: LatLng? = null
    private var focusPoint: LatLng? = null
    private var topInset = 0
    private var bottomInset = 0
    private var signature: String? = null
    private var released = false
    private var gesture = false
    init {
        view.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) render()
        }
        view.getMapAsync { googleMap ->
            if (!released) {
                map = googleMap
                googleMap.setMapStyle(MapStyleOptions(view.context.assets.open("foodbridge-google-map.json").bufferedReader().use { it.readText() }))
                googleMap.uiSettings.apply { isMapToolbarEnabled = false; isZoomControlsEnabled = false; isMyLocationButtonEnabled = false }
                googleMap.setOnMarkerClickListener { marker -> (marker.tag as? String)?.let(select); true }
                googleMap.setOnCameraMoveStartedListener { reason -> gesture = reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE }
                googleMap.setOnCameraIdleListener { if (gesture) { gesture = false; center(googleMap.cameraPosition.target) } }
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(1.3521, 103.8198), 10.4f))
                render()
            }
        }
    }
    fun update(services: List<Service>, route: JSONObject?, origin: LatLng?, focusPoint: LatLng?, topInset: Int, bottomInset: Int) {
        this.services = services; this.route = route; this.origin = origin; this.focusPoint = focusPoint
        this.topInset = topInset; this.bottomInset = bottomInset
        render()
    }
    private fun render() {
        val googleMap = map ?: return
        val next = services.joinToString { "${it.id}:${it.latitude}:${it.longitude}:${it.json.optString("location_role")}:${it.availability}" } + route.toString() + origin.toString() + focusPoint.toString() + ":$topInset:$bottomInset:${view.width}:${view.height}"
        if (signature == next) return
        signature = next
        googleMap.clear()
        services.forEach { service ->
            googleMap.addMarker(MarkerOptions().position(LatLng(service.latitude, service.longitude)).title("${service.name} · ${collectionLocationLabel(service.json.optString("location_role"))}")
                .snippet(when (service.availability) { "UNAVAILABLE" -> "Reported unavailable"; "UNKNOWN" -> "Availability unknown"; "LIMITED" -> "Limited availability"; else -> "Reported available · contact before travel" })
                .icon(markerIcon(service)))?.tag = service.id
        }
        origin?.let {
            googleMap.addCircle(CircleOptions().center(it).radius(32.0).fillColor(android.graphics.Color.argb(45, 66, 133, 244)).strokeWidth(0f).zIndex(1f))
            googleMap.addCircle(CircleOptions().center(it).radius(9.0).fillColor(android.graphics.Color.rgb(66, 133, 244)).strokeColor(android.graphics.Color.WHITE).strokeWidth(3f).zIndex(2f))
        }
        val geometry = route?.optJSONArray("geometry")
        val routePoints = if (geometry == null) emptyList() else (0 until geometry.length()).map { index ->
            geometry.getJSONArray(index).let { LatLng(it.getDouble(0), it.getDouble(1)) }
        }
        if (routePoints.size > 1) googleMap.addPolyline(PolylineOptions().addAll(routePoints).color(android.graphics.Color.rgb(0, 83, 91)).width(6f * view.resources.displayMetrics.density))
        val points = services.map { LatLng(it.latitude, it.longitude) } + routePoints + listOfNotNull(origin)
        val density = view.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        // SDK padding also keeps Google's logo/attribution clear of the floating cards.
        val mapTop = if (overlay) topInset + dp(8) else dp(20)
        val mapBottom = if (overlay) bottomInset + dp(8) else dp(20)
        googleMap.setPadding(dp(16), mapTop, dp(16), mapBottom)
        if (points.isEmpty()) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(1.3521, 103.8198), 11.0f))
            return
        }
        view.post {
            if (released || signature != next) return@post
            val focus = focusPoint
            if (focus != null || points.distinct().size == 1) {
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(focus ?: points.first(), 15f))
            } else if (view.width > dp(100) && view.height - mapTop - mapBottom > dp(64)) {
                val bounds = LatLngBounds.builder().apply { points.forEach { include(it) } }.build()
                // Use the rendered pixel scale so display density and overlay cards cannot crop the journey.
                googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(
                    CameraPosition.builder(googleMap.cameraPosition).bearing(0f).tilt(0f).build()))
                val projected = points.map { googleMap.projection.toScreenLocation(it) }
                val spanX = (projected.maxOf { it.x } - projected.minOf { it.x }).coerceAtLeast(1)
                val spanY = (projected.maxOf { it.y } - projected.minOf { it.y }).coerceAtLeast(1)
                val scale = minOf((view.width - dp(96)).toDouble() / spanX,
                    (view.height - mapTop - mapBottom - dp(64)).toDouble() / spanY)
                val zoom = (googleMap.cameraPosition.zoom + kotlin.math.log2(scale)).toFloat()
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(bounds.center, zoom))
            }
        }
    }
    private fun markerIcon(service: Service): BitmapDescriptor {
        val density = view.resources.displayMetrics.density
        val size = (52 * density).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val confirmed = service.json.optString("location_role") in listOf("collection_site", "confirmed_collection_site")
        val center = size / 2f
        paint.color = android.graphics.Color.rgb(0, 83, 91)
        if (confirmed) canvas.drawCircle(center, center, size * .38f, paint)
        else {
            val path = android.graphics.Path().apply { moveTo(center, size * .05f); lineTo(size * .95f, center); lineTo(center, size * .95f); lineTo(size * .05f, center); close() }
            canvas.drawPath(path, paint)
        }
        paint.color = android.graphics.Color.WHITE
        paint.textAlign = android.graphics.Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        paint.textSize = size * .45f
        canvas.drawText(if (confirmed) "✓" else "?", center, center - (paint.ascent() + paint.descent()) / 2, paint)
        val status = when (service.availability) { "UNAVAILABLE" -> "×"; "UNKNOWN" -> "U"; "LIMITED" -> "!"; else -> null }
        if (status != null) {
            paint.color = android.graphics.Color.rgb(255, 237, 214)
            canvas.drawCircle(size * .81f, size * .20f, size * .18f, paint)
            paint.color = android.graphics.Color.rgb(48, 43, 38)
            paint.textSize = size * .26f
            canvas.drawText(status, size * .81f, size * .20f - (paint.ascent() + paint.descent()) / 2, paint)
        }
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
    fun release() { released = true; signature = null; map = null }
}
