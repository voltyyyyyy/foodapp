package sg.foodbridge.navigator

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId

data class UiState(
    val tab: String = "Find", val page: String = "home", val origin: String = "", val radius: Int = 80,
    val diets: Set<String> = emptySet(), val type: String = "All", val openOnly: Boolean = false,
    val wheelchair: Boolean = false, val preferred: String = "", val services: List<Service> = emptyList(),
    val results: List<Service> = emptyList(), val saved: Set<String> = emptySet(), val selected: Service? = null,
    val loading: Boolean = false, val offline: Boolean = false, val error: String? = null,
    val candidates: List<JSONObject> = emptyList(), val reference: String? = null,
    val route: JSONObject? = null, val routeDestination: Service? = null, val routeMode: String = "walk", val routeLoading: Boolean = false, val eta: Map<String, String> = emptyMap(),
    val staff: Boolean = false, val staffSection: String = "reports", val staffItems: List<JSONObject> = emptyList(),
    val staffSelected: JSONObject? = null, val notice: String? = null, val lastUpdate: String? = null,
    val clock: Long = System.currentTimeMillis(),
    val locationLoading: Boolean = false, val locationError: String? = null,
    val deviceLatitude: Double? = null, val deviceLongitude: Double? = null,
    val hasSearched: Boolean = false, val originLatitude: Double? = null, val originLongitude: Double? = null,
    val providers: List<JSONObject> = emptyList(), val providerOffline: Boolean = false,
    val providerLoading: Boolean = false, val providerError: String? = null, val selectedProvider: JSONObject? = null,
    val totalMatches: Int = 0, val informationReturnPage: String = "home"
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = Repository(application)
    private val mutable = MutableStateFlow(UiState())
    val state = mutable.asStateFlow()
    private var coordinates: Pair<Double, Double>? = null
    private var etaJob: Job? = null
    private var searchJob: Job? = null
    private var routeJob: Job? = null
    init {
        refresh()
        loadProviders()
        viewModelScope.launch { while (true) { delay(30_000); update { it.copy(clock = System.currentTimeMillis()) } } }
    }
    fun update(transform: (UiState) -> UiState) {
        val old = mutable.value
        val next = transform(old)
        val changed = old.origin != next.origin || old.radius != next.radius || old.diets != next.diets || old.type != next.type || old.openOnly != next.openOnly || old.wheelchair != next.wheelchair || old.preferred != next.preferred
        if (changed) etaJob?.cancel()
        mutable.value = if (changed) next.copy(results = emptyList(), totalMatches = 0, hasSearched = false, eta = emptyMap()) else next
    }
    fun error(message: String?) = update { it.copy(error = message, loading = false) }
    fun loadProviders() { viewModelScope.launch {
        update { it.copy(providerLoading = true, providerError = null) }
        try {
            val (providers, offline) = repo.providers()
            update { it.copy(providers = providers, providerOffline = offline, providerLoading = false) }
        } catch (_: Exception) { update { it.copy(providerLoading = false, providerError = "Provider directory unavailable. Try again when connected.") } }
    } }
    fun browseProviders() { update { it.copy(page = "providers", selectedProvider = null) }; loadProviders() }
    fun providerDetail(provider: JSONObject) { update { it.copy(informationReturnPage = it.page, page = "providerDetail", selectedProvider = provider, selected = null) } }
    fun refresh() { viewModelScope.launch {
        try {
            val (services, offline) = repo.all()
            val saved = repo.bookmarks()
            val diets = repo.diets()
            update { it.copy(services = services, offline = offline, saved = saved, diets = diets, lastUpdate = repo.lastUpdate) }
        } catch (e: Exception) { error("Could not load services. Start the API or try again. ${e.message}") }
    } }
    fun tab(name: String) { routeJob?.cancel(); update { it.copy(tab = name, page = if (name == "Find" && it.results.isNotEmpty()) "results" else "home", selected = null, error = null, routeDestination = null, route = null, routeLoading = false) }; if (name == "Saved") refresh() }
    fun back() {
        if (state.value.tab == "Map" && state.value.page == "home" && state.value.routeDestination != null) { closeRoute(); return }
        if (state.value.page in listOf("report", "confirmation")) { returnToInformation(); return }
        update { it.copy(page = if (it.page in listOf("detail", "providerDetail") && it.informationReturnPage in listOf("allSupport", "nameResults", "providers")) it.informationReturnPage else if (it.page == "providerDetail") "providers" else if (it.staff) "staff" else if (it.results.isNotEmpty() && it.tab == "Find") "results" else "home", selected = null, selectedProvider = null, reference = null, route = null, staffSelected = null, error = null) }
    }
    fun openReport() { update { it.copy(page = "report", reference = null, error = null) } }
    fun returnToInformation() { update { it.copy(page = if (it.selectedProvider != null) "providerDetail" else "detail", reference = null, error = null) } }
    fun origin(text: String) { searchJob?.cancel(); routeJob?.cancel(); coordinates = null; update { it.copy(origin = text, originLatitude = null, originLongitude = null, candidates = emptyList(), loading = false, error = null, locationLoading = false, locationError = null, route = null, routeLoading = false) } }
    fun openOrganisation(match: OrganisationMatch) {
        searchJob?.cancel()
        update { it.copy(loading = false, candidates = emptyList(), error = null) }
        match.service?.let { detail(it) } ?: match.provider?.let { providerDetail(it) }
    }
    fun showOrganisations() { searchJob?.cancel(); update { it.copy(page = "nameResults", loading = false, candidates = emptyList(), error = null) } }
    fun search() {
        if (state.value.tab == "Map" && state.value.routeDestination != null) { searchRouteOrigin(); return }
        if (state.value.origin.isBlank()) { browseAllSupport(); return }
        if (coordinates == null && organisationMatches(state.value).isNotEmpty()) { showOrganisations(); return }
        searchAddress()
    }
    fun browseAllSupport() {
        searchJob?.cancel(); etaJob?.cancel(); routeJob?.cancel()
        coordinates = null
        update { it.copy(page = "allSupport", loading = true, error = null, selected = null, selectedProvider = null,
            candidates = emptyList(), route = null, eta = emptyMap(), results = emptyList(), hasSearched = false,
            totalMatches = 0, originLatitude = null, originLongitude = null) }
        searchJob = viewModelScope.launch {
            try {
                val fetched = coroutineScope {
                    val servicesRequest = async { repo.all() }
                    val providersRequest = async { repo.providers() }
                    servicesRequest.await() to providersRequest.await()
                }
                val (services, offline) = fetched.first
                val (providers, providerOffline) = fetched.second
                update { it.copy(services = services, providers = providers, offline = offline,
                    providerOffline = providerOffline, loading = false, lastUpdate = repo.lastUpdate) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error("Could not refresh all support listings. Any saved listings remain below. Try again when connected.") }
        }
    }
    fun locationStarted() { update { it.copy(locationLoading = true, locationError = null, error = null) } }
    fun locationFailed(message: String) { update { it.copy(locationLoading = false, locationError = message) } }
    fun location(lat: Double, lon: Double) {
        if (lat !in 1.13..1.48 || lon !in 103.59..104.1) { locationFailed("Your device is outside Singapore. Enter a Singapore starting address."); return }
        searchJob?.cancel(); routeJob?.cancel()
        coordinates = lat to lon
        update { it.copy(origin = "Your location", originLatitude = lat, originLongitude = lon,
            deviceLatitude = lat, deviceLongitude = lon, locationLoading = false, locationError = null, error = null) }
        if (state.value.tab == "Map") search()
    }
    fun searchMapArea(lat: Double, lon: Double) {
        if (lat !in 1.13..1.48 || lon !in 103.59..104.1) { error("Move the map to Singapore to search this area."); return }
        coordinates = lat to lon
        update { it.copy(origin = "Selected map area", originLatitude = lat, originLongitude = lon, hasSearched = false, results = emptyList()) }
        search()
    }
    fun clearLocation() {
        coordinates = null
        etaJob?.cancel()
        searchJob?.cancel()
        routeJob?.cancel()
        update { it.copy(origin = "", originLatitude = null, originLongitude = null, deviceLatitude = null, deviceLongitude = null, locationLoading = false, locationError = null, hasSearched = false, results = emptyList(), totalMatches = 0, route = null, routeDestination = null, routeLoading = false, candidates = emptyList(), eta = emptyMap(), loading = false) }
    }
    fun toggleDiet(diet: String) {
        val diets = state.value.diets.let { if (diet in it) it - diet else it + diet }
        update { it.copy(diets = diets) }
        viewModelScope.launch { repo.saveDiets(diets) }
    }
    fun save(service: Service) {
        val ids = state.value.saved.let { if (service.id in it) it - service.id else it + service.id }
        update { it.copy(saved = ids) }
        viewModelScope.launch { repo.saveBookmarks(ids) }
    }
    fun clearLocal() { viewModelScope.launch { repo.clearLocal(); clearLocation(); update { it.copy(saved = emptySet(), diets = emptySet(), notice = "Local saves and preferences cleared") } } }
    fun searchAddress() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
        update { it.copy(loading = true, error = null, eta = emptyMap()) }
        try {
            if (coordinates == null) {
                val query = state.value.origin.trim()
                if (query.length < 3) throw IllegalArgumentException("Enter at least 3 characters for a Singapore address, or a six-digit postal code.")
                val response = JSONObject(repo.request("/api/geocode", "POST", JSONObject().put("query", query)))
                if (state.value.origin.trim() != query) return@launch
                val candidates = response.getJSONArray("candidates").objects()
                if (candidates.isEmpty()) throw IllegalArgumentException("No matching address. Try a postal code or choose an organisation suggestion.")
                if (candidates.size > 1) { update { it.copy(candidates = candidates, loading = false) }; return@launch }
                chooseOrigin(candidates.first(), false)
            }
            val (lat, lon) = coordinates ?: return@launch
            val s = state.value
            val body = JSONObject().put("latitude", lat).put("longitude", lon).put("radius_km", s.radius).put("limit", 10)
                .put("diets", JSONArray(s.diets.toList())).put("support_type", s.type).put("open_only", s.openOnly).put("wheelchair", s.wheelchair)
            if (s.preferred.isNotBlank()) {
                val parsed = runCatching { LocalDateTime.parse(s.preferred).atZone(ZoneId.of("Asia/Singapore")).toOffsetDateTime().toString() }
                    .getOrElse { throw IllegalArgumentException("Use YYYY-MM-DDTHH:MM for your preferred Singapore time.") }
                body.put("preferred_time", parsed)
            }
            val response = JSONObject(repo.request("/api/search", "POST", body))
            if (coordinates != (lat to lon) || SearchSummary(state.value) != SearchSummary(s)) {
                update { it.copy(loading = false) }; return@launch
            }
            val results = response.getJSONArray("services").objects().map(::Service)
            update { it.copy(results = results, totalMatches = response.optInt("total", results.size), hasSearched = true, loading = false, page = if (it.tab == "Map") "home" else "results", offline = false, lastUpdate = response.optString("last_update")) }
            estimate(results)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error(e.message ?: "Search unavailable. Your input has been kept.") }
    } }
    fun chooseOrigin(candidate: JSONObject, submit: Boolean = true) {
        coordinates = candidate.getDouble("latitude") to candidate.getDouble("longitude")
        update { it.copy(origin = candidate.getString("address"), originLatitude = coordinates?.first, originLongitude = coordinates?.second, candidates = emptyList(), error = null) }
        if (submit) { if (state.value.routeDestination != null && state.value.tab == "Map") route(state.value.routeMode) else searchAddress() }
    }
    private fun estimate(services: List<Service>) {
        etaJob?.cancel()
        etaJob = viewModelScope.launch {
            val semaphore = Semaphore(3)
            services.map { service -> async {
                semaphore.withPermit {
                    val result = runCatching { getRoute(service, "walk") }.getOrNull()
                    val label = if (result?.optBoolean("available") == true) "${kotlin.math.ceil(result.getDouble("duration_minutes")).toInt()} min walk" else "Travel time unavailable"
                    update { it.copy(eta = it.eta + (service.id to label)) }
                }
            } }.awaitAll()
        }
    }
    fun detail(service: Service) {
        routeJob?.cancel()
        update { it.copy(informationReturnPage = it.page, selected = service, selectedProvider = null, page = "detail", route = null, routeDestination = null, routeLoading = false, error = null) }
        viewModelScope.launch {
            runCatching { Service(JSONObject(repo.request("/api/services/${service.id}"))) }.onSuccess { fresh ->
                if (!service.distance.isNaN()) fresh.json.put("distance_km", service.distance)
                fresh.json.put("explanations", service.json.optJSONArray("explanations") ?: JSONArray())
                update { if (it.selected?.id == fresh.id) it.copy(selected = fresh) else it }
            }
        }
    }
    private suspend fun getRoute(service: Service, mode: String): JSONObject {
        val origin = coordinates ?: throw IllegalArgumentException("Search an address on Find or Map before requesting directions.")
        return JSONObject(repo.request("/api/routes", "POST", JSONObject().put("latitude", origin.first)
            .put("longitude", origin.second).put("service_id", service.id).put("mode", mode)))
    }
    fun closeRoute() { routeJob?.cancel(); update { it.copy(routeDestination = null, route = null, routeLoading = false, candidates = emptyList(), error = null) } }
    fun searchRouteOrigin() {
        routeJob?.cancel()
        val destination = state.value.routeDestination ?: return
        val mode = state.value.routeMode
        if (coordinates != null) { route(mode); return }
        routeJob = viewModelScope.launch {
            update { it.copy(routeLoading = true, route = null, error = null) }
            try {
                val query = state.value.origin.trim()
                if (query.length < 3) throw IllegalArgumentException("Enter a starting address or use current location.")
                val response = JSONObject(repo.request("/api/geocode", "POST", JSONObject().put("query", query)))
                if (state.value.origin.trim() != query || state.value.routeDestination?.id != destination.id) return@launch
                val candidates = response.getJSONArray("candidates").objects()
                if (candidates.isEmpty()) throw IllegalArgumentException("No starting address found. Try a postal code.")
                if (candidates.size > 1) { update { it.copy(candidates = candidates, routeLoading = false) }; return@launch }
                chooseOrigin(candidates.first(), false)
                route(mode)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { update { it.copy(routeLoading = false, route = JSONObject().put("available", false).put("message", e.message ?: "Starting address unavailable.")) } }
        }
    }
    fun route(mode: String) {
        val service = state.value.routeDestination ?: state.value.selected ?: return
        if (mode !in listOf("walk", "pt", "drive")) return
        routeJob?.cancel(); searchJob?.cancel(); etaJob?.cancel()
        if (state.value.routeDestination?.id != service.id) {
            val snapshot = state.value
            coordinates = snapshot.deviceLatitude?.let { lat -> snapshot.deviceLongitude?.let { lat to it } }
            update { it.copy(origin = if (coordinates != null) "Your location" else "", originLatitude = coordinates?.first,
                originLongitude = coordinates?.second, locationError = null) }
        }
        update { it.copy(tab = "Map", page = "home", origin = if (coordinates == null && it.routeDestination == null) "" else it.origin, routeDestination = service, routeMode = mode,
            route = null, routeLoading = coordinates != null, loading = false, error = null, candidates = emptyList()) }
        if (coordinates == null) return
        val requestOrigin = coordinates
        routeJob = viewModelScope.launch {
            try {
                val route = getRoute(service, mode)
                if (coordinates == requestOrigin && state.value.routeDestination?.id == service.id && state.value.routeMode == mode)
                    update { it.copy(route = route, routeLoading = false) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { update { it.copy(routeLoading = false, route = JSONObject().put("available", false).put("message", "Route unavailable. Check your connection and try again.")) } }
        }
    }
    fun report(category: String, description: String, evidence: String) { viewModelScope.launch {
        update { it.copy(loading = true, error = null) }
        try {
            val snapshot = state.value
            val targetId = snapshot.selected?.id ?: snapshot.selectedProvider?.getString("id") ?: throw IllegalStateException("Open a listing before reporting incorrect information.")
            val body = JSONObject().put("service_id", targetId).put("target_kind", if (snapshot.selectedProvider != null) "provider" else "service").put("category", category).put("description", description.trim()).put("evidence", evidence.trim())
            val response = JSONObject(repo.request("/api/reports", "POST", body))
            update { it.copy(reference = response.getString("reference"), page = "confirmation", loading = false) }
        } catch (e: Exception) { error(e.message ?: "Report could not be submitted. Your input has been kept.") }
    } }
    fun login(email: String, password: String) { viewModelScope.launch {
        update { it.copy(loading = true, error = null) }
        try {
            val result = JSONObject(repo.request("/api/admin/login", "POST", JSONObject().put("email", email).put("password", password)))
            repo.token = result.getString("token")
            update { it.copy(staff = true, page = "staff", loading = false) }
            staffLoad("reports")
        } catch (e: Exception) { error(e.message ?: "Sign-in failed") }
    } }
    fun logout() { viewModelScope.launch { runCatching { repo.request("/api/admin/logout", "POST") }; repo.token = null; update { it.copy(staff = false, page = "home", staffItems = emptyList(), staffSelected = null) } } }
    fun staffLoad(section: String) { viewModelScope.launch {
        update { it.copy(staffSection = section, loading = true, error = null, page = "staff", staffSelected = null) }
        try { val items = JSONArray(repo.request("/api/admin/$section")).objects(); update { it.copy(staffItems = items, loading = false) } }
        catch (e: Exception) { error(e.message ?: "Could not load staff data") }
    } }
    fun staffAction(path: String, method: String = "POST", body: JSONObject) { viewModelScope.launch {
        update { it.copy(loading = true, error = null) }
        try {
            val result = JSONObject(repo.request(path, method, body))
            val notice = if (path == "/api/admin/organisations") "Organisation created · ID: ${result.optString("id")}" else if (path == "/api/admin/import") "${result.optInt("accepted_pending")} records pending review · ${result.optInt("quarantined")} quarantined" else "Saved successfully"
            update { it.copy(notice = notice, loading = false) }; staffLoad(state.value.staffSection); refresh()
        }
        catch (e: Exception) { error(e.message ?: "Could not save this decision") }
    } }
}
