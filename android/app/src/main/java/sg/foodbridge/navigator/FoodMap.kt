package sg.foodbridge.navigator

import android.Manifest
import android.content.pm.PackageManager
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

@Composable fun MapScreen(s: UiState, model: AppViewModel) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var filters by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var mapCenter by remember { mutableStateOf(LatLng(1.3521, 103.8198)) }
    var moved by remember { mutableStateOf(false) }
    var showSaved by remember { mutableStateOf(false) }
    var savedFocus by remember { mutableStateOf<LatLng?>(null) }
    val savedServices = s.services.filter { it.id in s.saved }
    val routing = s.routeDestination != null
    val selected = (s.results + s.services).find { it.id == selectedId }
    val savedSelection = selected != null && selected.id !in s.results.map { it.id }
    val mapServices = if (routing) listOfNotNull(s.routeDestination) else if (savedSelection) s.results + listOfNotNull(selected) else s.results
    fun search() { focus.clearFocus(); showSaved = false; selectedId = null; savedFocus = null; moved = false; model.search() }
    fun locate() { (context as? MainActivity)?.requestCurrentLocation() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) locate() else model.error("Location permission was declined. Search by address instead.")
    }
    Box(Modifier.fillMaxSize().imePadding()) {
        FoodMap(mapServices, if (routing) s.route else null, Modifier.fillMaxSize(), focusPoint = if (routing) null else savedFocus, origin = s.originLatitude?.let { lat -> s.originLongitude?.let { LatLng(lat, it) } },
            overlay = true, onCenter = { point -> mapCenter = point; moved = true }) { selectedId = it; savedFocus = null }
        Column(Modifier.align(Alignment.TopCenter).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(28.dp), color = Color.White, shadowElevation = 4.dp) {
                TextField(s.origin, { savedFocus = null; selectedId = null; model.origin(it) }, modifier = Modifier.fillMaxWidth().onFocusChanged { showSaved = it.isFocused }, singleLine = true,
                    placeholder = { Text(if (routing) "Starting address or postal code" else "Address or organisation", maxLines = 1) },
                    leadingIcon = { IconButton(onClick = { search() }, enabled = !s.loading) { Icon(Icons.Outlined.Search, "Search nearby support", tint = Teal) } },
                    trailingIcon = { if (s.origin.isNotEmpty()) IconButton(onClick = { model.origin(""); selectedId = null; savedFocus = null }) { Icon(Icons.Outlined.Close, "Clear search") } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
            }
            if (showSaved && !routing) Surface(shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 4.dp) {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    item { Box(Modifier.padding(horizontal = 16.dp)) { SearchSuggestions(s, model) { focus.clearFocus(); showSaved = false } } }
                    item {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.BookmarkBorder, null, Modifier.size(18.dp), tint = Teal)
                            Spacer(Modifier.width(8.dp)); Text("Saved services", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            IconButton(onClick = { focus.clearFocus(); showSaved = false }) { Icon(Icons.Outlined.Close, "Close saved services") }
                        }
                    }
                    items(savedServices, key = { it.id }) { service ->
                        Row(Modifier.fillMaxWidth().clickable {
                            focus.clearFocus(); showSaved = false; selectedId = service.id
                            savedFocus = LatLng(service.latitude, service.longitude)
                        }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.LocationOn, null, Modifier.size(20.dp), tint = Teal)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(service.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                Text(if (service.demo) "${service.type} · Demo listing" else service.type, color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (savedServices.isEmpty()) item { Text("Save a service from its details to find it here.", Modifier.padding(16.dp), color = Muted, style = MaterialTheme.typography.bodyMedium) }
                    if (s.saved.any { id -> s.services.none { it.id == id } }) item { Text("Some saved services are no longer listed.", Modifier.padding(16.dp), color = Muted, style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (!routing) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { focus.clearFocus(); filters = true }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White), elevation = ButtonDefaults.buttonElevation(2.dp)) {
                    Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Filters")
                }
                if (moved) FilledTonalButton(onClick = { focus.clearFocus(); selectedId = null; savedFocus = null; moved = false; model.searchMapArea(mapCenter.latitude, mapCenter.longitude) }, enabled = !s.loading,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White), elevation = ButtonDefaults.buttonElevation(2.dp)) { Text("Search this area") }
            }
        }
        if (!showSaved || routing) Column(Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp).padding(bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SmallFloatingActionButton(onClick = {
                    focus.clearFocus()
                    if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate()
                    else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                }, containerColor = Color.White, contentColor = Teal) { Icon(Icons.Outlined.MyLocation, "Find support near my location") }
            }
            Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(20.dp), shadowElevation = 3.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (routing) {
                        MapRouteSummary(s, model)
                    } else if (selected != null) {
                        if (savedSelection) Text("Saved service · suitability not checked for this search", color = Muted, style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(selected.name, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = { selectedId = null; savedFocus = null }) { Icon(Icons.Outlined.Close, "Close service preview") }
                        }
                        Text(selected.type, color = Muted, style = MaterialTheme.typography.bodyMedium)
                        if (selected.demo) Text("Demo listing · Fictional. Do not travel here.", color = Teal, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { model.detail(selected) }, modifier = Modifier.fillMaxWidth()) { Text("View support details") }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(when { s.loading -> "Finding support…"; s.hasSearched -> "${s.results.size} matching services"; else -> "Explore food support" }, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            if (s.hasSearched && s.results.isNotEmpty()) TextButton(onClick = { model.tab("Find") }) { Text("List") }
                            else TextButton(onClick = model::browseProviders) { Text("Providers") }
                        }
                        Text(if (s.hasSearched && s.results.isEmpty()) "No confirmed matches for these filters. Check dietary or accessibility requirements, or browse Providers."
                            else if (s.hasSearched) "Tap a marker for details." else "Search an address or move the map to find help.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                        Text(SearchSummary(s), color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (s.results.any { it.demo }) Text("Demo pins are fictional. Do not travel to them.", color = Teal, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (filters) SearchFiltersSheet(s, model) { filters = false; selectedId = null; if (s.origin.isNotBlank()) search() }
}

@Composable private fun MapRouteSummary(s: UiState, model: AppViewModel) {
    val destination = s.routeDestination ?: return
    val uriHandler = LocalUriHandler.current
    var stepsExpanded by remember(destination.id, s.routeMode) { mutableStateOf(false) }
    val route = s.route
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Directions to", color = Muted, style = MaterialTheme.typography.labelMedium)
                Text(destination.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = model::closeRoute) { Icon(Icons.Outlined.Close, "Close directions") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("walk" to "Walk", "pt" to "Public transport", "drive" to "Drive").forEach { (value, label) -> Filter(label, s.routeMode == value) { model.route(value) } }
        }
        when {
            s.routeLoading -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Finding your route…", color = Muted, style = MaterialTheme.typography.bodySmall) }
            s.originLatitude == null -> Text(route?.optString("message")?.takeIf { it.isNotBlank() } ?: "Enter a starting address above or use the location button.", color = Muted, style = MaterialTheme.typography.bodyMedium)
            route?.optBoolean("available") == true -> {
                Text("%d min · %.1f km".format(kotlin.math.ceil(route.getDouble("duration_minutes")).toInt(), route.getDouble("distance_km")), style = MaterialTheme.typography.titleLarge, color = Teal)
                if (s.routeMode == "pt") Text("Depart now · ${route.optInt("transfers")} transfers", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text("Estimate · ${route.optString("attribution")}", color = Muted, style = MaterialTheme.typography.bodySmall)
                route.optJSONArray("warnings")?.let { warnings ->
                    for (i in 0 until warnings.length()) Text(warnings.getString(i), color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                route.optJSONArray("agencies")?.objects()?.forEach { agency ->
                    val url = agency.optString("url")
                    if (url.startsWith("https://") || url.startsWith("http://")) TextButton(onClick = { uriHandler.openUri(url) }, contentPadding = PaddingValues(0.dp)) { Text(agency.getString("name"), style = MaterialTheme.typography.bodySmall) }
                    else Text(agency.getString("name"), color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                val instructions = route.optJSONArray("instructions")
                if (instructions != null && instructions.length() > 0) {
                    TextButton(onClick = { stepsExpanded = !stepsExpanded }, contentPadding = PaddingValues(0.dp)) { Text(if (stepsExpanded) "Hide route steps" else "View route steps"); Icon(if (stepsExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null) }
                    if (stepsExpanded) Column(Modifier.fillMaxWidth().heightIn(max = 160.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (i in 0 until instructions.length()) Text("${i + 1}. ${instructions.getString(i)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            else -> {
                Text(route?.optString("message") ?: "Choose a travel option to calculate your route.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { model.searchRouteOrigin() }) { Text("Try route again") }
            }
        }
        TextButton(onClick = { model.detail(destination) }) { Text("View support details") }
    }
}

@Composable fun FoodMap(services: List<Service>, route: JSONObject?, modifier: Modifier,
    origin: LatLng? = null, focusPoint: LatLng? = null, overlay: Boolean = false, onCenter: ((LatLng) -> Unit)? = null, onSelect: (String) -> Unit) {
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
    AndroidView(factory = { view }, modifier = modifier, update = { controller.update(services, route, origin, focusPoint) })
}

private class MapController(private val view: MapView, private val overlay: Boolean,
    private val select: (String) -> Unit, private val center: (LatLng) -> Unit) {
    private var map: GoogleMap? = null
    private var services: List<Service> = emptyList()
    private var route: JSONObject? = null
    private var origin: LatLng? = null
    private var focusPoint: LatLng? = null
    private var signature: String? = null
    private var released = false
    private var gesture = false
    init {
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
    fun update(services: List<Service>, route: JSONObject?, origin: LatLng?, focusPoint: LatLng?) {
        this.services = services; this.route = route; this.origin = origin; this.focusPoint = focusPoint
        render()
    }
    private fun render() {
        val googleMap = map ?: return
        val next = services.joinToString { "${it.id}:${it.latitude}:${it.longitude}" } + route.toString() + origin.toString() + focusPoint.toString()
        if (signature == next) return
        signature = next
        googleMap.clear()
        services.forEach { service ->
            googleMap.addMarker(MarkerOptions().position(LatLng(service.latitude, service.longitude)).title(service.name)
                .icon(BitmapDescriptorFactory.defaultMarker(183f)))?.tag = service.id
        }
        origin?.let { googleMap.addMarker(MarkerOptions().position(it).title("Starting point").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))) }
        val geometry = route?.optJSONArray("geometry")
        val routePoints = if (geometry == null) emptyList() else (0 until geometry.length()).map { index ->
            geometry.getJSONArray(index).let { LatLng(it.getDouble(0), it.getDouble(1)) }
        }
        if (routePoints.size > 1) googleMap.addPolyline(PolylineOptions().addAll(routePoints).color(android.graphics.Color.rgb(0, 83, 91)).width(6f * view.resources.displayMetrics.density))
        val points = services.map { LatLng(it.latitude, it.longitude) } + routePoints + listOfNotNull(origin)
        val density = view.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        // SDK padding also keeps Google's logo/attribution clear of the floating cards.
        googleMap.setPadding(dp(24), dp(if (overlay) 100 else 20), dp(24), dp(if (overlay) (if (routePoints.isNotEmpty()) 340 else 220) else 20))
        if (points.isEmpty()) return
        view.post {
            if (released || signature != next) return@post
            val focus = focusPoint
            if (focus != null || points.distinct().size == 1) {
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(focus ?: points.first(), 15f))
            } else if (view.width > dp(100) && view.height > dp(460)) {
                val bounds = LatLngBounds.builder().apply { points.forEach { include(it) } }.build()
                googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, dp(24)))
            }
        }
    }
    fun release() { released = true; signature = null; map = null }
}
