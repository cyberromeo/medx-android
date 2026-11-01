package quest.srihari.medxelite.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.draw.drawWithContent
import quest.srihari.medxelite.ui.components.DotDivider
import quest.srihari.medxelite.ui.components.DotGrid
import quest.srihari.medxelite.ui.components.DotText
import quest.srihari.medxelite.ui.components.NLabel
import quest.srihari.medxelite.ui.components.RecDot
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
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
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
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.ink, unfocusedBorderColor = c.separator,
        focusedContainerColor = c.card, unfocusedContainerColor = c.card,
        focusedLabelColor = c.ink, unfocusedLabelColor = c.secondaryText, cursorColor = c.accent,
    )
    val fieldShape = RoundedCornerShape(20.dp)
    Box(Modifier.fillMaxSize().background(c.groupedBackground)) {
        // Dot-matrix field fading out behind the wordmark.
        DotGrid(
            Modifier.fillMaxWidth().height(340.dp).drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, c.groupedBackground), startY = size.height * 0.35f),
                )
            },
            spacing = 16.dp, dot = 2.dp,
        )
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(40.dp))
            Column(Modifier.widthIn(max = 440.dp).fillMaxWidth().align(Alignment.CenterHorizontally)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MedxLogoMark(size = 64.dp)
                    Spacer(Modifier.width(12.dp))
                    RecDot()
                    Spacer(Modifier.width(8.dp))
                    NLabel("FMGE prep · v${quest.srihari.medxelite.BuildConfig.VERSION_NAME}")
                }
                Spacer(Modifier.height(36.dp))
                DotText("MEDX", size = 84.sp)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    DotText("ELITE", size = 84.sp, color = c.secondaryText)
                    Spacer(Modifier.width(10.dp))
                    RecDot(size = 14.dp)
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Sign in to your MedX account",
                    color = c.secondaryText,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(32.dp))

                OutlinedButton(
                    onClick = ::signInGoogle,
                    enabled = busy == null,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, c.separator),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = c.card, contentColor = c.ink),
                ) {
                    if (busy == "google") {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = c.ink)
                    } else {
                        GoogleG()
                        Spacer(Modifier.width(10.dp))
                        Text("Continue with Google", fontWeight = FontWeight.SemiBold)
                    }
                }

                Row(Modifier.padding(vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    DotDivider(Modifier.weight(1f))
                    NLabel("or use email", Modifier.padding(horizontal = 12.dp))
                    DotDivider(Modifier.weight(1f))
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    shape = fieldShape,
                    colors = fieldColors,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { signInEmail() }),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Show password")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = fieldShape,
                    colors = fieldColors,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = ::forgot, enabled = busy == null) {
                        Text(if (busy == "reset") "Sending…" else "Forgot password?", color = c.secondaryText)
                    }
                }

                if (error != null) {
                    Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.Top) {
                        RecDot(Modifier.padding(top = 6.dp), color = c.wrong)
                        Spacer(Modifier.width(8.dp))
                        Text(error!!, color = c.wrong, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (info != null) {
                    Text(info!!, color = c.ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 10.dp))
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

                Button(
                    onClick = ::signInEmail,
                    enabled = busy == null,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = CircleShape,
                ) {
                    if (busy == "email") {
                        CircularProgressIndicator(Modifier.size(20.dp), color = c.onInk, strokeWidth = 2.dp)
                    } else {
                        Text("Sign in", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(28.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text("New to MedX?", color = c.secondaryText)
                    TextButton(onClick = { openCustomTab(context, Config.WEB_CREATE_ACCOUNT_URL) }) {
                        Text("Create account", fontWeight = FontWeight.SemiBold, color = c.ink)
                    }
                }
                Text(
                    "Accounts are created on the MedX website with an activation code, then work here too.",
                    color = c.tertiaryText,
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
