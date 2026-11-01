package quest.srihari.medxelite

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import quest.srihari.medxelite.auth.AuthRepository
import quest.srihari.medxelite.auth.FirebaseBridge
import quest.srihari.medxelite.data.ActivityStore
import quest.srihari.medxelite.data.AttemptStore
import quest.srihari.medxelite.data.CustomModuleStore
import quest.srihari.medxelite.data.DiskCache
import quest.srihari.medxelite.data.Firestore
import quest.srihari.medxelite.data.Prefs
import quest.srihari.medxelite.duel.DuelCenter
import quest.srihari.medxelite.duel.DuelRepo

/** The app's singletons (AppState / AuthService / FirestoreService / *Store .shared on iOS). */
object Medx {
    lateinit var auth: AuthRepository; private set
    lateinit var cache: DiskCache; private set
    lateinit var fs: Firestore; private set
    lateinit var prefs: Prefs; private set
    lateinit var attempts: AttemptStore; private set
    lateinit var activity: ActivityStore; private set
    lateinit var custom: CustomModuleStore; private set

    /** Background syncs must never take the app down: a failure is logged and the UI keeps its local copy. */
    private val logErrors = CoroutineExceptionHandler { _, e -> Log.e("Medx", "Background sync failed", e) }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + logErrors)

    fun init(context: Context) {
        if (::auth.isInitialized) return
        val app = context.applicationContext
        FirebaseBridge.init(app)
        auth = AuthRepository(app)
        cache = DiskCache(app)
        prefs = Prefs(app)
        fs = Firestore(cache) { auth.validIdToken() }
        val uid = { auth.uid }
        attempts = AttemptStore(app, fs, uid)
        activity = ActivityStore(app, fs, uid)
        custom = CustomModuleStore(app, fs, uid)
        auth.onSignedOut = {
            DuelCenter.reset()
            attempts.reset()
            activity.reset()
            custom.reset()
        }
        if (auth.current != null) onSignedIn()
    }

    /** Paint from what this phone kept for the user, then sync with Firestore. */
    fun onSignedIn() {
        attempts.loadLocal()
        activity.loadLocal()
        custom.loadLocal()
        scope.launch { attempts.refresh() }
        scope.launch { activity.sync() }
        scope.launch { custom.refresh() }
        // Searchable public profile for duel invites (name + photo, email only as a hash).
        scope.launch { runCatching { DuelRepo.upsertMyProfile() } }
    }
}
