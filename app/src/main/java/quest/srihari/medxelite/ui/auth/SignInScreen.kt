package quest.srihari.medxelite.ui.auth

import quest.srihari.medxelite.ui.components.GradientButton
import quest.srihari.medxelite.ui.components.GlassButton
import quest.srihari.medxelite.ui.components.GradientText
import quest.srihari.medxelite.ui.components.NeuralField
import quest.srihari.medxelite.ui.components.glass
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
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
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
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
    val idle = if (c.isDark) Color.White.copy(alpha = 0.16f) else Color(0xFF1B1446).copy(alpha = 0.18f)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = c.card, unfocusedContainerColor = c.card,
        focusedBorderColor = c.glowEnd, unfocusedBorderColor = idle,
        focusedLabelColor = c.glowEnd, unfocusedLabelColor = c.secondaryText,
        cursorColor = c.glowEnd, focusedLeadingIconColor = c.glowEnd, unfocusedLeadingIconColor = c.secondaryText,
        focusedTrailingIconColor = c.text, unfocusedTrailingIconColor = c.secondaryText,
    )
    Box(Modifier.fillMaxSize()) {
        NeuralField(Modifier.fillMaxWidth().height(440.dp), nodes = 44, seed = 3, hollow = androidx.compose.ui.geometry.Offset(0.5f, 0.62f), hollowRadius = 0.5f, fadeBottom = true)

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
            MedxLogoMark(size = 92.dp)
            Spacer(Modifier.height(22.dp))
            GradientText("MedX Elite", MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, letterSpacing = (-1.4).sp))
            Spacer(Modifier.height(4.dp))
            Text(
                "Sign in to your MedX account",
                color = Mx.colors.secondaryText,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FeatureTag(Icons.Rounded.Quiz, "QBank")
                FeatureTag(Icons.Rounded.Assignment, "Tests")
                FeatureTag(Icons.Rounded.PlayCircle, "Classes")
            }
            Spacer(Modifier.height(28.dp))

            Column(
                Modifier.widthIn(max = 440.dp).fillMaxWidth()
                    .glass(RoundedCornerShape(28.dp), glow = true, glowCorner = 28.dp)
                    .padding(18.dp),
            ) {
                GlassButton(
                    onClick = ::signInGoogle,
                    enabled = busy == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy == "google") {
                        CircularProgressIndicator(Modifier.size(20.dp), color = c.glowEnd, strokeWidth = 2.dp)
                    } else {
                        GoogleG()
                        Spacer(Modifier.width(10.dp))
                        Text("Continue with Google", fontWeight = FontWeight.SemiBold)
                    }
                }

                Row(Modifier.padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(Modifier.weight(1f), color = Mx.colors.separator)
                    Text("  or use email  ", color = Mx.colors.secondaryText, style = MaterialTheme.typography.labelMedium)
                    HorizontalDivider(Modifier.weight(1f), color = Mx.colors.separator)
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Rounded.AlternateEmail, null) },
                    colors = fieldColors,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                    colors = fieldColors,
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { signInEmail() }),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show password")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = ::forgot, enabled = busy == null) {
                        Text(if (busy == "reset") "Sending…" else "Forgot password?")
                    }
                }

                if (error != null) {
                    Text(error!!, color = Mx.colors.wrong, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
                }
                if (info != null) {
                    Text(info!!, color = Mx.colors.correct, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
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
                    }) { Text("Resend verification email") }
                }
                if (needsActivation) {
                    TextButton(onClick = { openCustomTab(context, Config.WEB_ACTIVATE_URL) }) { Text("Activate on the MedX website") }
                }

                GradientButton(
                    onClick = ::signInEmail,
                    enabled = busy == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy == "email") {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Sign in")
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Column(Modifier.widthIn(max = 440.dp).fillMaxWidth()) {

                Spacer(Modifier.height(28.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text("New to MedX?", color = Mx.colors.secondaryText)
                    TextButton(onClick = { openCustomTab(context, Config.WEB_CREATE_ACCOUNT_URL) }) {
                        Text("Create account", fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(
                    "Accounts are created on the MedX website with an activation code, then work here too.",
                    color = Mx.colors.tertiaryText,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun GoogleG() {
    Box(
        Modifier.size(22.dp).clip(RoundedCornerShape(50)).background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun FeatureTag(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    val c = Mx.colors
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(c.card).border(1.dp, c.hairline, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.3.sp), color = c.text)
    }
}
