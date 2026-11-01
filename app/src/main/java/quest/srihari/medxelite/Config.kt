package quest.srihari.medxelite

/**
 * The one source of backend truth, ported from the iOS app's `FirebaseConfig.swift`.
 *
 * Everything here is public client configuration (the same values ship inside the web app).
 */
object Config {
    const val API_KEY = "AIzaSyCkuExDptljH-kIN555APSeaRboZw06Vaw"
    const val PROJECT_ID = "medx-e9acd"
    const val AUTH_DOMAIN = "medx-e9acd.firebaseapp.com"
    const val STORAGE_BUCKET = "medx-e9acd.firebasestorage.app"
    const val MESSAGING_SENDER_ID = "300960747898"

    /** The web app registration (used by the PWA). Not accepted by the Android SDK. */
    const val WEB_APP_ID = "1:300960747898:web:c8ad40db21d815a6a946c3"

    /**
     * TODO(Sri): the OAuth **Web client ID** used for "Continue with Google".
     *
     * Firebase console ▸ Authentication ▸ Sign-in method ▸ Google ▸ "Web SDK configuration" ▸
     * Web client ID. It looks like `300960747898-xxxxxxxx.apps.googleusercontent.com`.
     *
     * Either paste it here, or put `GOOGLE_WEB_CLIENT_ID=...` in `local.properties`
     * (or a CI env var) — the BuildConfig value wins when it is set.
     * While it is empty the Google button explains what is missing and email sign-in still works.
     */
    private const val GOOGLE_WEB_CLIENT_ID_FALLBACK = ""

    /**
     * TODO(Sri): the **Android** app registration, `1:300960747898:android:<hex>`.
     *
     * Firebase console ▸ Project settings ▸ Your apps ▸ Add app ▸ Android, package
     * `quest.srihari.medxelite`, plus the debug SHA-1 from the README. Copy the "App ID".
     * No google-services.json is needed — [quest.srihari.medxelite.auth.FirebaseBridge] builds
     * FirebaseOptions from these constants.
     *
     * Optional: every screen runs on the Firestore/Identity Toolkit REST APIs with [API_KEY]
     * alone. When this is set, the Firebase Auth SDK is also signed in alongside (mirrors the
     * iOS MedxFirebaseBridge) so SDK-based features can be added later.
     */
    private const val ANDROID_APP_ID_FALLBACK = ""

    val googleWebClientId: String
        get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.ifBlank { GOOGLE_WEB_CLIENT_ID_FALLBACK }

    val androidAppId: String
        get() = BuildConfig.FIREBASE_ANDROID_APP_ID.ifBlank { ANDROID_APP_ID_FALLBACK }

    const val FIRESTORE_BASE =
        "https://firestore.googleapis.com/v1/projects/$PROJECT_ID/databases/(default)/documents"
    const val FIRESTORE_DOC_PREFIX = "projects/$PROJECT_ID/databases/(default)/documents"
    const val IDENTITY_BASE = "https://identitytoolkit.googleapis.com/v1/accounts"
    const val SECURE_TOKEN_BASE = "https://securetoken.googleapis.com/v1/token"

    const val IMAGE_CDN_BASE = "https://cdn.jsdelivr.net/gh/cyberromeo/img@main"
    const val FLASHCARD_CDN_BASE = "https://d2vhwjmp3pf4cn.cloudfront.net"

    /** The web app: account creation (with activation code) lives here. */
    const val WEB_APP_URL = "https://medx.srihari.quest"
    const val WEB_CREATE_ACCOUNT_URL = "https://medx.srihari.quest/login"
    const val WEB_ACTIVATE_URL = "https://medx.srihari.quest/activate"
    const val WEB_ACTIVATION_STATUS_URL = "https://medx.srihari.quest/api/activation/status"
}
