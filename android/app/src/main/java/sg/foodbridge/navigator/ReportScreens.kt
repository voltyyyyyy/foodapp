package sg.foodbridge.navigator

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URI

private val reportCategories = listOf("Address", "Contact", "Hours", "Service details", "Permanently closed", "Food unavailable", "Other")

private fun reportValidation(category: String, description: String, evidence: String): Map<String, String> = buildMap {
    if (category !in reportCategories) put("category", "Choose what is incorrect.")
    if (description.isBlank()) put("description", "Describe what needs correcting.")
    else if (description.length > 1000) put("description", "Shorten the description to 1,000 characters. Your pasted text is kept below.")
    if (evidence.length > 1000) put("evidence", "Shorten the link to 1,000 characters. Your pasted text is kept below.")
    else if (evidence.isNotBlank() && runCatching {
        val url = URI(evidence.trim())
        url.scheme in listOf("http", "https") && !url.host.isNullOrBlank()
    }.getOrDefault(false).not()) put("evidence", "Use a complete public website link beginning with https:// or http://, or leave this blank.")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun ReportScreen(s: UiState, model: AppViewModel) {
    var categoryMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val categoryView = remember { BringIntoViewRequester() }
    val descriptionView = remember { BringIntoViewRequester() }
    val evidenceView = remember { BringIntoViewRequester() }
    val descriptionFocus = remember { FocusRequester() }
    val evidenceFocus = remember { FocusRequester() }
    val immediate = reportValidation(s.reportCategory, s.reportDescription, s.reportEvidence)
    val errors = s.reportFieldErrors + immediate.filterKeys { key ->
        (key == "description" && s.reportDescription.length > 1000) ||
            (key == "evidence" && s.reportEvidence.length > 1000)
    }
    LaunchedEffect(s.reportFieldErrors) {
        when {
            "category" in s.reportFieldErrors -> categoryView.bringIntoView()
            "description" in s.reportFieldErrors -> { descriptionFocus.requestFocus(); descriptionView.bringIntoView() }
            "evidence" in s.reportFieldErrors -> { evidenceFocus.requestFocus(); evidenceView.bringIntoView() }
        }
    }
    Page {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Report an issue", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text("About: ${s.reportService?.name ?: s.reportProvider?.optString("name") ?: s.selected?.name ?: s.selectedProvider?.optString("name").orEmpty()}", color = Teal, fontWeight = FontWeight.SemiBold)
            Text("Linked to your account. Leave out personal details. Staff review reports before changing listings.", color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        if (errors.isNotEmpty()) Text("Check the highlighted fields. Your report has not been sent.", color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.bringIntoViewRequester(categoryView), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("What is incorrect? (required)", fontWeight = FontWeight.SemiBold)
                Box {
                    OutlinedButton(onClick = { categoryMenu = true }, enabled = !s.loading,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(12.dp)) {
                        Text(reportCategoryLabel(s.reportCategory).ifBlank { "Choose a category" }, Modifier.weight(1f)); Icon(Icons.Outlined.ExpandMore, null)
                    }
                    DropdownMenu(expanded = categoryMenu && !s.loading, onDismissRequest = { categoryMenu = false }) {
                        reportCategories.forEach { value ->
                            DropdownMenuItem(text = { Text(reportCategoryLabel(value)) }, onClick = {
                                model.update { it.copy(reportCategory = value, reportFieldErrors = it.reportFieldErrors - "category") }; categoryMenu = false
                            })
                        }
                    }
                }
                errors["category"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            OutlinedTextField(s.reportDescription, { value ->
                model.update { it.copy(reportDescription = value, reportFieldErrors = it.reportFieldErrors - "description") }
            }, enabled = !s.loading, label = { Text("Describe the issue (required)") }, isError = "description" in errors,
                supportingText = { Text(errors["description"] ?: "${s.reportDescription.length} / 1000 characters. Explain what needs changing.") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp).focusRequester(descriptionFocus).bringIntoViewRequester(descriptionView)
                    .onFocusChanged { if (it.isFocused) scope.launch { descriptionView.bringIntoView() } }, minLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { evidenceFocus.requestFocus() }))
            OutlinedTextField(s.reportEvidence, { value ->
                model.update { it.copy(reportEvidence = value, reportFieldErrors = it.reportFieldErrors - "evidence") }
            }, enabled = !s.loading, label = { Text("Evidence link (optional)") }, isError = "evidence" in errors,
                supportingText = { Text(errors["evidence"] ?: "${s.reportEvidence.length} / 1000 characters. Use a public https:// or http:// link.") },
                modifier = Modifier.fillMaxWidth().focusRequester(evidenceFocus).bringIntoViewRequester(evidenceView)
                    .onFocusChanged { if (it.isFocused) scope.launch { evidenceView.bringIntoView() } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }))
            if (s.loading) Text("Sending report… Keep this screen open while FoodBridge confirms your reference.", color = Teal,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Primary(if (s.loading) "Sending report…" else "Submit report", Icons.Outlined.Send, !s.loading) {
                val validation = reportValidation(s.reportCategory, s.reportDescription, s.reportEvidence)
                if (validation.isNotEmpty()) model.update { it.copy(reportFieldErrors = validation) }
                else { keyboard?.hide(); model.report(s.reportCategory, s.reportDescription, s.reportEvidence.trim()) }
            }
            OutlinedButton(onClick = model::cancelReport, enabled = !s.loading, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cancel report") }
        }
        // The shared Page handles the IME inset. Extra scroll space lets the last actions clear it.
        Spacer(Modifier.height(32.dp))
    }
}

@Composable private fun CopyReference(reference: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(reference) { mutableStateOf(false) }
    SelectionContainer { Text(reference, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
    TextButton(onClick = { clipboard.setText(AnnotatedString(reference)); copied = true }) {
        Icon(Icons.Outlined.ContentCopy, null); Spacer(Modifier.width(8.dp)); Text(if (copied) "Reference copied" else "Copy reference")
    }
}

@Composable private fun ReportReceipt(receipt: JSONObject) {
    Panel(Mint) {
        Text(receipt.optString("target_name").ifBlank { "Reported listing" }, fontWeight = FontWeight.SemiBold)
        Text(reportCategoryLabel(receipt.optString("category")), color = Muted)
        Text("Submitted ${formatTime(receipt.optString("submitted_at"))}", color = Muted, style = MaterialTheme.typography.bodySmall)
        Eyebrow("Report reference")
        CopyReference(receipt.optString("reference"))
    }
}

@Composable fun Confirmation(s: UiState, model: AppViewModel) { Page {
    Icon(Icons.Outlined.CheckCircle, null, Modifier.size(48.dp), tint = Teal)
    Text("Report sent", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    Text("Staff will review it before changing the listing. This receipt confirms submission; a review outcome is not available here.", color = Muted)
    val receipt = s.reportReceipts.firstOrNull { it.optString("reference") == s.reference }
    if (receipt != null) ReportReceipt(receipt)
    else Panel(Mint) { Eyebrow("Your report reference"); CopyReference(s.reference.orEmpty()) }
    val providerReport = receipt?.optString("target_kind")?.let { it == "provider" } ?: (s.selectedProvider != null)
    Primary(if (providerReport) "Back to provider" else "Back to service", Icons.Outlined.ArrowBack) { model.returnToInformation() }
    TextButton(onClick = { model.update { it.copy(page = "reportHistory") } }) { Text("View your report receipts") }
} }

@Composable fun ReportHistoryScreen(s: UiState, model: AppViewModel) { Page {
    Text("Your report receipts", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    Text("Submission receipts saved privately for this account on this device. They do not show staff review status.", color = Muted)
    if (s.reportReceipts.isEmpty()) Text("No report receipts saved on this device yet.", color = Muted)
    s.reportReceipts.forEach { ReportReceipt(it) }
    OutlinedButton(onClick = { model.tab("Saved") }, modifier = Modifier.fillMaxWidth()) { Text("Back to Saved") }
} }

/** A single bounded scroll container keeps all three choices reachable with large text. */
@Composable private fun ReportChoiceDialog(title: String, detail: String, onDismiss: () -> Unit, actions: @Composable ColumnScope.() -> Unit) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp).heightIn(max = maxHeight)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(detail, color = Muted)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), content = actions)
            }
        }
    }
}

@Composable fun LeaveReportDialog(onKeep: () -> Unit, onDiscard: () -> Unit, onContinue: () -> Unit) {
    ReportChoiceDialog("Leave this report?", "Keep your draft for this account on this device, or discard the unsent text.", onContinue) {
        Button(onClick = onKeep, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Keep draft and leave") }
        OutlinedButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Discard draft and leave") }
        TextButton(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Continue editing") }
    }
}

@Composable fun ReportTargetDialog(s: UiState, model: AppViewModel) {
    val previous = s.reportService?.name ?: s.reportProvider?.optString("name").orEmpty()
    val requested = s.pendingReportService?.name ?: s.pendingReportProvider?.optString("name").orEmpty()
    val dismiss = { model.update { it.copy(reportTargetPrompt = false, pendingReportService = null, pendingReportProvider = null) } }
    ReportChoiceDialog("You have an unfinished report", "Resume your report for $previous, or discard it and start a report for $requested. These are different listings.", dismiss) {
        Button(onClick = { model.resolveReportTarget(true) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Resume report for $previous") }
        OutlinedButton(onClick = { model.resolveReportTarget(false) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Discard draft and report $requested") }
        TextButton(onClick = dismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cancel") }
    }
}
