package sg.foodbridge.navigator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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

@Composable fun PasswordField(label: String, value: String, onChange: (String) -> Unit, enabled: Boolean = true) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, enabled = enabled,
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = { IconButton(onClick = { visible = !visible }) {
            Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "Hide password" else "Show password")
        } })
}

@Composable fun AccountEntryScreen(s: UiState, model: AppViewModel) {
    val context = LocalContext.current
    var email by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    // Passwords deliberately remain only in memory and are never placed in saved instance state.
    var password by remember(s.authPage) { mutableStateOf("") }
    var confirmation by remember(s.authPage) { mutableStateOf("") }
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
                Text(when (s.authPage) { "register" -> "Create an account"; "forgot" -> "Reset your password"; "resend" -> "Verify your email"; else -> "Welcome to FoodBridge" }, style = MaterialTheme.typography.headlineLarge)
                Text(when (s.authPage) { "register" -> "Find and save food support. Verify your email, then sign in."; "forgot" -> "We’ll email you a link to choose a new password."; "resend" -> "Open the link in your email, then return here to sign in."; else -> "Sign in to find and save food support." }, color = Muted)
            }
            s.authError?.let { Banner(it, Icons.Outlined.Info, Peach) }
            s.authMessage?.let { Banner(it, Icons.Outlined.MarkEmailRead, Mint) }
            if (s.authMessage != null && s.account == null) Text("Finished in your browser? Return here to sign in.", color = Muted)
            if (s.authBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (registration) OutlinedTextField(name, { name = it }, label = { Text("Display name") }, enabled = !s.authBusy,
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                OutlinedTextField(email, { email = it }, label = { Text("Email address") }, enabled = !s.authBusy,
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                if (!recovery) {
                    PasswordField("Password", password, { password = it }, !s.authBusy)
                    if (registration) {
                        PasswordField("Confirm password", confirmation, { confirmation = it }, !s.authBusy)
                        Text("12–128 characters.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Primary(when (s.authPage) { "register" -> "Create account"; "forgot" -> "Send reset link"; "resend" -> "Send verification link"; else -> "Sign in" },
                    if (recovery) Icons.Outlined.Email else Icons.Outlined.Login, !s.authBusy && email.isNotBlank()) {
                    if (recovery) model.requestEmail(email.trim(), s.authPage == "resend")
                    else if (registration) model.register(name, email.trim(), password, confirmation)
                    else model.authenticate(email.trim(), password)
                    password = ""; confirmation = ""
                }
                if (!registration && !recovery) {
                    TextButton(onClick = { model.authPage("forgot") }, enabled = !s.authBusy) { Text("Forgot password?") }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        HorizontalDivider(Modifier.weight(1f)); Text("or", color = Muted); HorizontalDivider(Modifier.weight(1f))
                    }
                    OutlinedButton(onClick = { model.googleSignIn(context) }, enabled = !s.authBusy,
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Color(0xFF1F1F1F)),
                        border = BorderStroke(1.dp, Color(0xFF747775)),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(14.dp)) {
                        Image(painterResource(R.drawable.google_g), null, Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Sign in with Google", fontFamily = FontFamily(Font(R.font.google_sans, FontWeight.Medium)), fontWeight = FontWeight.Medium)
                    }
                    TextButton(onClick = { model.authPage("register") }, enabled = !s.authBusy, modifier = Modifier.fillMaxWidth()) { Text("Create an account") }
                    TextButton(onClick = { model.authPage("resend") }, enabled = !s.authBusy, modifier = Modifier.fillMaxWidth()) { Text("Resend verification email") }
                } else TextButton(onClick = { model.authPage("signin") }, enabled = !s.authBusy) { Text("Back to sign in") }
            }

        }
    } }
}

@Composable fun ProfilePanel(s: UiState, model: AppViewModel) {
    val context = LocalContext.current
    val account = s.account ?: return
    var editing by remember { mutableStateOf(s.profileNameDraft != null) }
    val name = s.profileNameDraft ?: account.optString("display_name")
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
            OutlinedTextField(name, { value -> model.update { it.copy(profileNameDraft = value) } }, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
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
    Page {
        Text("Change password", style = MaterialTheme.typography.headlineMedium)
        s.authError?.let { Banner(it, Icons.Outlined.Info, Peach) }
        s.authMessage?.let { Banner(it, Icons.Outlined.MarkEmailRead, Mint) }
        if (s.account?.optBoolean("has_password") == false) {
            Text("You signed up with Google. Request a password reset link to set a FoodBridge password.", color = Muted)
            Primary("Send password reset link", Icons.Outlined.Email, !s.authBusy) { model.requestEmail(s.account.optString("email"), false) }
        } else {
            Text("Use 12 to 128 characters. Changing your password signs out all your FoodBridge sessions.", color = Muted)
            PasswordField("Current password", current, { current = it }, !s.authBusy)
            PasswordField("New password", password, { password = it }, !s.authBusy)
            PasswordField("Confirm new password", confirmation, { confirmation = it }, !s.authBusy)
            Primary("Change password", Icons.Outlined.Lock, !s.authBusy) {
                model.changePassword(current, password, confirmation)
                current = ""; password = ""; confirmation = ""
            }
        }
        TextButton(onClick = model::back, enabled = !s.authBusy) { Text("Cancel") }
    }
}
