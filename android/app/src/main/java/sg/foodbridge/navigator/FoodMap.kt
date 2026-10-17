package sg.foodbridge.navigator

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumeGeneration by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumeGeneration++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var filters by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var mapProvider by remember { mutableStateOf<JSONObject?>(null) }
    var mapCenter by remember { mutableStateOf(LatLng(1.3521, 103.8198)) }
    var moved by remember { mutableStateOf(false) }
    var showSaved by remember { mutableStateOf(false) }
    var showStart by remember { mutableStateOf(false) }
    var savedFocus by remember { mutableStateOf<LatLng?>(null) }
    var topHeight by remember { mutableIntStateOf(0) }
    var bottomHeight by remember { mutableIntStateOf(0) }
    val routing = s.routeDestination != null
    val savedServices = s.services.filter { it.id in s.saved }
    val selected = (s.results + s.services).find { it.id == selectedId }
    val savedSelection = selected != null && s.results.none { it.id == selected.id }
    val mapServices = when {
        routing -> listOfNotNull(s.routeDestination)
        savedSelection -> s.results + listOfNotNull(selected)
        s.hasSearched -> s.results
        else -> s.services
    }
    fun hasPermission() = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun locationEnabled() = context.getSystemService(LocationManager::class.java).isLocationEnabled
    fun locate() { focus.clearFocus(); showSaved = false; showStart = false; savedFocus = null; moved = false; (context as? MainActivity)?.requestCurrentLocation() }
    fun preview(match: OrganisationMatch) {
        focus.clearFocus(); showSaved = false; showStart = false; moved = false
        val service = match.service ?: s.services.firstOrNull { it.json.optString("provider_id") == match.id }
            ?: s.services.firstOrNull { it.organisationName.equals(match.name, ignoreCase = true) || it.name.equals(match.name, ignoreCase = true) }
        selectedId = service?.id
        mapProvider = if (service == null) match.provider else null
        savedFocus = service?.let { LatLng(it.latitude, it.longitude) }
    }
    fun search() {
        focus.clearFocus(); showSaved = false; showStart = false; selectedId = null; mapProvider = null; savedFocus = null; moved = false
        val matches = if (!routing) organisationMatches(s) else emptyList()
        val exact = matches.firstOrNull { it.name.equals(s.origin.trim(), ignoreCase = true) }
        if (exact != null) preview(exact)
        else if (matches.isNotEmpty() && matches.map { it.name.lowercase() }.distinct().size == 1) preview(matches.first())
        else model.search()
    }
    fun submitStart() {
        focus.clearFocus(); showSaved = false; showStart = false; savedFocus = null; moved = false
        if (routing) model.searchRouteOrigin() else model.searchAddress()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) locate()
        else { model.locationFailed("Location access is off. Enter a starting address to continue."); showStart = true }
    }
    fun useDevice() {
        if (hasPermission()) locate()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    // Only request once on entry/new destination. Editing an address must not trigger GPS again.
    LaunchedEffect(s.routeDestination?.id, resumeGeneration) {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@LaunchedEffect
        if (s.origin.isBlank() && s.originLatitude == null && !s.locationLoading) {
            if (hasPermission() && locationEnabled()) locate()
            else showStart = true
        }
    }
    LaunchedEffect(s.locationError) { if (s.locationError != null) showStart = true }
    Box(Modifier.fillMaxSize().imePadding()) {
        FoodMap(mapServices, if (routing) s.route else null, Modifier.fillMaxSize(),
            focusPoint = savedFocus, origin = s.originLatitude?.let { lat -> s.originLongitude?.let { LatLng(lat, it) } },
            overlay = true, topInset = topHeight, bottomInset = bottomHeight,
            onCenter = { mapCenter = it; moved = true }) { selectedId = it; mapProvider = null; savedFocus = null }
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged { topHeight = it.height }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (routing) {
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 3.dp) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = model::closeRoute) { Icon(Icons.Outlined.ArrowBack, "Back to map") }
                            Column(Modifier.weight(1f)) {
                                Row(Modifier.fillMaxWidth().clickable { showStart = true }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.RadioButtonChecked, null, Modifier.size(18.dp), tint = Color(0xFF4285F4))
                                    Spacer(Modifier.width(12.dp))
                                    Text(if (s.locationLoading) "Finding your location..." else s.origin.ifBlank { "Choose starting location" }, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                    Icon(Icons.Outlined.Edit, "Change starting location", Modifier.size(18.dp), tint = Muted)
                                }
                                HorizontalDivider(color = Color(0xFFEDF0F0))
                                Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.LocationOn, null, Modifier.size(18.dp), tint = Teal)
                                    Spacer(Modifier.width(12.dp)); Text(s.routeDestination!!.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(Triple("drive", "Drive", Icons.Outlined.DirectionsCar), Triple("pt", "Public transport", Icons.Outlined.DirectionsTransit), Triple("walk", "Walk", Icons.Outlined.DirectionsWalk)).forEach { (value, label, icon) ->
                                Surface(onClick = { model.route(value) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), color = if (s.routeMode == value) Mint else Color.White) {
                                    Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(icon, null, Modifier.size(22.dp), tint = if (s.routeMode == value) Teal else Muted)
                                        Text(label, style = MaterialTheme.typography.labelSmall, color = if (s.routeMode == value) Teal else Muted, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Surface(shape = RoundedCornerShape(32.dp), color = Color.White, shadowElevation = 3.dp) {
                    TextField(if (s.origin in listOf("Your location", "Selected map area")) "" else s.origin,
                        { savedFocus = null; selectedId = null; mapProvider = null; model.origin(it) }, modifier = Modifier.fillMaxWidth().onFocusChanged { showSaved = it.isFocused }, singleLine = true,
                        placeholder = { Text("Search food support", style = MaterialTheme.typography.bodyLarge) },
                        leadingIcon = { IconButton(onClick = ::search) { Icon(Icons.Outlined.Search, "Search food support", tint = Teal) } },
                        trailingIcon = { if (showSaved || s.origin.isNotBlank() && s.origin != "Your location") IconButton(onClick = { focus.clearFocus(); showSaved = false; model.origin(""); selectedId = null; mapProvider = null; savedFocus = null }) { Icon(Icons.Outlined.Close, "Clear search") } },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }),
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                }
                if (!showSaved) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { focus.clearFocus(); filters = true }, label = { Text("Filters") }, leadingIcon = { Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color.White), border = null)
                    AssistChip(onClick = model::browseProviders, label = { Text("Providers") }, leadingIcon = { Icon(Icons.Outlined.Groups, null, Modifier.size(18.dp)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color.White), border = null)
                    AssistChip(onClick = { if (s.hasSearched) model.tab("Find") else model.browseAllSupport() }, label = { Text("View list") },
                        leadingIcon = { Icon(Icons.Outlined.List, null, Modifier.size(18.dp)) }, colors = AssistChipDefaults.assistChipColors(containerColor = Color.White), border = null)
                }
            }
            if (showSaved && !routing) Surface(shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 3.dp) {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item { TextButton(onClick = { useDevice() }) { Icon(Icons.Outlined.MyLocation, null, Modifier.size(18.dp)); Spacer(Modifier.width(12.dp)); Text("Your location") } }
                    item { SearchSuggestions(s, model, onMatch = ::preview) { focus.clearFocus(); showSaved = false } }
                    item { Text("Saved places", style = MaterialTheme.typography.labelMedium, color = Muted, modifier = Modifier.padding(top = 8.dp)) }
                    items(savedServices, key = { it.id }) { service ->
                        Row(Modifier.fillMaxWidth().clickable { focus.clearFocus(); showSaved = false; mapProvider = null; selectedId = service.id; savedFocus = LatLng(service.latitude, service.longitude) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.BookmarkBorder, null, Modifier.size(22.dp), tint = Teal); Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) { Text(service.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2); Text(service.type, color = Muted, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    if (savedServices.isEmpty()) item { Text("Your saved services will appear here.", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 12.dp)) }
                }
            }
        }
        if (moved && !routing && !showSaved) FilledTonalButton(onClick = { selectedId = null; savedFocus = null; moved = false; model.searchMapArea(mapCenter.latitude, mapCenter.longitude) }, modifier = Modifier.align(Alignment.TopCenter).padding(top = with(density) { topHeight.toDp() }), enabled = !s.loading,
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White), elevation = ButtonDefaults.buttonElevation(2.dp)) { Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Search this area") }
        if (!showSaved || routing) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { bottomHeight = it.height }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(end = 16.dp), horizontalArrangement = Arrangement.End) {
                SmallFloatingActionButton(onClick = { useDevice() }, shape = CircleShape, containerColor = Color.White, contentColor = Teal) {
                    if (s.locationLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.MyLocation, "Use current device location")
                }
            }
            Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), shadowElevation = 4.dp) {
                Column(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.align(Alignment.CenterHorizontally).width(32.dp).height(4.dp).background(Color(0xFFD9DFDF), CircleShape))
                    if (routing) key(s.routeDestination?.id, s.routeMode, s.origin) { MapRouteSummary(s, model, onChooseStart = { showStart = true }) }
                    else if (selected != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(selected.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
                            IconButton(onClick = { selectedId = null; savedFocus = null }) { Icon(Icons.Outlined.Close, "Close place") }
                        }
                        Text(selected.type, color = Muted, style = MaterialTheme.typography.bodyMedium)
                        Text(collectionLocationLabel(selected.json.optString("location_role")), color = Teal, style = MaterialTheme.typography.bodySmall)
                        Text(selected.address, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (selected.json.optString("location_role") == "coordination_site") Text("${accessMethodLabel(selected.json.optString("access_method", "contact_to_confirm"))} · This pin is not a walk-in collection point.", color = Muted, style = MaterialTheme.typography.bodySmall)
                        if (savedSelection) Text("Suitability not checked for this search", color = Muted, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { model.detail(selected) }, modifier = Modifier.fillMaxWidth()) { Text("View support details") }
                        Button(onClick = { savedFocus = null; model.detail(selected); model.route(s.routeMode) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Directions, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(directionsButtonLabel(selected.json.optString("location_role")).replace("Get directions", "Navigate"))
                        }
                    } else if (mapProvider != null) {
                        val provider = mapProvider!!
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(provider.getString("name"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
                            IconButton(onClick = { mapProvider = null }) { Icon(Icons.Outlined.Close, "Close provider") }
                        }
                        Text(accessMethodLabel(provider.optString("access_method", if (provider.optString("verification_status") == "provider_reviewed") "contact_to_confirm" else "unverified")), color = Teal, style = MaterialTheme.typography.bodyMedium)
                        Text("No confirmed collection-site address is listed. Contact the provider before travelling.", color = Muted, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { model.providerDetail(provider) }, modifier = Modifier.fillMaxWidth()) { Text("View support details") }
                        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Directions, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Navigate here") }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(when { s.locationLoading -> "Finding your location..."; s.loading -> "Finding nearby support..."; s.hasSearched -> "${s.results.size} nearby services"; else -> "Food support near you" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(if (s.hasSearched && s.results.isEmpty()) "No matches. Adjust filters or browse providers." else if (s.originLatitude == null) "Choose a starting location to find nearby help." else "Tap a place on the map for details.", color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { if (s.originLatitude == null) showStart = true else model.tab("Find") }) { Text(if (s.originLatitude == null) "Set location" else "View list") }
                        }
                    }
                }
            }
        }
    }
    if (filters) SearchFiltersSheet(s, model) { filters = false; selectedId = null; if (s.originLatitude != null || s.origin.isNotBlank()) search() }
    if (showStart) ModalBottomSheet(onDismissRequest = { showStart = false }, containerColor = Color.White, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Choose starting location", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(s.locationError ?: if (!hasPermission()) "Allow location access or enter an address." else if (!locationEnabled()) "Device location is off. Turn it on or enter an address." else "Use your device location or choose another starting point.", color = Muted, style = MaterialTheme.typography.bodyMedium)
            FilledTonalButton(onClick = { useDevice() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.MyLocation, null, Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Text("Use your location") }
            OutlinedTextField(if (s.origin in listOf("Your location", "Selected map area")) "" else s.origin, model::origin, modifier = Modifier.fillMaxWidth(), label = { Text("Address or postal code") }, placeholder = { Text("e.g. 560123") }, singleLine = true,
                shape = RoundedCornerShape(12.dp), leadingIcon = { Icon(Icons.Outlined.LocationOn, null) }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { submitStart() }))
            Button(onClick = { submitStart() }, enabled = s.origin.trim().length >= 3 && s.origin !in listOf("Your location", "Selected map area"), modifier = Modifier.fillMaxWidth()) { Text("Use this starting location") }
        }
    }
}

@Composable private fun MapRouteSummary(s: UiState, model: AppViewModel, onChooseStart: () -> Unit) {
    val destination = s.routeDestination ?: return
    val context = LocalContext.current
    var stepsExpanded by remember(destination.id, s.routeMode) { mutableStateOf(s.routeMode == "pt") }
    val route = s.route
    Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(destination.name, style = MaterialTheme.typography.titleLarge)
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
        if (!s.locationLoading && !s.routeLoading && s.originLatitude != null && s.originLongitude != null && route?.optBoolean("available") != true) {
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
                Icon(Icons.Outlined.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Open in Google Maps")
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
        val next = services.joinToString { "${it.id}:${it.latitude}:${it.longitude}" } + route.toString() + origin.toString() + focusPoint.toString() + ":$topInset:$bottomInset:${view.width}:${view.height}"
        if (signature == next) return
        signature = next
        googleMap.clear()
        services.forEach { service ->
            googleMap.addMarker(MarkerOptions().position(LatLng(service.latitude, service.longitude)).title(service.name)
                .icon(BitmapDescriptorFactory.defaultMarker(183f)))?.tag = service.id
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
    fun release() { released = true; signature = null; map = null }
}
