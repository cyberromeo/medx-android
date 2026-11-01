package quest.srihari.medxelite.auth

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import quest.srihari.medxelite.Config
import quest.srihari.medxelite.data.Http
import quest.srihari.medxelite.data.HttpException
import quest.srihari.medxelite.data.MedxJson
import quest.srihari.medxelite.data.bool
import quest.srihari.medxelite.data.jsonOf
import quest.srihari.medxelite.data.long
import quest.srihari.medxelite.data.obj
import quest.srihari.medxelite.data.objects
import quest.srihari.medxelite.data.str
import java.io.IOException
import java.net.URLEncoder

/**
 * The signed-in Firebase user. Replaces the iOS `Profile` (two hard-coded people): everything the
 * app used to read off a profile — id, display name, initials, avatar — now derives from here.
 */
data class AuthSession(
    val idToken: String,
    val refreshToken: String,
    val uid: String,
    val email: String,
    val displayName: String?,
    val photoUrl: String?,
    val provider: String, // "password" or "google.com"
    val expiresAtMs: Long,
) {
    /** What the app calls the person: their Firebase display name, else the email's local part. */
    val name: String
        get() = displayName?.takeIf { it.isNotBlank() }
            ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }.ifBlank { "You" }

    val firstName: String get() = name.split(" ").firstOrNull()?.ifBlank { null } ?: name

    val initials: String
        get() = name.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase().ifEmpty { "?" }

    fun toJson() = jsonOf(
        "idToken" to idToken, "refreshToken" to refreshToken, "uid" to uid, "email" to email,
        "displayName" to displayName, "photoUrl" to photoUrl, "provider" to provider, "expiresAtMs" to expiresAtMs,
    )

    companion object {
        fun from(o: JsonObject): AuthSession? {
            return AuthSession(
                idToken = o.str("idToken") ?: return null,
                refreshToken = o.str("refreshToken") ?: return null,
                uid = o.str("uid") ?: return null,
                email = o.str("email") ?: "",
                displayName = o.str("displayName"),
                photoUrl = o.str("photoUrl"),
                provider = o.str("provider") ?: "password",
                expiresAtMs = o.long("expiresAtMs") ?: 0,
            )
        }
    }
}

class AuthException(message: String, val kind: Kind = Kind.GENERAL) : IOException(message) {
    enum class Kind { GENERAL, NOT_VERIFIED, NOT_ACTIVATED, EXPIRED }
}

/**
 * Firebase Auth over the Identity Toolkit REST API (same as iOS AuthService.swift), so it works
 * with nothing but the API key. Google sign-in exchanges the Credential Manager ID token through
 * `accounts:signInWithIdp`. The session persists in SharedPreferences, like the iOS UserDefaults.
 */
class AuthRepository(context: Context) {
    private val prefs = context.getSharedPreferences("medx.auth", Context.MODE_PRIVATE)
    private val _session = MutableStateFlow<AuthSession?>(load())
    val session: StateFlow<AuthSession?> = _session

    /** Called after sign-out so per-user stores can drop what they hold. */
    var onSignedOut: (() -> Unit)? = null

    private val refreshMutex = Mutex()

    val current: AuthSession? get() = _session.value
    val uid: String? get() = _session.value?.uid

    private fun load(): AuthSession? = prefs.getString("session", null)?.let {
        runCatching { AuthSession.from(MedxJson.parseToJsonElement(it).jsonObject) }.getOrNull()
    }

    private fun save(s: AuthSession) {
        prefs.edit().putString("session", s.toJson().toString()).apply()
        _session.value = s
    }

    // ------------------------------------------------------------------ email + password

    suspend fun signInWithEmail(email: String, password: String): AuthSession {
        val json = identity(
            "signInWithPassword",
            jsonOf("email" to email.trim(), "password" to password, "returnSecureToken" to true),
        )
        val idToken = json.str("idToken") ?: throw AuthException("Unexpected response from sign-in.")
        val user = lookup(idToken)
        // Same rule as the web app's login page: an unverified email/password account is sent to
        // verify first.
        if (user?.bool("emailVerified") == false) {
            pendingVerificationToken = idToken
            throw AuthException(
                "Verify your email first. We sent a link to ${email.trim()} when the account was created.",
                AuthException.Kind.NOT_VERIFIED,
            )
        }
        val s = sessionFrom(json, user, "password")
        save(s)
        FirebaseBridge.signInWithEmail(email.trim(), password)
        return s
    }

    private var pendingVerificationToken: String? = null

    suspend fun resendVerification(): Boolean {
        val token = pendingVerificationToken ?: return false
        identity("sendOobCode", jsonOf("requestType" to "VERIFY_EMAIL", "idToken" to token))
        return true
    }

    suspend fun sendPasswordReset(email: String) {
        identity("sendOobCode", jsonOf("requestType" to "PASSWORD_RESET", "email" to email.trim()))
    }

    // ------------------------------------------------------------------ Google

    suspend fun signInWithGoogleIdToken(googleIdToken: String): AuthSession {
        val postBody = "id_token=" + URLEncoder.encode(googleIdToken, "UTF-8") + "&providerId=google.com"
        val json = identity(
            "signInWithIdp",
            jsonOf(
                "postBody" to postBody,
                "requestUri" to "http://localhost",
                "returnIdpCredential" to true,
                "returnSecureToken" to true,
            ),
        )
        val idToken = json.str("idToken") ?: throw AuthException(
            json.str("errorMessage") ?: "Google sign-in did not return a Firebase session.",
        )
        val user = lookup(idToken)
        val providers = user?.objects("providerUserInfo")?.mapNotNull { it.str("providerId") } ?: emptyList()
        // Google-only accounts are gated by an activation code on the web app; ask the same API.
        if (!providers.contains("password")) {
            val active = checkActivation(idToken)
            if (active == false) {
                throw AuthException(
                    "This Google account isn't activated yet. Redeem your activation code on the MedX website, then sign in again.",
                    AuthException.Kind.NOT_ACTIVATED,
                )
            }
        }
        val s = sessionFrom(json, user, "google.com")
        save(s)
        FirebaseBridge.signInWithGoogle(googleIdToken)
        return s
    }

    /** true / false from the web app's activation API, null when it could not be asked. */
    private suspend fun checkActivation(idToken: String): Boolean? = try {
        val text = Http.call("GET", Config.WEB_ACTIVATION_STATUS_URL, idToken)
        MedxJson.parseToJsonElement(text).jsonObject.bool("active")
    } catch (e: HttpException) {
        if (e.code == 401 || e.code == 403) false else null
    } catch (_: Exception) {
        null
    }

    // ------------------------------------------------------------------ tokens

    /** A valid ID token, refreshed through securetoken when it is about to expire. */
    suspend fun validIdToken(): String {
        val s = _session.value ?: throw AuthException("Not signed in", AuthException.Kind.EXPIRED)
        if (s.expiresAtMs > System.currentTimeMillis()) return s.idToken
        return refreshMutex.withLock {
            val again = _session.value ?: throw AuthException("Not signed in", AuthException.Kind.EXPIRED)
            if (again.expiresAtMs > System.currentTimeMillis()) return@withLock again.idToken
            refresh(again)
        }
    }

    private suspend fun refresh(s: AuthSession): String {
        val text = try {
            Http.call(
                "POST",
                "${Config.SECURE_TOKEN_BASE}?key=${Config.API_KEY}",
                formBody = "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(s.refreshToken, "UTF-8"),
            )
        } catch (e: HttpException) {
            val message = runCatching {
                MedxJson.parseToJsonElement(e.body).jsonObject.obj("error")?.str("message")
            }.getOrNull() ?: ""
            val fatal = listOf(
                "INVALID_REFRESH_TOKEN", "TOKEN_EXPIRED", "USER_DISABLED", "USER_NOT_FOUND",
                "INVALID_GRANT_TYPE", "MISSING_REFRESH_TOKEN",
            )
            if (e.code in 400..401 && fatal.any { message.startsWith(it) }) {
                signOut()
                throw AuthException("Session expired. Please sign in again.", AuthException.Kind.EXPIRED)
            }
            throw e
        }
        val json = MedxJson.parseToJsonElement(text).jsonObject
        val newId = json.str("id_token") ?: throw IOException("Bad refresh response")
        val updated = s.copy(
            idToken = newId,
            refreshToken = json.str("refresh_token") ?: s.refreshToken,
            expiresAtMs = System.currentTimeMillis() + ((json.str("expires_in")?.toLongOrNull() ?: 3600) - 60) * 1000,
        )
        save(updated)
        return newId
    }

    fun signOut() {
        prefs.edit().remove("session").apply()
        _session.value = null
        FirebaseBridge.signOut()
        onSignedOut?.invoke()
    }

    // ------------------------------------------------------------------ helpers

    private fun sessionFrom(json: JsonObject, user: JsonObject?, provider: String): AuthSession {
        val expires = json.str("expiresIn")?.toLongOrNull() ?: 3600
        return AuthSession(
            idToken = json.str("idToken")!!,
            refreshToken = json.str("refreshToken") ?: "",
            uid = json.str("localId") ?: user?.str("localId") ?: "",
            email = json.str("email") ?: user?.str("email") ?: "",
            displayName = user?.str("displayName") ?: json.str("displayName") ?: json.str("fullName"),
            photoUrl = user?.str("photoUrl") ?: json.str("photoUrl"),
            provider = provider,
            expiresAtMs = System.currentTimeMillis() + (expires - 60) * 1000,
        )
    }

    private suspend fun lookup(idToken: String): JsonObject? = try {
        identity("lookup", jsonOf("idToken" to idToken)).objects("users").firstOrNull()
    } catch (_: Exception) {
        null
    }

    private suspend fun identity(method: String, body: JsonObject): JsonObject {
        try {
            val text = Http.call("POST", "${Config.IDENTITY_BASE}:$method?key=${Config.API_KEY}", body = body)
            return MedxJson.parseToJsonElement(text).jsonObject
        } catch (e: HttpException) {
            val code = runCatching {
                MedxJson.parseToJsonElement(e.body).jsonObject.obj("error")?.str("message")
            }.getOrNull() ?: ""
            throw AuthException(friendly(code))
        } catch (e: AuthException) {
            throw e
        } catch (e: Exception) {
            throw AuthException("Couldn't reach the sign-in service. Check your connection.")
        }
    }

    private fun friendly(code: String): String = when {
        code.startsWith("EMAIL_NOT_FOUND") -> "No account found with this email."
        code.startsWith("INVALID_PASSWORD") || code.startsWith("INVALID_LOGIN_CREDENTIALS") ->
            "Incorrect email or password."
        code.startsWith("USER_DISABLED") -> "This account has been disabled."
        code.startsWith("TOO_MANY_ATTEMPTS_TRY_LATER") -> "Too many attempts. Please try again later."
        code.startsWith("INVALID_EMAIL") -> "That email address doesn't look right."
        code.startsWith("MISSING_PASSWORD") -> "Enter your password."
        code.startsWith("INVALID_IDP_RESPONSE") || code.startsWith("INVALID_ID_TOKEN") ->
            "Google sign-in was rejected. Check the Web client ID and that Google is enabled in Firebase."
        code.startsWith("OPERATION_NOT_ALLOWED") -> "This sign-in method is not enabled for the project."
        code.isBlank() -> "Sign-in failed."
        else -> code.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    }
}
