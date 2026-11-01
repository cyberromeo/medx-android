package quest.srihari.medxelite.ui.auth

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import quest.srihari.medxelite.R
import quest.srihari.medxelite.ui.components.PrimaryButton
import quest.srihari.medxelite.ui.components.tappable
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import quest.srihari.medxelite.Config
import quest.srihari.medxelite.Medx
import quest.srihari.medxelite.auth.AuthException
import quest.srihari.medxelite.auth.GoogleSignIn
import quest.srihari.medxelite.ui.components.MedxLogoMark
import quest.srihari.medxelite.ui.components.openCustomTab
import quest.srihari.medxelite.ui.theme.Mx

/**
 * Open sign-in for any account in the medx-e9acd project. Replaces the iOS profile picker
 * (ProfilePickerView + PasswordPromptView, two hard-coded people).
 */
@Composable
fun SignInScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var canResend by remember { mutableStateOf(false) }
    var needsActivation by remember { mutableStateOf(false) }

    fun signInEmail() {
        if (busy != null) return
        focus.clearFocus()
        error = null; info = null; canResend = false; needsActivation = false
        if (email.isBlank() || password.isEmpty()) {
            error = "Enter your email and password."
            return
        }
        busy = "email"
        scope.launch {
            try {
                Medx.auth.signInWithEmail(email, password)
                Medx.onSignedIn()
            } catch (e: AuthException) {
                error = e.message
                canResend = e.kind == AuthException.Kind.NOT_VERIFIED
            } catch (e: Exception) {
                error = e.message ?: "Sign-in failed."
            } finally {
                busy = null
            }
        }
    }

    fun signInGoogle() {
        if (busy != null) return
        error = null; info = null; canResend = false; needsActivation = false
        val activity = context as? Activity ?: return
        busy = "google"
        scope.launch {
            try {
                val token = GoogleSignIn.idToken(activity)
                if (token != null) {
                    Medx.auth.signInWithGoogleIdToken(token)
                    Medx.onSignedIn()
                }
            } catch (e: AuthException) {
                error = e.message
                needsActivation = e.kind == AuthException.Kind.NOT_ACTIVATED
            } catch (e: Exception) {
                error = e.message ?: "Google sign-in failed."
            } finally {
                busy = null
            }
        }
    }

    fun forgot() {
        if (busy != null) return
        error = null; info = null
        if (email.isBlank()) {
            error = "Enter your email address above, then tap Forgot password."
            return
        }
        busy = "reset"
        scope.launch {
            try {
                Medx.auth.sendPasswordReset(email)
                info = "Password reset email sent to ${email.trim()}. Check your inbox."
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = null
            }
        }
    }

    val c = Mx.colors
    Box(Modifier.fillMaxSize().background(c.groupedBackground)) {
        // A soft wash of brand colour behind the mark: depth without decoration.
        Box(
            Modifier
                .fillMaxWidth()
                .height(360.dp)
                .background(Brush.verticalGradient(listOf(c.accentContainer.copy(alpha = if (c.isDark) 0.55f else 0.9f), c.groupedBackground))),
        )
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))
            MedxLogoMark(size = 76.dp)
            Spacer(Modifier.height(24.dp))
            Text("Welcome to MedX Elite", style = MaterialTheme.typography.headlineMedium, color = c.ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "Sign in to pick up your FMGE prep where you left off.",
                color = c.secondaryText,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp),
            )
            Spacer(Modifier.height(36.dp))

            Column(Modifier.widthIn(max = 440.dp).fillMaxWidth()) {
                GoogleSignInButton(
                    dark = c.isDark,
                    loading = busy == "google",
                    enabled = busy == null,
                    onClick = ::signInGoogle,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(Modifier.padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(Modifier.weight(1f), color = c.separator)
                    Text("or with email", color = c.secondaryText, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp))
                    HorizontalDivider(Modifier.weight(1f), color = c.separator)
                }

                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = c.card, unfocusedContainerColor = c.card,
                    unfocusedBorderColor = c.separator, focusedBorderColor = c.accent,
                    focusedLabelColor = c.accent, unfocusedLabelColor = c.secondaryText,
                    cursorColor = c.accent,
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Rounded.MailOutline, null, tint = c.secondaryText) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, null, tint = c.secondaryText) },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { signInEmail() }),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                if (showPassword) "Hide password" else "Show password",
                                tint = c.secondaryText,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = ::forgot, enabled = busy == null) {
                        Text(if (busy == "reset") "Sending…" else "Forgot password?", style = MaterialTheme.typography.labelLarge, color = c.accent)
                    }
                }

                if (error != null) {
                    Notice(error!!, c.wrong, c.wrongContainer)
                }
                if (info != null) {
                    Notice(info!!, c.correct, c.correctContainer)
                }
                if (canResend) {
                    TextButton(onClick = {
                        scope.launch {
                            info = if (runCatching { Medx.auth.resendVerification() }.getOrDefault(false)) {
                                "Verification email sent. Open the link, then sign in again."
                            } else {
                                null
                            }
                            error = null
                            canResend = false
                        }
                    }) { Text("Resend verification email", style = MaterialTheme.typography.labelLarge) }
                }
                if (needsActivation) {
                    TextButton(onClick = { openCustomTab(context, Config.WEB_ACTIVATE_URL) }) { Text("Activate on the MedX website", style = MaterialTheme.typography.labelLarge) }
                }

                Spacer(Modifier.height(8.dp))
                PrimaryButton(
                    "Sign in",
                    onClick = ::signInEmail,
                    enabled = busy == null || busy == "email",
                    loading = busy == "email",
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(32.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text("New to MedX?", color = c.secondaryText, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { openCustomTab(context, Config.WEB_CREATE_ACCOUNT_URL) }) {
                        Text("Create account", style = MaterialTheme.typography.labelLarge, color = c.accent)
                    }
                }
                Text(
                    "Accounts are created on the MedX website with an activation code, then work here too.",
                    color = c.secondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun Notice(text: String, fg: Color, bg: Color) {
    Text(
        text, color = fg, style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(14.dp)).background(bg).padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/**
 * Sign in with Google button built to Google's branding guidelines
 * (developers.google.com/identity/branding-guidelines): light #FFFFFF / 1dp #747775 stroke /
 * #1F1F1F text, dark #131314 / #8E918F / #E3E3E3, standard four-colour G at its fixed size,
 * 12dp before the logo, 10dp after it, 12dp after the text, medium-weight label.
 */
@Composable
fun GoogleSignInButton(dark: Boolean, loading: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val fill = if (dark) Color(0xFF131314) else Color(0xFFFFFFFF)
    val stroke = if (dark) Color(0xFF8E918F) else Color(0xFF747775)
    val text = if (dark) Color(0xFFE3E3E3) else Color(0xFF1F1F1F)
    Box(
        modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(50))
            .background(fill)
            .border(1.dp, stroke, RoundedCornerShape(50))
            .tappable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_google_g), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), color = text, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Signing in…", color = text, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 20.sp)
            } else {
                Text("Continue with Google", color = text, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 20.sp)
            }
        }
    }
}
