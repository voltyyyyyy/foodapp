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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.*
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.android.gestures.MoveGestureDetector
import org.json.JSONObject
import kotlin.math.*

@Composable fun MapScreen(s: UiState, model: AppViewModel) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var filters by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var mapCenter by remember { mutableStateOf(Point.fromLngLat(103.8198, 1.3521)) }
    var moved by remember { mutableStateOf(false) }
    var showSaved by remember { mutableStateOf(false) }
    var savedFocus by remember { mutableStateOf<Point?>(null) }
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
        FoodMap(mapServices, if (routing) s.route else null, Modifier.fillMaxSize(), focusPoint = if (routing) null else savedFocus, origin = s.originLatitude?.let { lat -> s.originLongitude?.let { Point.fromLngLat(it, lat) } },
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
                            savedFocus = Point.fromLngLat(service.longitude, service.latitude)
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
                if (moved) FilledTonalButton(onClick = { focus.clearFocus(); selectedId = null; savedFocus = null; moved = false; model.searchMapArea(mapCenter.latitude(), mapCenter.longitude()) }, enabled = !s.loading,
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
    origin: Point? = null, focusPoint: Point? = null, overlay: Boolean = false, onCenter: ((Point) -> Unit)? = null, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val select by rememberUpdatedState(onSelect)
    val center by rememberUpdatedState(onCenter)
    var status by remember { mutableStateOf<String?>("Loading map…") }
    var retry by remember { mutableIntStateOf(0) }
    if (!BuildConfig.MAPBOX_ACCESS_TOKEN.startsWith("pk.")) {
        Box(modifier.background(Color(0xFFEAF1EF)), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Map, null, Modifier.size(40.dp), tint = Teal)
                Text("Map is not connected yet", fontWeight = FontWeight.Bold)
                Text("Search is available. The map will appear once setup is complete.", color = Muted)
            }
        }
        return
    }
    Box(modifier) {
        key(retry) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = {
                MapboxOptions.accessToken = BuildConfig.MAPBOX_ACCESS_TOKEN
                MapView(context).apply {
                    val density = resources.displayMetrics.density
                    attribution.updateSettings { marginBottom = 8f * density }
                    scalebar.updateSettings { enabled = false }
                    compass.updateSettings { marginTop = (if (overlay) 145f else 16f) * density }
                    val controller = MapController(this, overlay) { select(it) }
                    tag = controller
                    mapboxMap.subscribeMapLoadingError { post { status = "Map unavailable. Check your connection and retry." } }
                    mapboxMap.subscribeMapIdle { post { status = null } }
                    gestures.addOnMoveListener(object : OnMoveListener {
                        override fun onMoveBegin(detector: MoveGestureDetector) {}
                        override fun onMove(detector: MoveGestureDetector): Boolean = false
                        override fun onMoveEnd(detector: MoveGestureDetector) { center?.invoke(mapboxMap.cameraState.center) }
                    })
                    val style = BuildConfig.MAPBOX_STYLE_URI.ifBlank { context.assets.open("foodbridge-map.json").bufferedReader().use { it.readText().trimStart('\uFEFF') } }
                    mapboxMap.loadStyle(style) { controller.ready = true; controller.render() }
                    mapboxMap.setCamera(CameraOptions.Builder().center(Point.fromLngLat(103.8198, 1.3521)).zoom(10.4).pitch(0.0).build())
                }
            }, update = { view -> (view.tag as MapController).update(services, route, origin, focusPoint) }, onRelease = { view -> (view.tag as MapController).release(); view.onDestroy() })
        }
        if (status != null) Surface(Modifier.align(Alignment.Center).padding(24.dp), color = Color.White, shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(status!!, style = MaterialTheme.typography.bodyMedium)
                if (status != "Loading map…") TextButton(onClick = { status = "Loading map…"; retry++ }) { Text("Retry map") }
            }
        }
    }
}

private class MapController(private val view: MapView, private val overlay: Boolean, private val select: (String) -> Unit) {
    var ready = false
    private var services: List<Service> = emptyList()
    private var route: JSONObject? = null
    private var origin: Point? = null
    private var focusPoint: Point? = null
    private var signature: String? = null
    private var circles: CircleAnnotationManager? = null
    private var lines: PolylineAnnotationManager? = null
    private var ids: Map<String, String> = emptyMap()
    fun update(services: List<Service>, route: JSONObject?, origin: Point?, focusPoint: Point?) {
        this.services = services; this.route = route; this.origin = origin; this.focusPoint = focusPoint
        render()
    }
    fun render() {
        if (!ready) return
        val next = services.joinToString { "${it.id}:${it.latitude}:${it.longitude}" } + route.toString() + origin.toString() + focusPoint.toString()
        if (signature == next) return
        signature = next
        if (circles == null) {
            circles = view.annotations.createCircleAnnotationManager().also { manager ->
                manager.addClickListener { annotation -> ids[annotation.id]?.let(select); true }
            }
            lines = view.annotations.createPolylineAnnotationManager()
        }
        circles?.deleteAll(); lines?.deleteAll()
        val pins = circles!!.create(services.map { service -> CircleAnnotationOptions().withPoint(Point.fromLngLat(service.longitude, service.latitude))
            .withCircleRadius(10.0).withCircleColor("#00535B").withCircleStrokeColor("#FFFFFF").withCircleStrokeWidth(3.0) })
        ids = pins.mapIndexed { index, pin -> pin.id to services[index].id }.toMap()
        origin?.let { circles!!.create(CircleAnnotationOptions().withPoint(it).withCircleRadius(6.0).withCircleColor("#276BE8").withCircleStrokeColor("#FFFFFF").withCircleStrokeWidth(2.0)) }
        val geometry = route?.optJSONArray("geometry")
        val routePoints = if (geometry == null) emptyList() else (0 until geometry.length()).map { index -> geometry.getJSONArray(index).let { Point.fromLngLat(it.getDouble(1), it.getDouble(0)) } }
        if (routePoints.size > 1) lines!!.create(PolylineAnnotationOptions().withPoints(routePoints).withLineColor("#00535B").withLineWidth(5.0))
        val points = services.map { Point.fromLngLat(it.longitude, it.latitude) } + routePoints + listOfNotNull(origin)
        if (points.isNotEmpty()) view.post {
            if (signature != next) return@post
            val density = view.resources.displayMetrics.density
            val padding = EdgeInsets(if (overlay) 110.0 * density else 20.0, 30.0 * density,
                if (overlay) (if (routePoints.isNotEmpty()) 340.0 else 220.0) * density else 20.0, 30.0 * density)
            val focus = focusPoint
            if (focus != null) {
                view.mapboxMap.setCamera(CameraOptions.Builder().center(focus).zoom(15.0).padding(padding).build())
            } else {
                view.mapboxMap.cameraForCoordinates(points, CameraOptions.Builder().padding(padding).build(),
                    EdgeInsets(20.0 * density, 20.0 * density, 20.0 * density, 20.0 * density), 15.0, null) { camera ->
                    if (signature == next) view.mapboxMap.setCamera(camera)
                }
            }
        }
    }
    fun release() { signature = null; ready = false; circles?.onDestroy(); lines?.onDestroy() }
}
