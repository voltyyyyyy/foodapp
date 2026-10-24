package sg.foodbridge.navigator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable private fun AccountField(label: String, value: String, onChange: (String) -> Unit,
    enabled: Boolean = true, error: String? = null, firstError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text, imeAction: ImeAction = ImeAction.Next, focusTrigger: String? = null) {
    val focus = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    LaunchedEffect(focusTrigger, enabled) { if (firstError && error != null && focusTrigger != null && enabled) requester.requestFocus() }
    OutlinedTextField(value, onChange, label = { Text(label) }, enabled = enabled, singleLine = true,
        isError = error != null, supportingText = error?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth().focusRequester(requester), shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }, onDone = { focus.clearFocus() }))
}

@Composable fun PasswordField(label: String, value: String, onChange: (String) -> Unit, enabled: Boolean = true,
    error: String? = null, firstError: Boolean = false, imeAction: ImeAction = ImeAction.Next, focusTrigger: String? = null) {
    var visible by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    LaunchedEffect(focusTrigger, enabled) { if (firstError && error != null && focusTrigger != null && enabled) requester.requestFocus() }
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, enabled = enabled,
        isError = error != null, supportingText = error?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth().focusRequester(requester), shape = RoundedCornerShape(14.dp),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }, onDone = { focus.clearFocus() }),
        trailingIcon = { IconButton(onClick = { visible = !visible }, enabled = enabled) {
            Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "Hide password" else "Show password")
        } })
}

@Composable fun AccountEntryScreen(s: UiState, model: AppViewModel, onHelp: () -> Unit = {}, onPrivacy: () -> Unit = {}) {
    val context = LocalContext.current
    var email by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    // Passwords deliberately remain only in memory and are never placed in saved instance state.
    var password by remember(s.authPage) { mutableStateOf("") }
    var confirmation by remember(s.authPage) { mutableStateOf("") }
    val firstError = listOf("display_name", "email", "password", "confirmation").firstOrNull { s.authFieldErrors.containsKey(it) }
    fun changed(field: String) { model.update { it.copy(authFieldErrors = it.authFieldErrors - field) } }
    val registration = s.authPage == "register"
    val recovery = s.authPage in listOf("forgot", "resend")
    BackHandler(s.authPage != "signin" && !s.authBusy) { model.authPage("signin") }
    // Reset the shared form scroll position when switching between sign-in and account-access pages.
    key(s.authPage) { Surface(color = Canvas, modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Page {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Spa, null, tint = Teal)
                Text("foodbridge", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(when (s.authPage) { "register" -> "Create an account"; "forgot" -> "Reset your password"; "resend" -> "Verify your email"; else -> "Sign in" }, style = MaterialTheme.typography.headlineMedium)
                Text(when (s.authPage) { "register" -> "Find and save food support. Verify your email, then sign in."; "forgot" -> "We’ll email you a link to choose a new password."; "resend" -> "Open the link in your email, then return here to sign in."; else -> "Sign in to find and save food support." }, color = Muted)
            }
            s.authError?.let { Banner(it, Icons.Outlined.Info, Peach) }
            s.authMessage?.let { Banner(it, Icons.Outlined.MarkEmailRead, Mint) }
            if (s.authMessage != null && s.account == null && (recovery || s.authMessage.contains("email", ignoreCase = true) || s.authMessage.contains("browser", ignoreCase = true))) Text("Finished in your browser? Return here to sign in.", color = Muted)
            if (s.authBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (registration) AccountField("Display name", name, { name = it; changed("display_name") }, !s.authBusy,
                    s.authFieldErrors["display_name"], firstError == "display_name", focusTrigger = s.authError)
                AccountField("Email address", email, { email = it; changed("email") }, !s.authBusy,
                    s.authFieldErrors["email"], firstError == "email", KeyboardType.Email, if (recovery) ImeAction.Done else ImeAction.Next, focusTrigger = s.authError)
                if (!recovery) {
                    PasswordField("Password", password, { password = it; changed("password") }, !s.authBusy,
                        s.authFieldErrors["password"], firstError == "password", if (registration) ImeAction.Next else ImeAction.Done, focusTrigger = s.authError)
                    if (registration) {
                        PasswordField("Confirm password", confirmation, { confirmation = it; changed("confirmation") }, !s.authBusy,
                            s.authFieldErrors["confirmation"], firstError == "confirmation", ImeAction.Done, focusTrigger = s.authError)
                        Text("12–128 characters.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Primary(when (s.authPage) { "register" -> "Create account"; "forgot" -> "Send reset link"; "resend" -> "Send verification link"; else -> "Sign in" },
                    if (recovery) Icons.Outlined.Email else Icons.Outlined.Login, !s.authBusy && email.isNotBlank()) {
                    if (recovery) model.requestEmail(email.trim(), s.authPage == "resend")
                    else if (registration) model.register(name, email.trim(), password, confirmation)
                    else model.authenticate(email.trim(), password)
                }
                if (!registration && !recovery) {
                    OutlinedButton(onClick = { model.googleSignIn(context) }, enabled = !s.authBusy,
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Color(0xFF1F1F1F)),
                        border = BorderStroke(1.dp, Color(0xFF747775)),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(14.dp)) {
                        Image(painterResource(R.drawable.google_g), null, Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Sign in with Google", fontFamily = FontFamily(Font(R.font.google_sans, FontWeight.Medium)), fontWeight = FontWeight.Medium)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { model.authPage("register") }, enabled = !s.authBusy, modifier = Modifier.weight(1f)) { Text("Create an account") }
                        TextButton(onClick = { model.authPage("forgot") }, enabled = !s.authBusy, modifier = Modifier.weight(1f)) { Text("Forgot password?") }
                    }
                    TextButton(onClick = { model.authPage("resend") }, enabled = !s.authBusy, modifier = Modifier.fillMaxWidth()) { Text("Resend verification email") }
                } else TextButton(onClick = { model.authPage("signin") }, enabled = !s.authBusy) { Text("Back to sign in") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onHelp, modifier = Modifier.weight(1f)) { Text("Help finding support") }
                TextButton(onClick = onPrivacy, modifier = Modifier.weight(1f)) { Text("Privacy & account help") }
            }
        }
    } }
}

@Composable fun ProfilePanel(s: UiState, model: AppViewModel) {
    val context = LocalContext.current
    val account = s.account ?: return
    var editing by remember { mutableStateOf(s.profileNameDraft != null) }
    val name = s.profileNameDraft ?: account.optString("display_name")
    LaunchedEffect(s.profileNameDraft, s.authMessage) {
        if (editing && s.profileNameDraft == null && s.authMessage == "Display name updated.") editing = false
    }
    Panel(Mint) {
        Icon(Icons.Outlined.AccountCircle, null, tint = Teal, modifier = Modifier.size(44.dp))
        Text(account.optString("display_name"), style = MaterialTheme.typography.titleLarge)
        Text(account.optString("email"))
        Text(if (account.optString("role") == "ADMINISTRATOR") "Administrator" else "Registered user", color = Muted)
        if (account.optString("role") == "ADMINISTRATOR") {
            Text("Manage listings and reports in the staff portal.")
            OutlinedButton(onClick = {
                runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(BuildConfig.API_URL.trimEnd('/') + "/staff/"))) }
                    .onFailure { model.error("No browser is available. Open the FoodBridge staff portal in your browser.") }
            }) { Text("Open staff portal") }
        }
        if (editing) {
            AccountField("Display name", name, { value -> model.update { it.copy(profileNameDraft = value, authFieldErrors = it.authFieldErrors - "display_name") } }, !s.authBusy,
                s.authFieldErrors["display_name"], s.authFieldErrors.containsKey("display_name"), imeAction = ImeAction.Done, focusTrigger = s.authError)
            Primary("Save name", Icons.Outlined.Check, !s.authBusy) { model.updateName(name) }
            TextButton(onClick = { model.update { it.copy(profileNameDraft = null) }; editing = false; model.authError(null) }) { Text("Cancel") }
        } else TextButton(onClick = { editing = true; model.authError(null) }) { Text("Edit display name") }
    }
    s.authError?.let { Banner(it, Icons.Outlined.Info, Peach) }
    s.authMessage?.let { Banner(it, Icons.Outlined.CheckCircle, Mint) }
    OutlinedButton(onClick = { model.authError(null); model.update { it.copy(page = "password", authMessage = null) } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Change password") }
    OutlinedButton(onClick = model::signOut, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Sign out") }
}

@Composable fun ChangePasswordScreen(s: UiState, model: AppViewModel) {
    var current by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val firstError = listOf("current_password", "password", "confirmation").firstOrNull { s.authFieldErrors.containsKey(it) }
    fun changed(field: String) { model.update { it.copy(authFieldErrors = it.authFieldErrors - field) } }
    Page {
        Text("Change password", style = MaterialTheme.typography.headlineMedium)
        s.authError?.let { Banner(it, Icons.Outlined.Info, Peach) }
        s.authMessage?.let { Banner(it, Icons.Outlined.MarkEmailRead, Mint) }
        if (s.account?.optBoolean("has_password") == false) {
            Text("You signed up with Google. Request a password reset link to set a FoodBridge password.", color = Muted)
            Primary("Send password reset link", Icons.Outlined.Email, !s.authBusy) { model.requestEmail(s.account.optString("email"), false) }
        } else {
            Text("Use 12 to 128 characters. Changing your password signs out all your FoodBridge sessions.", color = Muted)
            PasswordField("Current password", current, { current = it; changed("current_password") }, !s.authBusy,
                s.authFieldErrors["current_password"], firstError == "current_password", focusTrigger = s.authError)
            PasswordField("New password", password, { password = it; changed("password") }, !s.authBusy,
                s.authFieldErrors["password"], firstError == "password", focusTrigger = s.authError)
            PasswordField("Confirm new password", confirmation, { confirmation = it; changed("confirmation") }, !s.authBusy,
                s.authFieldErrors["confirmation"], firstError == "confirmation", ImeAction.Done, focusTrigger = s.authError)
            Primary("Change password", Icons.Outlined.Lock, !s.authBusy) {
                model.changePassword(current, password, confirmation)
            }
        }
        TextButton(onClick = model::back, enabled = !s.authBusy) { Text("Cancel") }
    }
}
