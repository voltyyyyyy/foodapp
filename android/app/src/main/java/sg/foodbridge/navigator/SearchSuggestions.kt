package sg.foodbridge.navigator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.util.Locale

data class OrganisationMatch(val id: String, val name: String, val service: Service? = null, val provider: JSONObject? = null)
private fun searchWords(value: String) = value.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
private fun supportEntries(s: UiState): List<OrganisationMatch> =
    s.providers.filter { it.optBoolean("active", true) }.map { OrganisationMatch(it.getString("id"), it.getString("name"), provider = it) } +
        s.services.filter { it.json.optBoolean("active", true) }.map { OrganisationMatch(it.id, it.name, service = it) }
fun organisationMatches(s: UiState): List<OrganisationMatch> {
    val query = searchWords(s.origin)
    if (query.length < 2) return emptyList()
    val tokens = query.split(' ').filter { it.isNotBlank() }
    val entries = supportEntries(s)
    return entries.filter { entry ->
        val names = searchWords(entry.name + " " + (entry.service?.organisationName ?: ""))
        tokens.all { it in names }
    }.sortedWith(compareBy<OrganisationMatch> { if (searchWords(it.name) == query) 0 else if (searchWords(it.name).startsWith(query)) 1 else 2 }.thenBy { it.name.lowercase(Locale.ROOT) }.thenBy { it.id })
}

@Composable private fun OrganisationMatchRow(match: OrganisationMatch, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(if (match.service != null) Icons.Outlined.LocationOn else Icons.Outlined.Groups, null, Modifier.size(22.dp), tint = Teal)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(match.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(if (match.service != null) "${match.service.type} · Service location${if (match.service.demo) " · Fictional demo" else ""}" else "Provider · Contact and programme information", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Outlined.ChevronRight, "View information", Modifier.size(20.dp), tint = Muted)
    }
}

@Composable fun SearchSuggestions(s: UiState, model: AppViewModel, onMatch: ((OrganisationMatch) -> Unit)? = null, onSelect: () -> Unit = {}) {
    if (s.origin.trim().length < 2 || s.originLatitude != null) return
    val matches = organisationMatches(s)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (matches.isNotEmpty()) {
            Text("Organisations & services", color = Muted, style = MaterialTheme.typography.labelMedium)
            matches.take(4).forEach { match -> OrganisationMatchRow(match) { onSelect(); if (onMatch != null) onMatch(match) else model.openOrganisation(match) } }
            if (matches.size > 4) TextButton(onClick = { onSelect(); model.showOrganisations() }) { Text("View all ${matches.size} name matches") }
        }
        TextButton(onClick = { onSelect(); model.searchAddress() }, enabled = !s.loading && s.origin.trim().length >= 3) {
            Icon(Icons.Outlined.LocationOn, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Search this as an address")
        }
        if (s.providerLoading) Text("Loading provider names…", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable fun OrganisationResultsScreen(s: UiState, model: AppViewModel, browseAll: Boolean = false) {
    val matches = if (browseAll) supportEntries(s).sortedWith(compareBy<OrganisationMatch> { it.name.lowercase(Locale.ROOT) }.thenBy { it.id }) else organisationMatches(s)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (browseAll) "All food support" else "Name matches", style = MaterialTheme.typography.headlineMedium)
                if (browseAll) {
                    Text("${matches.count { it.provider != null }} providers · ${matches.count { it.service != null }} service locations", color = Muted)
                    Text("All active listings, A–Z. Your search filters are not applied in this view. Enter an address to find your nearest matching services.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { model.update { it.copy(page = "home") } }) { Icon(Icons.Outlined.LocationOn, null); Spacer(Modifier.width(8.dp)); Text("Search by address or name") }
                    if (s.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (s.offline || s.providerOffline) Text("Includes saved offline listings", color = Muted, style = MaterialTheme.typography.bodySmall)
                    if (s.error != null) TextButton(onClick = model::browseAllSupport) { Text("Retry loading listings") }
                } else {
                    Text("For “${s.origin}”", color = Muted)
                    Text("${matches.size} organisations and service locations. Name matches have not been checked against your dietary or accessibility filters.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = model::searchAddress) { Icon(Icons.Outlined.LocationOn, null); Spacer(Modifier.width(8.dp)); Text("Search this as an address instead") }
                }
            }
        }
        items(matches, key = { it.id }) { match -> OrganisationMatchRow(match) { model.openOrganisation(match) }; HorizontalDivider() }
        if (matches.isEmpty() && !s.loading) item { Text(if (browseAll) "No support listings are available yet. Try refreshing when connected." else "No name matches. Try a shorter name or browse the provider directory."); TextButton(onClick = if (browseAll) model::browseAllSupport else model::browseProviders) { Text(if (browseAll) "Refresh listings" else "Browse providers") } }
    }
}
