package sg.foodbridge.navigator

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.Instant
import java.util.UUID

data class NavigationEntry(val page: String, val tab: String, val serviceId: String? = null, val providerId: String? = null)

data class UiState(
    val tab: String = "Find", val page: String = "home", val origin: String = "", val radius: Int = 80,
    val diets: Set<String> = emptySet(), val searchDiets: Set<String> = emptySet(), val type: String = "All", val openOnly: Boolean = false,
    val wheelchair: Boolean = false, val preferred: String = "", val services: List<Service> = emptyList(),
    val results: List<Service> = emptyList(), val saved: Set<String> = emptySet(), val selected: Service? = null,
    val loading: Boolean = false, val offline: Boolean = false, val error: String? = null,
    val candidates: List<JSONObject> = emptyList(), val reference: String? = null,
    val route: JSONObject? = null, val routeDestination: Service? = null, val routeMode: String = "walk", val routeLoading: Boolean = false, val eta: Map<String, String> = emptyMap(),
    val notice: String? = null, val lastUpdate: String? = null,
    val clock: Long = System.currentTimeMillis(),
    val locationLoading: Boolean = false, val locationError: String? = null,
    val deviceLatitude: Double? = null, val deviceLongitude: Double? = null,
    val startingAddress: String? = null, val startingLatitude: Double? = null, val startingLongitude: Double? = null,
    val hasSearched: Boolean = false, val originLatitude: Double? = null, val originLongitude: Double? = null,
    val providers: List<JSONObject> = emptyList(), val providerOffline: Boolean = false,
    val providerLoading: Boolean = false, val providerError: String? = null, val selectedProvider: JSONObject? = null,
    val totalMatches: Int = 0, val informationReturnPage: String = "home",
    val account: JSONObject? = null, val authChecking: Boolean = true, val authBusy: Boolean = false,
    val authPage: String = "signin", val authError: String? = null, val authMessage: String? = null,
    val reportCategory: String = "", val reportDescription: String = "", val reportEvidence: String = "",
    val profileNameDraft: String? = null,
    val reportService: Service? = null, val reportProvider: JSONObject? = null,
    val reportLeavePrompt: Boolean = false, val reportNextTab: String? = null,
    val reminders: Map<String, String> = emptyMap(), val notificationsAllowed: Boolean = false,
    val reminderTime: String = "08:00",
    val navigation: List<NavigationEntry> = emptyList(),
    val searchStatus: String = "idle", val locationCleared: Boolean = false, val filtersRequested: Boolean = false,
    val catalogueLoading: Boolean = false,
    val detailLoading: Boolean = false, val detailError: String? = null, val detailWithdrawn: Boolean = false,
    val resultListIndex: Int = 0, val resultListOffset: Int = 0,
    val nameListIndex: Int = 0, val nameListOffset: Int = 0, val browseListIndex: Int = 0, val browseListOffset: Int = 0,
    val providerQuery: String = "", val providerCategory: String = "All", val providerListIndex: Int = 0, val providerListOffset: Int = 0,
    val authFieldErrors: Map<String, String> = emptyMap(), val reportFieldErrors: Map<String, String> = emptyMap(),
    val privacyInfo: JSONObject? = null, val privacyError: String? = null,
    val reportSubmissionId: String? = null, val reportReceipts: List<JSONObject> = emptyList(),
    val reportTargetPrompt: Boolean = false, val pendingReportService: Service? = null, val pendingReportProvider: JSONObject? = null,
    val undoBookmarkId: String? = null, val undoBookmarkReminder: String? = null
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = Repository(application)
    private val reminderStore = ReminderStore(application)
    private val mutable = MutableStateFlow(UiState())
    val state = mutable.asStateFlow()
    private var coordinates: Pair<Double, Double>? = null
    private var etaJob: Job? = null
    private var searchJob: Job? = null
    private var routeJob: Job? = null
    private var draftSaveJob: Job? = null
    private var userScope = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[Job]))
    private var interrupted: UiState? = null
    private var interruptedAccount: String? = null
    private var lastServerActivity = 0L
    private var sessionGeneration = 0L
    private var returnedFromAccount = false
    init {
        repo.onSessionExpired = { revoked -> viewModelScope.launch { if (state.value.account != null && !repo.sessionValid()) endSession(expired = true, revoked = revoked) } }
        viewModelScope.launch {
            CatalogueObservers.changes.collect { if (state.value.account != null) refresh() }
        }
        viewModelScope.launch {
            repo.retryRevocations()
            if (repo.sessionValid()) {
                val stored = repo.sessionStore.read().optJSONObject("account")
                repo.accountId = stored?.optString("id")
                try {
                    val account = JSONObject(repo.request("/api/auth/me"))
                    signedIn(account)
                } catch (e: SessionExpired) { endSession(expired = true, revoked = e.revoked) }
                catch (_: Exception) { if (repo.sessionValid() && stored != null) signedIn(stored) else endSession(expired = true) }
            } else {
                val hadSession = repo.sessionStore.read().has("token")
                endSession(expired = hadSession || reminderStore.hasOwner())
            }
        }
        viewModelScope.launch { while (true) {
            delay(5_000)
            if (state.value.account != null && !repo.sessionValid()) endSession(expired = true)
            update { it.copy(clock = System.currentTimeMillis()) }
        } }
    }
    fun authPage(page: String) { update { it.copy(authPage = page, authError = null, authMessage = null, authFieldErrors = emptyMap()) } }
    fun authError(message: String?) { update { it.copy(authError = message, authBusy = false, authFieldErrors = if (message == null) emptyMap() else it.authFieldErrors) } }
    fun accountReturn() { returnedFromAccount = true; update { it.copy(page = "home", authPage = "signin", authError = null, authFieldErrors = emptyMap(), authMessage = "Returned from account access. Sign in after completing the steps in your browser.") } }
    fun loadPrivacy() { viewModelScope.launch {
        update { it.copy(privacyError = null) }
        try { val info = repo.operatorPrivacy(); update { it.copy(privacyInfo = info) } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { update { it.copy(privacyError = "Could not load the operator's contact and retention information. Check your connection and retry.") } }
    } }
    fun touchSession() {
        if (state.value.account == null) return
        if (!repo.sessionValid()) { endSession(expired = true); return }
        val data = repo.sessionStore.read()
        // Avoid a disk write for every tap while preserving the inactivity deadline.
        if (System.currentTimeMillis() - data.optLong("last_activity") > 1_000) {
            repo.sessionStore.update { it.put("last_activity", System.currentTimeMillis()) }
            reminderStore.expiry()
        }
        if (System.currentTimeMillis() - lastServerActivity > 60_000) {
            lastServerActivity = System.currentTimeMillis()
            userScope.launch { try { repo.request("/api/auth/me") } catch (_: Exception) { } }
        }
    }
    fun checkSession() {
        if (state.value.account == null) return
        if (!repo.sessionValid()) { endSession(expired = true); return }
        userScope.launch {
            repo.retryRevocations()
            try { repo.request("/api/auth/me") } catch (e: CancellationException) { throw e }
            catch (e: SessionExpired) { if (state.value.account != null) endSession(expired = true, revoked = e.revoked) } catch (_: Exception) { }
        }
    }
    private fun signedIn(account: JSONObject) {
        repo.accountId = account.getString("id")
        repo.sessionStore.update { it.put("account", account) }
        val resume = interrupted?.takeIf { interruptedAccount == account.getString("id") }
        mutable.value = (resume ?: UiState()).copy(account = account, authChecking = false, authBusy = false,
            authMessage = null, authError = null, loading = false, routeLoading = false,
            notice = if (resume != null) "Signed in again. Review your input before submitting." else null)
        if (resume == null) repo.sessionStore.read().optJSONObject("report_drafts")?.optJSONObject(account.getString("id"))?.let { draft ->
            mutable.value = mutable.value.copy(reportService = draft.optJSONObject("service")?.let(::Service),
                reportProvider = draft.optJSONObject("provider"), reportCategory = draft.optString("category"),
                reportDescription = draft.optString("description"), reportEvidence = draft.optString("evidence"),
                reportSubmissionId = draft.optString("submission_id").takeIf { it.isNotBlank() })
        }
        interrupted = null; interruptedAccount = null
        reminderStore.activate(account.getString("id"))
        PushRegistration.registerIfConfigured(getApplication(), repo, viewModelScope)
        refresh(); loadProviders()
    }
    private fun endSession(expired: Boolean = false, message: String? = null, revoked: Boolean = false) {
        sessionGeneration += 1
        val current = state.value
        if (expired && current.account != null) {
            clearLocation()
            interrupted = state.value.copy(page = if (current.page == "results") "home" else current.page)
            interruptedAccount = current.account.optString("id")
        } else if (!expired) { interrupted = null; interruptedAccount = null }
        userScope.cancel()
        userScope = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[Job]))
        coordinates = null; etaJob?.cancel(); searchJob?.cancel(); routeJob?.cancel()
        repo.accountId = null
        repo.sessionStore.endSession()
        if (expired && !revoked) reminderStore.sessionExpired() else reminderStore.deactivate()
        mutable.value = UiState(authChecking = false, authMessage = message ?: if (returnedFromAccount) "Returned from account access. Sign in after completing the steps in your browser." else null,
            authError = if (revoked) "Your session ended and reminders were cancelled. Sign in again."
                else if (expired && reminderStore.hasEnabled()) "Your session has expired. Generic reminders continue from cached schedules. Sign in to view service details."
                else if (expired) "Your session has expired. Sign in again." else null)
    }
    fun requestSignIn() { update { it.copy(authPage = "signin", authError = null) } }
    private fun requireAccount(): Boolean {
        if (state.value.account != null && repo.sessionValid()) return true
        if (state.value.account != null) endSession(expired = true)
        requestSignIn(); return false
    }
    fun authenticate(email: String, password: String) = authAction {
        val response = JSONObject(repo.request("/api/auth/login", "POST", JSONObject().put("email", email).put("password", password)))
        acceptSession(response)
    }
    private fun acceptSession(response: JSONObject) {
        repo.sessionStore.update { data -> data.put("token", response.getString("token"))
            .put("account", response.getJSONObject("account")).put("expires_at", response.getString("expires_at"))
            .put("last_activity", System.currentTimeMillis()) }
        signedIn(response.getJSONObject("account"))
    }
    private fun authAction(showFieldErrors: Boolean = true, action: suspend () -> Unit) {
        if (state.value.authBusy) return
        val generation = sessionGeneration
        val scope = if (state.value.account == null) viewModelScope else userScope
        update { it.copy(authBusy = true, authError = null, authMessage = null, authFieldErrors = emptyMap()) }
        scope.launch {
            try { action() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (generation == sessionGeneration) update { it.copy(authError = e.message ?: "Could not complete this request. Please retry.", authFieldErrors = if (showFieldErrors) (e as? ApiError)?.fieldErrors.orEmpty() else emptyMap()) } }
            finally { if (generation == sessionGeneration) update { it.copy(authBusy = false) } }
        }
    }
    fun register(name: String, email: String, password: String, confirmation: String) = authAction {
        val response = JSONObject(repo.request("/api/auth/register", "POST", JSONObject().put("display_name", name)
            .put("email", email).put("password", password).put("confirmation", confirmation)))
        update { it.copy(authPage = "signin", authMessage = response.getString("message")) }
    }
    fun requestEmail(email: String, verification: Boolean) = authAction {
        val response = JSONObject(repo.request("/api/auth/" + if (verification) "resend-verification" else "forgot-password",
            "POST", JSONObject().put("email", email)))
        update { it.copy(authMessage = response.getString("message")) }
    }
    fun googleSignIn(context: android.content.Context) = authAction(showFieldErrors = false) {
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) throw IllegalStateException("Google sign-in is not configured yet. Use email and password.")
        val challenge = JSONObject(repo.request("/api/auth/google/challenge", "POST"))
        val nonce = challenge.getString("nonce")
        val option = com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).setNonce(nonce).build()
        val manager = androidx.credentials.CredentialManager.create(context)
        val credential = try {
            manager.getCredential(context, androidx.credentials.GetCredentialRequest.Builder().addCredentialOption(option).build()).credential
        } catch (_: androidx.credentials.exceptions.GetCredentialCancellationException) { return@authAction }
        catch (_: androidx.credentials.exceptions.GetCredentialException) { throw IllegalStateException("Google sign-in could not start. Try again or use email and password.") }
        val google = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(credential.data)
        acceptSession(JSONObject(repo.request("/api/auth/google", "POST", JSONObject().put("id_token", google.idToken).put("nonce", nonce))))
    }
    fun signOut() {
        viewModelScope.launch {
            try { if (repo.sessionValid()) repo.unregisterPushDevice() } catch (_: Exception) { }
            repo.sessionStore.endSession(queueRevocation = true)
            endSession(message = "You have signed out.")
            repo.retryRevocations()
        }
    }
    fun updateName(name: String) = authAction {
        val account = JSONObject(repo.request("/api/auth/me", "PATCH", JSONObject().put("display_name", name)))
        repo.sessionStore.update { it.put("account", account) }
        update { it.copy(account = account, profileNameDraft = null, authMessage = "Display name updated.") }
    }
    fun changePassword(current: String, password: String, confirmation: String) = authAction {
        repo.request("/api/auth/change-password", "POST", JSONObject().put("current_password", current)
            .put("password", password).put("confirmation", confirmation))
        endSession(message = "Password changed. Sign in with your new password.")
    }
    fun update(transform: (UiState) -> UiState) {
        val old = mutable.value
        val transformed = transform(old)
        val reportEdited = old.reportCategory != transformed.reportCategory || old.reportDescription != transformed.reportDescription || old.reportEvidence != transformed.reportEvidence || old.reportService?.id != transformed.reportService?.id || old.reportProvider?.optString("id") != transformed.reportProvider?.optString("id")
        val next = if (reportEdited) transformed.copy(reportSubmissionId = null, reportFieldErrors = emptyMap()) else transformed
        val changed = old.origin != next.origin || old.radius != next.radius || old.searchDiets != next.searchDiets || old.type != next.type || old.openOnly != next.openOnly || old.wheelchair != next.wheelchair || old.preferred != next.preferred
        if (changed) etaJob?.cancel()
        mutable.value = if (changed) next.copy(results = emptyList(), totalMatches = 0, hasSearched = false, searchStatus = "idle", eta = emptyMap()) else next
        if (old.reportCategory != next.reportCategory || old.reportDescription != next.reportDescription ||
            old.reportEvidence != next.reportEvidence || old.reportService?.id != next.reportService?.id ||
            old.reportProvider?.optString("id") != next.reportProvider?.optString("id") || old.reportSubmissionId != next.reportSubmissionId) {
            val accountId = next.account?.optString("id")
            if (accountId != null) {
                draftSaveJob?.cancel()
                draftSaveJob = viewModelScope.launch {
                    if (next.reportCategory.isNotBlank() || next.reportDescription.isNotBlank() || next.reportEvidence.isNotBlank()) delay(300)
                    persistReportDraft(next, accountId)
                }
            }
        }
    }
    private fun persistReportDraft(snapshot: UiState, accountId: String) {
        repo.sessionStore.update { data ->
            val drafts = data.optJSONObject("report_drafts") ?: JSONObject()
            if (snapshot.reportCategory.isBlank() && snapshot.reportDescription.isBlank() && snapshot.reportEvidence.isBlank()) drafts.remove(accountId)
            else drafts.put(accountId, JSONObject().put("service", snapshot.reportService?.json?.let { source ->
                JSONObject(source.toString()).apply { listOf("distance_km", "travel_time", "explanations", "status_at", "requested_time").forEach(::remove) }
            }).put("provider", snapshot.reportProvider).put("category", snapshot.reportCategory)
                .put("description", snapshot.reportDescription).put("evidence", snapshot.reportEvidence).put("submission_id", snapshot.reportSubmissionId))
            data.put("report_drafts", drafts)
        }
    }
    fun error(message: String?) = update { it.copy(error = message, loading = false) }
    fun loadProviders() { userScope.launch {
        update { it.copy(providerLoading = true, providerError = null) }
        try {
            val (providers, offline) = repo.providers()
            update { it.copy(providers = providers, providerOffline = offline, providerLoading = false) }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { update { it.copy(providerLoading = false, providerError = "Provider directory unavailable. Try again when connected.") } }
    } }
    fun browseProviders() { update { it.copy(page = "providers", selectedProvider = null) }; loadProviders() }
    private fun navigationEntry(s: UiState) = NavigationEntry(s.page, s.tab, s.selected?.id, s.selectedProvider?.optString("id"))
    fun providerDetail(provider: JSONObject) { update { it.copy(informationReturnPage = it.page, page = "providerDetail", selectedProvider = provider, selected = null,
        navigation = if (it.page == "providerDetail" && it.selectedProvider?.optString("id") == provider.optString("id")) it.navigation else it.navigation + navigationEntry(it)) } }
    fun refresh() { userScope.launch {
        update { it.copy(catalogueLoading = true) }
        // Personal data is independent of the catalogue network request and its cache.
        try {
            val saved = repo.bookmarks()
            val diets = repo.diets()
            val receipts = repo.reportReceipts()
            update { it.copy(saved = saved, diets = diets, searchDiets = if (it.services.isEmpty() && it.searchStatus == "idle") diets else it.searchDiets,
                reportReceipts = receipts, reminders = reminderStore.times(), reminderTime = reminderStore.dailyTime(), notificationsAllowed = reminderStore.allowed()) }
        } catch (e: CancellationException) { throw e }
        catch (e: SessionExpired) { return@launch }
        catch (_: Exception) { error("Could not read your saved settings. Please retry.") }
        try {
            val (services, offline) = repo.all()
            reminderStore.updateServices(services)
            update { it.copy(services = services, offline = offline, lastUpdate = repo.lastUpdate,
                reminders = reminderStore.times(), reminderTime = reminderStore.dailyTime(), notificationsAllowed = reminderStore.allowed()) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error("Could not load support details. Your saved IDs and settings are kept. Check your connection and retry.") }
        finally { update { it.copy(catalogueLoading = false) } }
    } }
    fun tab(name: String) {
        if (state.value.page == "report" && state.value.loading) { update { it.copy(notice = "Submitting your report. Please wait…") }; return }
        if (state.value.page == "report" && hasReportDraft()) { update { it.copy(reportLeavePrompt = true, reportNextTab = name) }; return }
        if (name in listOf("Saved", "Preferences") && !requireAccount()) return
        if (state.value.tab == name && state.value.page == "home") return
        update { it.copy(tab = name, page = if (name == "Find" && it.hasSearched && it.searchStatus == "complete") "results" else "home", selected = null, selectedProvider = null, navigation = emptyList(), error = null) }
        if (name == "Saved") refresh()
    }
    fun back() {
        if (state.value.page == "report" && state.value.loading) { update { it.copy(notice = "Submitting your report. Please wait…") }; return }
        if (state.value.page == "report" && hasReportDraft()) { update { it.copy(reportLeavePrompt = true, reportNextTab = null) }; return }
        if (state.value.tab == "Map" && state.value.page == "home" && state.value.routeDestination != null) { closeRoute(); return }
        if (state.value.page in listOf("report", "confirmation")) { returnToInformation(); return }
        if (state.value.page == "results") { update { it.copy(page = "home", error = null) }; return }
        val entry = state.value.navigation.lastOrNull()
        update { current ->
            if (entry == null) current.copy(page = "home", selected = null, selectedProvider = null, reference = null, error = null)
            else current.copy(page = entry.page, tab = entry.tab, selected = entry.serviceId?.let { id -> (current.results + current.services).find { it.id == id } },
                selectedProvider = entry.providerId?.let { id -> current.providers.find { it.optString("id") == id } }, navigation = current.navigation.dropLast(1), reference = null, error = null)
        }
    }
    fun hasReportDraft() = state.value.let { it.reportCategory.isNotBlank() || it.reportDescription.isNotBlank() || it.reportEvidence.isNotBlank() }
    fun openReport() {
        if (!requireAccount()) return
        val current = state.value
        val target = current.selected?.id ?: current.selectedProvider?.optString("id")
        val draftTarget = current.reportService?.id ?: current.reportProvider?.optString("id")
        if (hasReportDraft() && target != null && target != draftTarget) {
            update { it.copy(reportTargetPrompt = true, pendingReportService = it.selected, pendingReportProvider = it.selectedProvider) }
            return
        }
        openReportDraft(hasReportDraft())
    }
    fun resumeReport() { if (requireAccount()) openReportDraft(true) }
    private fun openReportDraft(resume: Boolean) {
        update { it.copy(page = "report", reference = null, error = null,
            selected = if (resume) it.reportService else it.selected,
            selectedProvider = if (resume) it.reportProvider else it.selectedProvider,
            reportService = if (resume) it.reportService else it.selected,
            reportProvider = if (resume) it.reportProvider else it.selectedProvider,
            reportFieldErrors = emptyMap(), notice = if (resume) "Your unfinished report has been restored." else null) }
    }
    fun resolveReportTarget(resumeExisting: Boolean) {
        if (!resumeExisting) update { it.copy(selected = it.pendingReportService, selectedProvider = it.pendingReportProvider,
            reportCategory = "", reportDescription = "", reportEvidence = "", reportService = null, reportProvider = null, reportSubmissionId = null) }
        update { it.copy(reportTargetPrompt = false, pendingReportService = null, pendingReportProvider = null) }
        openReportDraft(resumeExisting)
    }
    fun cancelReport() { if (hasReportDraft()) update { it.copy(reportLeavePrompt = true, reportNextTab = null) } else returnToInformation() }
    fun leaveReport(discard: Boolean) {
        val nextTab = state.value.reportNextTab
        update { it.copy(reportLeavePrompt = false, reportNextTab = null,
            reportCategory = if (discard) "" else it.reportCategory,
            reportDescription = if (discard) "" else it.reportDescription,
            reportEvidence = if (discard) "" else it.reportEvidence,
            reportService = if (discard) null else it.reportService,
            reportProvider = if (discard) null else it.reportProvider,
            notice = if (discard) "Report draft discarded." else "Report draft kept until you submit or discard it.") }
        returnToInformation()
        if (nextTab != null) tab(nextTab)
    }
    fun returnToInformation() { update { it.copy(page = if (it.selectedProvider != null) "providerDetail" else "detail", reference = null, error = null) } }
    fun origin(text: String) { searchJob?.cancel(); routeJob?.cancel(); coordinates = null; update { it.copy(origin = text, originLatitude = null, originLongitude = null, deviceLatitude = null, deviceLongitude = null, startingAddress = null, startingLatitude = null, startingLongitude = null, candidates = emptyList(), loading = false, error = null, locationLoading = false, locationError = null, route = null, routeLoading = false, locationCleared = false) } }
    fun openOrganisation(match: OrganisationMatch) {
        searchJob?.cancel()
        update { it.copy(loading = false, candidates = emptyList(), error = null) }
        match.service?.let { detail(it) } ?: match.provider?.let { providerDetail(it) }
    }
    fun showOrganisations() { searchJob?.cancel(); update { it.copy(page = "nameResults", loading = false, candidates = emptyList(), error = null) } }
    fun search() {
        if (state.value.tab == "Map" && state.value.routeDestination != null) { searchRouteOrigin(); return }
        if (state.value.origin.isBlank()) { browseAllSupport(); return }
        // The primary action always means an address search. Name lookup is an explicit suggestion/action.
        searchAddress()
    }
    fun browseAllSupport() {
        searchJob?.cancel(); etaJob?.cancel(); routeJob?.cancel()
        coordinates = null
        update { it.copy(page = "allSupport", loading = true, error = null, selected = null, selectedProvider = null,
            candidates = emptyList(), route = null, eta = emptyMap(), results = emptyList(), hasSearched = false,
            totalMatches = 0, originLatitude = null, originLongitude = null, deviceLatitude = null, deviceLongitude = null,
             startingAddress = null, startingLatitude = null, startingLongitude = null, routeDestination = null, locationCleared = false, searchStatus = "idle") }
        searchJob = userScope.launch {
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
    fun locationStarted() {
        searchJob?.cancel(); routeJob?.cancel(); etaJob?.cancel(); coordinates = null
        update { it.copy(origin = "", originLatitude = null, originLongitude = null, deviceLatitude = null,
            deviceLongitude = null, startingAddress = null, startingLatitude = null, startingLongitude = null, locationLoading = true, locationError = null, error = null, loading = false, route = null, routeLoading = false) }
    }
    fun locationFailed(message: String) { update { it.copy(locationLoading = false, locationError = message) } }
    fun location(lat: Double, lon: Double) {
        if (lat !in 1.13..1.48 || lon !in 103.59..104.1) { locationFailed("Your device is outside Singapore. Enter a Singapore starting address."); return }
        searchJob?.cancel(); routeJob?.cancel()
        coordinates = lat to lon
        update { it.copy(origin = "Your location", originLatitude = lat, originLongitude = lon,
             deviceLatitude = lat, deviceLongitude = lon, startingAddress = null, startingLatitude = null, startingLongitude = null, locationLoading = false, locationError = null, error = null, locationCleared = false) }
        if (state.value.tab == "Map") search()
    }
    fun searchMapArea(lat: Double, lon: Double) {
        if (lat !in 1.13..1.48 || lon !in 103.59..104.1) { error("Move the map to Singapore to search this area."); return }
        coordinates = lat to lon
        update { it.copy(origin = "Selected map area", originLatitude = lat, originLongitude = lon, deviceLatitude = null,
            deviceLongitude = null, startingAddress = null, startingLatitude = null, startingLongitude = null, hasSearched = false, results = emptyList()) }
        search()
    }
    fun clearLocation() {
        val current = state.value
        val hadContext = current.origin.isNotBlank() || current.originLatitude != null || current.hasSearched || current.route != null || current.locationLoading
        coordinates = null
        etaJob?.cancel()
        searchJob?.cancel()
        routeJob?.cancel()
        fun publicSelection(service: Service?) = service?.let { Service(JSONObject(it.json.toString()).apply { listOf("distance_km", "travel_time", "explanations").forEach(::remove) }) }
        update { it.copy(origin = "", originLatitude = null, originLongitude = null, deviceLatitude = null, deviceLongitude = null, startingAddress = null, startingLatitude = null, startingLongitude = null, locationLoading = false, locationError = null, hasSearched = false, results = emptyList(), totalMatches = 0, route = null,
            routeDestination = publicSelection(it.routeDestination), selected = publicSelection(it.selected), reportService = publicSelection(it.reportService), routeLoading = false, candidates = emptyList(), eta = emptyMap(), loading = if (it.page == "report") it.loading else false,
            page = if (it.page == "results") "home" else it.page, searchStatus = if (hadContext) "cleared" else it.searchStatus, locationCleared = it.locationCleared || hadContext,
            navigation = it.navigation.map { entry -> if (entry.page == "results") entry.copy(page = "home") else entry }) }
    }
    fun applySearchFilters(draft: UiState) {
        searchJob?.cancel(); etaJob?.cancel()
        update { it.copy(radius = draft.radius, type = draft.type, searchDiets = draft.searchDiets,
            preferred = draft.preferred, openOnly = draft.openOnly || draft.preferred.isNotBlank(), wheelchair = draft.wheelchair,
            filtersRequested = false, page = if (it.page == "results") "home" else it.page, error = null) }
    }
    fun toggleDiet(diet: String) {
        val account = state.value.account?.optString("id") ?: return
        val diets = state.value.diets.let { if (diet in it) it - diet else it + diet }
        update { it.copy(diets = diets, searchDiets = diets, notice = "Dietary preferences saved and applied to your next address search.") }
        userScope.launch { repo.saveDiets(diets, account) }
    }
    fun save(service: Service) {
        removeOrSave(service.id)
    }
    fun removeOrSave(serviceId: String) {
        if (!requireAccount()) return
        val account = state.value.account?.optString("id") ?: return
        val ids = state.value.saved.let { if (serviceId in it) it - serviceId else it + serviceId }
        val removed = serviceId !in ids
        val reminderSnapshot = if (removed) reminderStore.snapshot(serviceId) else null
        update { it.copy(saved = ids, undoBookmarkId = if (removed) serviceId else null, undoBookmarkReminder = reminderSnapshot,
            notice = if (removed) null else "Saved on this device.") }
        if (serviceId !in ids) { reminderStore.remove(serviceId); update { it.copy(reminders = reminderStore.times()) } }
        userScope.launch { repo.saveBookmarks(ids, account) }
    }
    fun undoBookmark() {
        if (!requireAccount()) return
        val s = state.value
        val id = s.undoBookmarkId ?: return
        val account = s.account?.optString("id") ?: return
        reminderStore.restore(id, s.undoBookmarkReminder)
        val ids = s.saved + id
        update { it.copy(saved = ids, reminders = reminderStore.times(), undoBookmarkId = null, undoBookmarkReminder = null, notice = "Saved listing and its reminder setting restored.") }
        userScope.launch { repo.saveBookmarks(ids, account) }
    }
    fun dismissBookmarkUndo() { update { it.copy(undoBookmarkId = null, undoBookmarkReminder = null) } }
    fun clearLocal() { userScope.launch { repo.clearLocal(); reminderStore.clear(); clearLocation(); update { it.copy(saved = emptySet(), diets = emptySet(), searchDiets = emptySet(), reminders = emptyMap(), undoBookmarkId = null, undoBookmarkReminder = null, reminderTime = reminderStore.dailyTime(), notice = "Local bookmarks, reminder settings and dietary preferences cleared.") } } }
    fun notificationState() { reminderStore.resume(); update { it.copy(notificationsAllowed = reminderStore.allowed(), clock = System.currentTimeMillis()) } }
    fun reminderTime(time: String) {
        if (!requireAccount()) return
        reminderStore.setDailyTime(time)
        update { it.copy(reminderTime = reminderStore.dailyTime(), reminders = reminderStore.times(), notice = "All reminders for this account now use $time Singapore time.") }
    }
    fun reminder(service: Service, enabled: Boolean) {
        if (!requireAccount()) return
        reminderStore.set(service, enabled)
        update { it.copy(reminders = reminderStore.times(), notificationsAllowed = reminderStore.allowed(),
            notice = if (!enabled) "Reminder disabled."
                else if (!reminderStore.allowed()) "Reminder enabled. Notifications are blocked; your preference is kept. Allow notifications in Android settings."
                else if (reminderStore.nextReminder(service).nextAt == null) "Reminder preference saved. No recorded session qualifies at ${reminderStore.dailyTime()}; change the shared time below."
                else "Reminder enabled around ${reminderStore.dailyTime()} Singapore time when a confirmed session qualifies. Food availability is not guaranteed.") }
    }
    fun searchAddress() {
        if (state.value.tab == "Map" && state.value.routeDestination != null) { searchRouteOrigin(); return }
        searchJob?.cancel()
        searchJob = userScope.launch {
        update { it.copy(loading = true, error = null, eta = emptyMap(), searchStatus = "loading", locationCleared = false) }
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
                .put("diets", JSONArray(s.searchDiets.toList())).put("support_type", s.type).put("open_only", s.openOnly).put("wheelchair", s.wheelchair)
            if (s.preferred.isNotBlank()) {
                if (!LocalDateTime.parse(s.preferred).isAfter(LocalDateTime.now(ZoneId.of("Asia/Singapore"))))
                    throw IllegalArgumentException("Choose a future preferred time, or use current time.")
                val parsed = runCatching { LocalDateTime.parse(s.preferred).atZone(ZoneId.of("Asia/Singapore")).toOffsetDateTime().toString() }
                    .getOrElse { throw IllegalArgumentException("Use YYYY-MM-DDTHH:MM for your preferred Singapore time.") }
                body.put("preferred_time", parsed)
            }
            val response = JSONObject(repo.request("/api/search", "POST", body))
            if (coordinates != (lat to lon) || SearchSummary(state.value) != SearchSummary(s)) {
                update { it.copy(loading = false) }; return@launch
            }
            val results = response.getJSONArray("services").objects().map { row ->
                if (s.preferred.isNotBlank()) row.put("status_at", s.preferred).put("requested_time", s.preferred)
                Service(row)
            }
            update { it.copy(results = results, totalMatches = response.optInt("total", results.size), hasSearched = true, searchStatus = "complete", resultListIndex = 0, resultListOffset = 0, loading = false, page = if (it.tab == "Map") "home" else "results", offline = false, lastUpdate = Instant.now().toString()) }
            estimate(results)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { update { it.copy(searchStatus = "error", loading = false, error = e.message ?: "Search unavailable. Your input has been kept.") } }
    } }
    fun chooseOrigin(candidate: JSONObject, submit: Boolean = true) {
        coordinates = candidate.getDouble("latitude") to candidate.getDouble("longitude")
        update { it.copy(origin = candidate.getString("address"), originLatitude = coordinates?.first, originLongitude = coordinates?.second, startingAddress = candidate.getString("address"), startingLatitude = coordinates?.first, startingLongitude = coordinates?.second, deviceLatitude = null, deviceLongitude = null, locationLoading = false, candidates = emptyList(), error = null) }
        if (submit) { if (state.value.routeDestination != null && state.value.tab == "Map") route(state.value.routeMode) else searchAddress() }
    }
    private fun estimate(services: List<Service>) {
        etaJob?.cancel()
        etaJob = userScope.launch {
            val semaphore = Semaphore(3)
            services.map { service -> async {
                semaphore.withPermit {
                    val result = try { getRoute(service, "walk") } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                    val label = if (result?.optBoolean("available") == true) "${kotlin.math.ceil(result.getDouble("duration_minutes")).toInt()} min walk" else "Travel time unavailable"
                    update { it.copy(eta = it.eta + (service.id to label)) }
                }
            } }.awaitAll()
        }
    }
    fun detail(service: Service) {
        val keepRoute = state.value.routeDestination?.id == service.id
        if (!keepRoute) routeJob?.cancel()
        val retry = state.value.page == "detail" && state.value.selected?.id == service.id
        update { it.copy(informationReturnPage = if (retry) it.informationReturnPage else it.page, selected = service, selectedProvider = null, page = "detail", route = if (keepRoute) it.route else null, routeDestination = if (keepRoute) it.routeDestination else null, routeLoading = keepRoute && it.routeLoading, error = null,
            detailLoading = true, detailError = null, detailWithdrawn = false, navigation = if (retry) it.navigation else it.navigation + navigationEntry(it)) }
        userScope.launch {
            try {
                val requestedAt = service.json.optString("requested_time").takeIf { value ->
                    runCatching { java.time.OffsetDateTime.parse(value).toInstant().isAfter(Instant.now()) }
                        .getOrElse { runCatching { LocalDateTime.parse(value).atZone(ZoneId.of("Asia/Singapore")).toInstant().isAfter(Instant.now()) }.getOrDefault(false) }
                }
                val timestamp = requestedAt?.let { value -> runCatching { java.time.OffsetDateTime.parse(value).toString() }.getOrElse { LocalDateTime.parse(value).atZone(ZoneId.of("Asia/Singapore")).toOffsetDateTime().toString() } }
                val suffix = timestamp?.let { "?preferred_time=" + java.net.URLEncoder.encode(it, "UTF-8") }.orEmpty()
                val fresh = Service(JSONObject(repo.request("/api/services/${service.id}$suffix")))
                if (timestamp != null) fresh.json.put("requested_time", timestamp)
                if (!service.distance.isNaN()) fresh.json.put("distance_km", service.distance)
                fresh.json.put("explanations", service.json.optJSONArray("explanations") ?: JSONArray())
                update { if (it.selected?.id == fresh.id) it.copy(selected = fresh, detailLoading = false) else it }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                update { if (it.selected?.id == service.id) it.copy(detailLoading = false, detailWithdrawn = (e as? ApiError)?.statusCode == 404,
                    detailError = if ((e as? ApiError)?.statusCode == 404) "This listing is no longer published. Saved information may be outdated."
                        else "Could not check the latest listing. Showing the information already loaded. Retry when connected.") else it }
            }
        }
    }
    fun openReminder(serviceId: String, owningAccountId: String?) { userScope.launch {
        if (owningAccountId.isNullOrBlank() || state.value.account?.optString("id") != owningAccountId || !repo.sessionValid()) {
            error("This reminder belongs to another account. Sign in with the account that enabled it to open its details.")
            return@launch
        }
        try { detail(Service(JSONObject(repo.request("/api/services/$serviceId")))) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error("This reminder’s service could not be loaded. Open Saved to check the latest approved information.") }
    } }
    private suspend fun getRoute(service: Service, mode: String): JSONObject {
        val origin = coordinates ?: throw IllegalArgumentException("Search an address on Find or Map before requesting directions.")
        return JSONObject(repo.request("/api/routes", "POST", JSONObject().put("latitude", origin.first)
            .put("longitude", origin.second).put("service_id", service.id).put("mode", mode)))
    }
    fun closeRoute() { routeJob?.cancel(); update { it.copy(routeDestination = null, route = null, routeLoading = false, candidates = emptyList(), error = null) } }
    fun searchRouteOrigin() {
        routeJob?.cancel(); searchJob?.cancel(); etaJob?.cancel()
        val destination = state.value.routeDestination ?: return
        val mode = state.value.routeMode
        if (coordinates != null) { route(mode); return }
        routeJob = userScope.launch {
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
            coordinates = snapshot.startingLatitude?.let { lat -> snapshot.startingLongitude?.let { lat to it } }
                ?: snapshot.deviceLatitude?.let { lat -> snapshot.deviceLongitude?.let { lat to it } }
            update { it.copy(origin = snapshot.startingAddress ?: if (coordinates != null) "Your location" else "", originLatitude = coordinates?.first,
                originLongitude = coordinates?.second, locationError = null) }
        }
        update { it.copy(tab = "Map", page = "home", origin = if (coordinates == null && it.routeDestination == null) "" else it.origin, routeDestination = service, routeMode = mode,
            route = null, routeLoading = coordinates != null, loading = false, error = null, candidates = emptyList()) }
        if (coordinates == null) return
        val requestOrigin = coordinates
        routeJob = userScope.launch {
            try {
                val route = getRoute(service, mode)
                if (coordinates == requestOrigin && state.value.routeDestination?.id == service.id && state.value.routeMode == mode)
                    update { it.copy(route = route, routeLoading = false) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { update { it.copy(routeLoading = false, route = JSONObject().put("available", false).put("message", "Route unavailable. Check your connection and try again.")) } }
        }
    }
    fun report(category: String, description: String, evidence: String) { userScope.launch {
        if (!requireAccount()) return@launch
        if (state.value.loading) return@launch
        val submissionId = state.value.reportSubmissionId ?: UUID.randomUUID().toString()
        update { it.copy(loading = true, error = null, notice = null, reportFieldErrors = emptyMap(), reportSubmissionId = submissionId) }
        try {
            val snapshot = state.value
            // Persist the retry identity before any request can reach the server, including process-death recovery.
            draftSaveJob?.cancel()
            persistReportDraft(snapshot, snapshot.account!!.getString("id"))
            val targetId = snapshot.reportService?.id ?: snapshot.reportProvider?.getString("id") ?: throw IllegalStateException("Open a listing before reporting incorrect information.")
            val targetKind = if (snapshot.reportProvider != null) "provider" else "service"
            val body = JSONObject().put("service_id", targetId).put("target_kind", targetKind).put("category", category).put("description", description.trim()).put("evidence", evidence.trim()).put("submission_id", submissionId)
            val response = JSONObject(repo.request("/api/reports", "POST", body))
            val receipt = JSONObject().put("reference", response.getString("reference")).put("target_name", snapshot.reportService?.name ?: snapshot.reportProvider?.optString("name"))
                .put("target_kind", targetKind).put("target_id", targetId).put("category", category).put("submitted_at", response.optString("submitted_at").ifBlank { Instant.now().toString() })
            repo.saveReportReceipt(receipt)
            val receipts = repo.reportReceipts()
            update { it.copy(reference = response.getString("reference"), page = "confirmation", loading = false,
                reportCategory = "", reportDescription = "", reportEvidence = "", reportService = null, reportProvider = null,
                reportSubmissionId = null, reportReceipts = receipts) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { update { it.copy(loading = false, reportFieldErrors = (e as? ApiError)?.fieldErrors.orEmpty(),
            error = (e.message ?: "Could not confirm submission.") + " Your draft and retry reference are kept. Retry unchanged to avoid a duplicate report.") } }
    } }
}
