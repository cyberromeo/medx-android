package quest.srihari.medxelite.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import quest.srihari.medxelite.Config

/**
 * The Firebase Android SDK, initialised by hand from [Config] (no google-services.json), and
 * signed in alongside the REST session — the Android twin of iOS `MedxFirebaseBridge`.
 *
 * It is optional: until [Config.androidAppId] is filled in it stays off and nothing depends on
 * it. If a `google-services.json` is dropped in later and the plugin applied, the default app
 * that creates is used as-is.
 */
object FirebaseBridge {
    private const val TAG = "FirebaseBridge"
    private var app: FirebaseApp? = null

    val isConfigured: Boolean get() = app != null

    val status: String
        get() = when {
            app != null -> "Firebase SDK ready (${app?.options?.applicationId})"
            Config.androidAppId.isBlank() -> "REST only — ANDROID_APP_ID not set"
            else -> "Firebase SDK failed to start"
        }

    fun init(context: Context) {
        try {
            val existing = FirebaseApp.getApps(context)
            if (existing.isNotEmpty()) {
                app = existing.first()
                return
            }
            val appId = Config.androidAppId
            if (!appId.contains(":android:")) return
            val options = FirebaseOptions.Builder()
                .setApiKey(Config.API_KEY)
                .setApplicationId(appId)
                .setProjectId(Config.PROJECT_ID)
                .setStorageBucket(Config.STORAGE_BUCKET)
                .setGcmSenderId(Config.MESSAGING_SENDER_ID)
                .build()
            app = FirebaseApp.initializeApp(context, options)
        } catch (e: Exception) {
            Log.w(TAG, "Firebase init skipped: ${e.message}")
            app = null
        }
    }

    private fun auth(): FirebaseAuth? = app?.let { runCatching { FirebaseAuth.getInstance(it) }.getOrNull() }

    fun signInWithEmail(email: String, password: String) {
        auth()?.signInWithEmailAndPassword(email, password)
            ?.addOnFailureListener { Log.w(TAG, "SDK email sign-in failed: ${it.message}") }
    }

    fun signInWithGoogle(idToken: String) {
        auth()?.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
            ?.addOnFailureListener { Log.w(TAG, "SDK Google sign-in failed: ${it.message}") }
    }

    fun signOut() {
        runCatching { auth()?.signOut() }
    }
}

/** Credential Manager → Google ID token. */
object GoogleSignIn {
    class Unavailable(message: String) : Exception(message)

    val isConfigured: Boolean get() = Config.googleWebClientId.endsWith(".apps.googleusercontent.com")

    /**
     * Shows the Google account picker and returns the Google ID token, or null if the person
     * cancelled. Tries the bottom-sheet (authorized accounts first, then any account) and falls
     * back to the full "Sign in with Google" flow when the device has no eligible account yet.
     */
    suspend fun idToken(activity: Activity): String? {
        if (!isConfigured) {
            throw Unavailable(
                "Google sign-in isn't set up in this build yet: GOOGLE_WEB_CLIENT_ID is empty. Use email for now.",
            )
        }
        val manager = CredentialManager.create(activity)
        val clientId = Config.googleWebClientId
        val attempts = listOf(
            GetCredentialRequest.Builder().addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(clientId)
                    .setAutoSelectEnabled(false)
                    .build(),
            ).build(),
            GetCredentialRequest.Builder().addCredentialOption(
                GetSignInWithGoogleOption.Builder(clientId).build(),
            ).build(),
        )
        var lastError: Exception? = null
        for (request in attempts) {
            try {
                val result = manager.getCredential(activity, request)
                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    return GoogleIdTokenCredential.createFrom(credential.data).idToken
                }
                throw Unavailable("Unexpected credential type.")
            } catch (e: GetCredentialCancellationException) {
                return null
            } catch (e: NoCredentialException) {
                lastError = e
                continue
            } catch (e: Unavailable) {
                throw e
            } catch (e: Exception) {
                lastError = e
                continue
            }
        }
        throw Unavailable(
            "Google sign-in couldn't start (${lastError?.javaClass?.simpleName ?: "unknown"}). " +
                "Check that this build's SHA-1 is registered in Firebase.",
        )
    }
}
