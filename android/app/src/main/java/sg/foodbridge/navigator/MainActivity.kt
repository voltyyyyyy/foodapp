package sg.foodbridge.navigator

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val Teal = Color(0xFF00535B)
val Ink = Color(0xFF182D31)
val Muted = Color(0xFF617175)
val Canvas = Color(0xFFFAFAF8)
val Mint = Color(0xFFE2F3EA)
val Peach = Color(0xFFFFF0E5)
val Readable = FontFamily(Font(R.font.atkinson_regular), Font(R.font.atkinson_bold, FontWeight.Bold))

class MainActivity : ComponentActivity() {
    private val model: AppViewModel by viewModels()
    private var locationRequest: CancellationSignal? = null
    fun requestCurrentLocation() {
        if (model.state.value.account == null) return
        locationRequest?.cancel()
        val signal = CancellationSignal()
        locationRequest = signal
        model.locationStarted()
        try {
            val manager = getSystemService(LocationManager::class.java)
            val providers = manager.getProviders(true)
            val provider = when {
                LocationManager.FUSED_PROVIDER in providers -> LocationManager.FUSED_PROVIDER
                LocationManager.GPS_PROVIDER in providers && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> LocationManager.GPS_PROVIDER
                else -> LocationManager.NETWORK_PROVIDER
            }
            manager.getCurrentLocation(provider, signal, mainExecutor) { location ->
                if (!signal.isCanceled) {
                    locationRequest = null
                    if (model.state.value.locationLoading) {
                        if (location == null) model.locationFailed("Could not find your device location. Enter a starting address or try again.")
                        else model.location(location.latitude, location.longitude)
                    }
                }
            }
            Handler(Looper.getMainLooper()).postDelayed({
                if (locationRequest === signal) {
                    signal.cancel(); locationRequest = null
                    if (model.state.value.locationLoading) model.locationFailed("Location is taking too long. Enter a starting address or try again.")
                }
            }, 10_000)
        } catch (_: Exception) {
            signal.cancel(); locationRequest = null
            model.locationFailed("Turn on device location and allow access, or enter a starting address.")
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FoodBridgeTheme { FoodBridge(model) } }
    }
    override fun onStop() {
        super.onStop()
        locationRequest?.cancel()
        locationRequest = null
        // A session ends when the app leaves the foreground; no location enters saved state.
        model.clearLocation()
    }
    override fun onResume() { super.onResume(); model.checkSession() }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) model.touchSession()
        return super.dispatchTouchEvent(event)
    }
}

@Composable fun FoodBridgeTheme(content: @Composable () -> Unit) {
    val base = Typography()
    MaterialTheme(colorScheme = lightColorScheme(primary = Teal, secondary = Color(0xFF32694F),
        background = Canvas, surface = Color.White, onSurface = Ink, onBackground = Ink,
        surfaceVariant = Color(0xFFEFF3F4), onSurfaceVariant = Muted),
        typography = Typography(
            displayLarge = base.displayLarge.copy(fontFamily = Readable),
            displayMedium = base.displayMedium.copy(fontFamily = Readable),
            displaySmall = base.displaySmall.copy(fontFamily = Readable),
            headlineLarge = androidx.compose.ui.text.TextStyle(fontFamily = Readable, fontSize = 32.sp, lineHeight = 37.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.5).sp),
            headlineMedium = androidx.compose.ui.text.TextStyle(fontFamily = Readable, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
            headlineSmall = base.headlineSmall.copy(fontFamily = Readable),
            titleLarge = androidx.compose.ui.text.TextStyle(fontFamily = Readable, fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold),
            titleMedium = base.titleMedium.copy(fontFamily = Readable),
            titleSmall = base.titleSmall.copy(fontFamily = Readable),
            bodyLarge = androidx.compose.ui.text.TextStyle(fontFamily = Readable, fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = androidx.compose.ui.text.TextStyle(fontFamily = Readable, fontSize = 14.sp, lineHeight = 21.sp),
            bodySmall = base.bodySmall.copy(fontFamily = Readable),
            labelLarge = base.labelLarge.copy(fontFamily = Readable),
            labelMedium = base.labelMedium.copy(fontFamily = Readable),
            labelSmall = base.labelSmall.copy(fontFamily = Readable)
        ), content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FoodBridge(model: AppViewModel) {
    val s by model.state.collectAsStateWithLifecycle()
    if (s.authChecking) {
        Box(Modifier.fillMaxSize().background(Canvas), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Teal) }
        return
    }
    if (s.account == null) { AccountEntryScreen(s, model); return }
    var showDemoInfo by remember { mutableStateOf(false) }
    BackHandler((s.page != "home" && s.page != "results") || (s.tab == "Map" && s.routeDestination != null)) { model.back() }
    Scaffold(containerColor = Canvas, topBar = {
        if (s.tab == "Map" && (s.page == "home" || s.page == "results")) Spacer(Modifier.statusBarsPadding()) else Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (s.page != "home" && s.page != "results") IconButton(onClick = model::back) { Icon(Icons.Outlined.ArrowBack, "Back") }
            else { Icon(Icons.Outlined.Spa, null, Modifier.size(24.dp), tint = Teal); Spacer(Modifier.width(8.dp)) }
            Text("foodbridge", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.6).sp)
            Spacer(Modifier.weight(1f))
            if (s.services.any { it.demo }) TextButton(onClick = { showDemoInfo = true }) { Text("Demo data", color = Muted, fontSize = 12.sp) }
        }
    }, bottomBar = {
        NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
            listOf("Find" to Icons.Outlined.Search, "Map" to Icons.Outlined.Map, "Saved" to Icons.Outlined.BookmarkBorder, "Preferences" to Icons.Outlined.PersonOutline).forEach { (name, icon) ->
                NavigationBarItem(selected = s.tab == name, onClick = { model.tab(name) }, icon = { Icon(icon, null, Modifier.size(23.dp)) }, label = { Text(if (name == "Preferences") "Profile" else name, fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = Mint, selectedIconColor = Teal, selectedTextColor = Teal))
            }
        }
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (s.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Teal)
            if (s.offline) Banner("Offline · last local update ${formatTime(s.lastUpdate.orEmpty())}", Icons.Outlined.CloudOff, Peach)
            s.error?.let { Banner(it, Icons.Outlined.Info, Color(0xFFFFE9E6), onClose = { model.error(null) }) }
            s.notice?.let { Banner(it, Icons.Outlined.CheckCircle, Mint, onClose = { model.update { it.copy(notice = null) } }) }
            when (s.page) {
                "detail" -> s.selected?.let { DetailScreen(it, s, model) }
                "nameResults" -> OrganisationResultsScreen(s, model)
                "allSupport" -> OrganisationResultsScreen(s, model, browseAll = true)
                "report" -> ReportScreen(s, model)
                "confirmation" -> Confirmation(s, model)
                "help" -> HelpScreen()
                "privacy" -> PrivacyScreen()
                "providers" -> ProvidersScreen(s, model)
                "providerDetail" -> s.selectedProvider?.let { ProviderDetailScreen(it, model) }
                "password" -> ChangePasswordScreen(s, model)
                else -> when (s.tab) {
                    "Map" -> MapScreen(s, model)
                    "Saved" -> SavedScreen(s, model)
                    "Preferences" -> PreferencesScreen(s, model)
                    else -> FindScreen(s, model)
                }
            }
        }
        if (s.candidates.isNotEmpty()) AlertDialog(onDismissRequest = { model.update { it.copy(candidates = emptyList()) } },
            title = { Text("Choose your location") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                s.candidates.forEach { c -> TextButton(onClick = { model.chooseOrigin(c) }) { Text(c.getString("address")) } }
            } }, confirmButton = {})
        if (showDemoInfo) AlertDialog(onDismissRequest = { showDemoInfo = false }, title = { Text("About this preview") },
            text = { Text("This workspace includes fictional listings marked Demo. Do not travel to those locations for support. Other listings retain their reviewed source information.") },
            confirmButton = { TextButton(onClick = { showDemoInfo = false }) { Text("Got it") } })
    }
}

@Composable fun Banner(text: String, icon: ImageVector, color: Color, onClose: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().background(color).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = Teal); Spacer(Modifier.width(10.dp)); Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (onClose != null) IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Close, "Dismiss") }
    }
}
@Composable fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(24.dp), content = content)
}
@Composable fun Panel(color: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = color, shadowElevation = 0.dp,
        border = if (color == Color.White) BorderStroke(1.dp, Color(0xFFE8ECE9)) else null) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}
@Composable fun Eyebrow(text: String) { Text(text, color = Muted, fontSize = 12.sp) }
@Composable fun Primary(text: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(16.dp)) {
        Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)); Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
@Composable fun Filter(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp),
        leadingIcon = if (selected) { { Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) } } else null,
        shape = RoundedCornerShape(12.dp),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected, borderColor = Color(0xFFDDE4DF), selectedBorderColor = Color.Transparent),
        colors = FilterChipDefaults.filterChipColors(containerColor = Color.White, labelColor = Muted, selectedContainerColor = Mint, selectedLabelColor = Teal))
}

fun SearchSummary(s: UiState): String = buildList {
    add(if (s.radius >= 80) "Across Singapore" else "Within ${s.radius} km")
    add(if (s.type == "All") "Any support" else s.type)
    if (s.diets.isNotEmpty()) add(s.diets.joinToString())
    if (s.openOnly) add("Open now")
    if (s.wheelchair) add("Wheelchair access")
    if (s.preferred.isNotBlank()) add(s.preferred.replace("T", " at "))
}.joinToString(" · ")

@Composable fun SearchOptionsRow(s: UiState, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Tune, null, Modifier.size(20.dp), tint = Teal)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Search filters", fontWeight = FontWeight.Medium)
            Text(SearchSummary(s), color = Muted, fontSize = 13.sp)
        }
        Icon(Icons.Outlined.ChevronRight, "Edit search filters", tint = Muted)
    }
}

@Composable fun FindScreen(s: UiState, model: AppViewModel) {
    if (s.page == "results") { ResultsScreen(s, model); return }
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var filters by remember { mutableStateOf(false) }
    fun locate() { (context as? MainActivity)?.requestCurrentLocation() }
    fun search() { focus.clearFocus(); model.search() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.any { it }) locate() else model.error("Location permission was declined. Enter a postal code instead.")
    }
    Page {
        if (s.locationLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        s.locationError?.let { Text(it, color = Muted) }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Find food support\nnear you.", style = MaterialTheme.typography.headlineLarge)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(s.origin, model::origin, label = { Text("Address or organisation") },
                placeholder = { Text("e.g. 560123 or Willing Hearts") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(14.dp), leadingIcon = { Icon(Icons.Outlined.LocationOn, null, tint = Muted) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }))
            SearchSuggestions(s, model) { focus.clearFocus() }
            TextButton(onClick = {
                focus.clearFocus()
                if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate()
                else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            }, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.MyLocation, null, Modifier.size(17.dp)); Spacer(Modifier.width(8.dp)); Text("Use current location", fontSize = 14.sp)
            }
            HorizontalDivider(color = Color(0xFFE4E8E5))
            SearchOptionsRow(s) { focus.clearFocus(); filters = true }
            HorizontalDivider(color = Color(0xFFE4E8E5))
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Primary(if (s.loading) "Finding support…" else "Find support", Icons.Outlined.Search, !s.loading) { search() }
            if (s.origin.isBlank()) Text("Leave this blank to browse all food support.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = model::browseProviders, contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Outlined.Groups, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Browse food support providers", fontSize = 14.sp)
            }
            TextButton(onClick = { model.update { it.copy(page = "help") } }, contentPadding = PaddingValues(0.dp)) { Text("Need help getting started?", fontSize = 14.sp) }
        }
    }
    if (filters) SearchFiltersSheet(s, model) { filters = false }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun SearchFiltersSheet(s: UiState, model: AppViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Canvas) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.88f)) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text("Refine your search", style = MaterialTheme.typography.headlineMedium)
                Text("Choose what works for you.", color = Muted, modifier = Modifier.padding(top = 6.dp))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Column {
                    Text("Search area", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(80, 1, 3, 5, 10, 20).forEach { r -> Filter(if (r == 80) "Across Singapore" else "$r km", r == s.radius) { model.update { it.copy(radius = r) } } }
                    }
                }
                Column {
                    Text("Type of support", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("All", "Hot meals", "Groceries", "Food bank", "Delivery").forEach { type -> Filter(if (type == "All") "Any" else type, s.type == type) { model.update { it.copy(type = type) } } }
                    }
                }
                Column {
                    Text("Dietary needs", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Halal", "Vegetarian", "Vegan", "Gluten-free", "Nut-free").forEach { diet -> Filter(diet, diet in s.diets) { model.toggleDiet(diet) } }
                    }
                    Text("Every selected need must match. Confirm allergens with the provider.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                }
                Column {
                    HorizontalDivider(color = Color(0xFFE4E8E5))
                    Toggle("Scheduled open now", s.openOnly) { value -> model.update { it.copy(openOnly = value) } }
                    Toggle("Wheelchair accessible", s.wheelchair) { value -> model.update { it.copy(wheelchair = value) } }
                    HorizontalDivider(color = Color(0xFFE4E8E5))
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Preferred time", fontWeight = FontWeight.SemiBold)
                    OutlinedButton(onClick = {
                        val today = LocalDate.now(ZoneId.of("Asia/Singapore"))
                        DatePickerDialog(context, { _, year, month, day ->
                            val date = LocalDate.of(year, month + 1, day)
                            val time = LocalTime.now(ZoneId.of("Asia/Singapore"))
                            TimePickerDialog(context, { _, hour, minute ->
                                model.update { it.copy(preferred = LocalDateTime.of(date, LocalTime.of(hour, minute)).toString()) }
                            }, time.hour, time.minute, false).show()
                        }, today.year, today.monthValue - 1, today.dayOfMonth).show()
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text(if (s.preferred.isBlank()) "Now · change time" else s.preferred.replace("T", " at "))
                    }
                    if (s.preferred.isNotBlank()) TextButton(onClick = { model.update { it.copy(preferred = "") } }) { Text("Use current time") }
                }
            }
            Surface(color = Canvas) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) { Primary("Done", Icons.Outlined.Check, onClick = onDismiss) }
            }
        }
    }
}

@Composable fun ResultsScreen(s: UiState, model: AppViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Nearest support", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    Text("${s.results.size} of ${s.totalMatches}", fontSize = 12.sp, color = Muted)
                }
                Text("Up to 10 matches, closest first by straight-line distance.", color = Muted, style = MaterialTheme.typography.bodySmall)
                Surface(color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFFE8ECE9))) {
                    Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(s.origin, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text(SearchSummary(s), color = Muted, fontSize = 12.sp)
                        }
                        TextButton(onClick = { model.update { it.copy(page = "home") } }) { Text("Edit") }
                    }
                }
            }
        }
        if (s.results.isEmpty()) item { Empty("No confirmed matches", "No listed service confirms all your selected requirements. Your current filters: ${SearchSummary(s)}. You can review filters or browse provider contacts to ask about support.", Icons.Outlined.SearchOff) { Primary("Review search filters", Icons.Outlined.Tune) { model.update { it.copy(page = "home") } }; TextButton(onClick = model::browseProviders) { Text("Browse provider contacts") } } }
        items(s.results, key = { it.id }) { ServiceCard(it, s, model) }
        item { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Contact the provider before travelling. Opening hours do not guarantee food availability.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            Text("Updated ${formatTime(s.lastUpdate.orEmpty())}", color = Muted, fontSize = 11.sp)
            if (s.results.any { it.demo }) DemoNotice()
        } }
    }
}

@Composable fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(checked, onChange) }
}
@Composable fun DemoNotice() { Text("Listings marked Demo are fictional. Do not travel to those locations for food support.", color = Color(0xFF8A6035), fontSize = 12.sp, lineHeight = 18.sp) }
@Composable fun Availability(status: String) {
    val label = when(status) { "AVAILABLE" -> "Reported available"; "LIMITED" -> "Limited availability"; "UNAVAILABLE" -> "Reported unavailable"; else -> "Availability unknown" }
    val color = when(status) { "AVAILABLE" -> Mint; "LIMITED" -> Peach; "UNAVAILABLE" -> Color(0xFFFFE9E6); else -> Color(0xFFEEF1F3) }
    Surface(color = color, shape = RoundedCornerShape(8.dp)) { Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (status == "AVAILABLE") Icons.Outlined.CheckCircle else if (status == "LIMITED") Icons.Outlined.Schedule else Icons.Outlined.Info, null, Modifier.size(14.dp)); Spacer(Modifier.width(5.dp)); Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    } }
}
@Composable fun ServiceCard(service: Service, s: UiState, model: AppViewModel) {
    Surface(color = Color.White, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFE8ECE9)),
        modifier = Modifier.fillMaxWidth().clickable { model.detail(service) }) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(buildList {
                    add(service.type)
                    if (!service.distance.isNaN()) add("%.1f km".format(service.distance))
                }.joinToString(" · "), color = Muted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
                if (service.demo) Text("Demo", color = Color(0xFF8A6035), fontSize = 11.sp, lineHeight = 16.sp)
                IconButton(onClick = { model.save(service) }, modifier = Modifier.size(48.dp)) {
                    Icon(if (service.id in s.saved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                        if (service.id in s.saved) "Remove saved service" else "Save service", tint = Teal, modifier = Modifier.size(22.dp))
                }
            }
            Text(service.organisationName, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
            if (service.organisationName != service.name) Text(service.name, fontSize = 13.sp, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${accessMethodLabel(service.json.optString("access_method", "contact_to_confirm"))} · ${collectionLocationLabel(service.json.optString("location_role"))}", color = Teal, fontSize = 12.sp, lineHeight = 18.sp)
            Availability(service.availability)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(when(service.operating) { "OPEN" -> "Scheduled open now"; "CLOSED" -> "Outside scheduled hours"; else -> "Hours not confirmed" }, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                service.evidence?.let { Text("Checked ${formatTime(it.optString("observed_at"))}", color = Muted, fontSize = 11.sp, lineHeight = 16.sp) }
                service.strings("explanations").firstOrNull()?.let { Text(it, color = Teal, fontSize = 12.sp, lineHeight = 18.sp) }
                s.eta[service.id]?.let { Text(it, color = Muted, fontSize = 12.sp, lineHeight = 18.sp) }
            }
        }
    }
}
fun formatTime(value: String): String = runCatching { DateTimeFormatter.ofPattern("d MMM, h:mm a").withZone(ZoneId.of("Asia/Singapore")).format(OffsetDateTime.parse(value).toInstant()) }.getOrDefault("Not confirmed")

@Composable fun Empty(title: String, detail: String, icon: ImageVector, action: @Composable () -> Unit = {}) {
    Panel { Icon(icon, null, Modifier.size(32.dp), tint = Teal); Text(title, style = MaterialTheme.typography.titleLarge); Text(detail, color = Muted); action() }
}
@Composable fun SavedScreen(s: UiState, model: AppViewModel) { Page {
    Text("Saved", style = MaterialTheme.typography.headlineMedium); Text("Your bookmarked services, kept on this device.", color = Muted)
    val services = s.services.filter { it.id in s.saved }
    if (s.saved.isEmpty()) Empty("No saved services", "Tap a service’s bookmark to keep it here.", Icons.Outlined.BookmarkBorder) { Primary("Find food support", Icons.Outlined.Search) { model.tab("Find") } }
    services.forEach { ServiceCard(it, s, model) }
    (s.saved - s.services.map { it.id }.toSet()).forEach { id ->
        Panel { Text("Service no longer listed", fontWeight = FontWeight.Bold)
            Text("This saved service is no longer in the latest approved information available to this device.", color = Muted)
            OutlinedButton(onClick = { model.removeOrSave(id) }) { Text("Remove bookmark") }
        }
    }
} }

@Composable fun PreferencesScreen(s: UiState, model: AppViewModel) { Page {
    Text("Your profile", style = MaterialTheme.typography.headlineMedium)
    ProfilePanel(s, model)
    Column { Text("Dietary preferences", style = MaterialTheme.typography.titleLarge); Text("Used for your searches. Change them at any time.", color = Muted)
        listOf("Halal", "Vegetarian", "Vegan", "Gluten-free", "Nut-free").forEach { diet -> Toggle(diet, diet in s.diets) { model.toggleDiet(diet) } }
    }
    HorizontalDivider(color = Color(0xFFE4E8E5))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("Privacy", style = MaterialTheme.typography.titleLarge); Text("Bookmarks and preferences stay on this device. Search location is cleared when you leave the app.", color = Muted)
        TextButton(onClick = { model.update { it.copy(page = "privacy") } }) { Icon(Icons.Outlined.Shield, null); Text("  Privacy & data use") }
        OutlinedButton(onClick = model::clearLocal, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Clear local saves & preferences") }
    }
    Text("FoodBridge Navigator · 1.0.0", color = Muted, fontSize = 12.sp)
} }

@Composable fun ReportScreen(s: UiState, model: AppViewModel) {
    var category by remember(s.reportCategory) { mutableStateOf(s.reportCategory) }
    var categoryMenu by remember { mutableStateOf(false) }
    var description by remember(s.reportDescription) { mutableStateOf(s.reportDescription) }
    var evidence by remember(s.reportEvidence) { mutableStateOf(s.reportEvidence) }
    SideEffect { if (s.reportCategory != category || s.reportDescription != description || s.reportEvidence != evidence)
        model.update { it.copy(reportCategory = category, reportDescription = description, reportEvidence = evidence) } }
    Page {
        Text("Report incorrect information", style = MaterialTheme.typography.headlineMedium); Text("For ${s.selected?.name ?: s.selectedProvider?.optString("name").orEmpty()}", color = Teal)
        Panel {
            Text("What is incorrect?", fontWeight = FontWeight.SemiBold)
            Box {
                OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(12.dp)) {
                    Text(reportCategoryLabel(category).ifBlank { "Choose a category" }, modifier = Modifier.weight(1f)); Icon(Icons.Outlined.ExpandMore, null)
                }
                DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                    listOf("Address", "Contact", "Hours", "Service details", "Permanently closed", "Food unavailable", "Other").forEach { value ->
                        DropdownMenuItem(text = { Text(reportCategoryLabel(value)) }, onClick = { category = value; categoryMenu = false })
                    }
                }
            }
            OutlinedTextField(description, { if (it.length <= 1000) description = it }, label = { Text("Describe the issue") }, supportingText = { Text("${description.length} / 1000 characters") }, modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp), minLines = 4)
            OutlinedTextField(evidence, { evidence = it }, label = { Text("Evidence link (optional)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            Text("No account required. Please avoid including names, phone numbers or other personal details in your report.", color = Muted, style = MaterialTheme.typography.bodyMedium)
            Text("An official website or public announcement helps staff verify your report. Reports are reviewed before a listing changes.", color = Muted, style = MaterialTheme.typography.bodySmall)
            Primary("Submit report", Icons.Outlined.Send, !s.loading && category.isNotBlank() && description.isNotBlank()) { model.report(category, description, evidence) }
        }
    }
}
@Composable fun Confirmation(s: UiState, model: AppViewModel) { Page {
    Spacer(Modifier.height(24.dp)); Icon(Icons.Outlined.CheckCircle, null, Modifier.size(64.dp), tint = Teal)
    Text("Thank you for helping.", style = MaterialTheme.typography.headlineMedium); Text("Your correction is awaiting staff review. It has not changed the public listing.", color = Muted)
    Panel(Mint) { Eyebrow("Your report reference"); Text(s.reference.orEmpty(), fontWeight = FontWeight.Bold, fontSize = 20.sp) }
    Primary("Back to support", Icons.Outlined.ArrowBack) { model.returnToInformation() }
} }

@Composable fun HelpScreen() { Page {
    Eyebrow("One step at a time"); Text("Need food support today?", style = MaterialTheme.typography.headlineMedium)
    Panel(Mint) { Text("Start with a nearby service", style = MaterialTheme.typography.titleLarge); Text("Search for hot meals or groceries, check eligibility, and contact the provider before travelling. Scheduled opening does not guarantee food is available.") }
    Panel { Text("If you need help finding assistance", style = MaterialTheme.typography.titleLarge); Text("Visit Singapore's SupportGoWhere directory for current official assistance options.", color = Muted)
        val context = LocalContext.current
        Primary("Open SupportGoWhere", Icons.Outlined.OpenInNew) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://supportgowhere.life.gov.sg/"))) }
    }
    Text("This is a directory, not an emergency dispatch service. We do not promise immediate food provision.", color = Muted)
} }
@Composable fun PrivacyScreen() { Page {
    Text("Privacy & data use", style = MaterialTheme.typography.headlineMedium)
    Panel { Text("An active account and valid session are required to find support, view details, plan routes, save services and report errors."); Text("Device location is used for your current search and route request, then cleared when the app leaves the foreground, the search is replaced, or you sign out. It is not saved in profiles, bookmarks or app backups."); Text("OneMap receives address and route requests. Google Maps receives map requests. Route origins and results are held only for the current session."); Text("Saved service IDs and dietary preferences stay on this device and are separated by account. Signing out hides them. Session credentials are encrypted using Android Keystore."); Text("Your account identifier is stored with correction reports for staff review. Avoid sending sensitive personal details."); Text("A production operator must publish its contact and retention policy before release.", color = Muted) }
} }
