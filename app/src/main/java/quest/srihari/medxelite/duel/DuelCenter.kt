package quest.srihari.medxelite.duel

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import quest.srihari.medxelite.MainActivity
import quest.srihari.medxelite.Medx
import quest.srihari.medxelite.R
import quest.srihari.medxelite.data.ServerClock

/**
 * App-wide duel state while the process is alive: incoming/outgoing invites, the duel in progress
 * (for "Rejoin"), the in-app invite prompt, local notifications, and deep links.
 *
 * "Realtime" here is adaptive polling over the Firestore REST API (the app has no Firestore SDK):
 * every 3 s in the foreground (1.5 s while an outgoing invite is pending, so the inviter sees the
 * accept almost at once), every 20 s while the process lives in the background. True push when the
 * app is killed needs FCM + a server; [DuelPush] is the hook that delivery would call.
 */
object DuelCenter {
    private const val TAG = "DuelCenter"
    private const val CHANNEL = "duel_invites"

    val incoming = MutableStateFlow<List<DuelInvite>>(emptyList())
    val outgoing = MutableStateFlow<List<DuelInvite>>(emptyList())
    /** Lobby/live duels I'm in (most recent first). */
    val active = MutableStateFlow<List<DuelGameDoc>>(emptyList())
    val history = MutableStateFlow<List<DuelGameDoc>>(emptyList())
    val record = MutableStateFlow<DuelRecord?>(null)
    /** The invite shown as an in-app dialog (anywhere but inside a match). */
    val prompt = MutableStateFlow<DuelInvite?>(null)
    /** A room to open now (deep link, accepted outgoing invite, notification tap). */
    val openRoom = MutableStateFlow<String?>(null)
    /** A room code from a link that still needs joining. */
    val pendingCode = MutableStateFlow<String?>(null)
    /** Set while a match screen is showing, so prompts don't interrupt play. */
    val inMatch = MutableStateFlow<String?>(null)
    val loaded = MutableStateFlow(false)

    @Volatile var foreground = false
    private var loop: Job? = null
    private val seen = HashSet<String>()
    private val announcedAccept = HashSet<String>()
    private val closedDeclined = HashSet<String>()
    private var appContext: Context? = null
    private var lastProfileWrite = 0L
    private var lastFull = 0L

    fun start(context: Context) {
        appContext = context.applicationContext
        ensureChannel(context)
        if (loop?.isActive == true) return
        loop = Medx.scope.launch {
            while (isActive) {
                if (Medx.auth.current != null) runCatching { refresh() }.onFailure { Log.w(TAG, "refresh failed: ${it.message}") }
                val pendingOut = outgoing.value.any { it.isLive(ServerClock.now()) }
                delay(
                    when {
                        !foreground -> 20_000
                        pendingOut -> 1_500
                        else -> 3_000
                    },
                )
            }
        }
    }

    fun stop() { loop?.cancel(); loop = null }

    fun reset() {
        incoming.value = emptyList(); outgoing.value = emptyList(); active.value = emptyList(); history.value = emptyList()
        record.value = null; prompt.value = null; openRoom.value = null; pendingCode.value = null; loaded.value = false
        seen.clear(); announcedAccept.clear(); closedDeclined.clear()
    }

    /** Pull everything now (also called by pull-to-refresh and after any duel action). */
    suspend fun refresh(full: Boolean = false) {
        val now = ServerClock.now()
        val inc = DuelRepo.incomingInvites()
        incoming.value = inc
        val out = DuelRepo.outgoingInvites()
        outgoing.value = out
        onIncoming(inc, now)
        onOutgoing(out, now)
        // Heavier reads (games, stats, profile presence) every 15 s or on demand.
        if (full || now - lastFull > 15_000 || !loaded.value) {
            lastFull = now
            val games = DuelRepo.myGames()
            active.value = games.filter { (it.status == MatchStatus.LIVE || it.status == MatchStatus.LOBBY) && now - it.updatedAtMs < 45 * 60_000 }
                .filter { g -> !(g.status == MatchStatus.LOBBY && g.guestUid == null && g.origin != "code") } // empty invite/queue lobbies are listed as invites instead
            history.value = games.filter { it.status == MatchStatus.DONE && it.result != null }
            record.value = DuelRepo.stats()
            loaded.value = true
        }
        if (now - lastProfileWrite > 60_000) {
            lastProfileWrite = now
            runCatching { DuelRepo.upsertMyProfile(inDuel = inMatch.value, rating = record.value?.rating) }
        }
    }

    private fun onIncoming(list: List<DuelInvite>, now: Long) {
        val fresh = list.filter { it.isLive(now) && seen.add(it.id) }
        if (fresh.isEmpty()) {
            // Drop a prompt whose invite was cancelled/expired meanwhile.
            prompt.value?.let { p -> if (list.none { it.id == p.id && it.isLive(now) }) prompt.value = null }
            return
        }
        val newest = fresh.maxBy { it.createdAt }
        if (foreground) {
            if (inMatch.value == null) prompt.value = newest
        } else {
            fresh.forEach { notifyInvite(it) }
        }
    }

    private suspend fun onOutgoing(list: List<DuelInvite>, now: Long) {
        for (inv in list) {
            if (inv.status == InviteStatus.ACCEPTED && now - inv.createdAt < DuelInvite.TTL_MS + 60_000 && announcedAccept.add(inv.id)) {
                // The inviter jumps straight into the room (the lobby starts once both are ready).
                if (inMatch.value != inv.gameId) {
                    if (foreground) openRoom.value = inv.gameId else notifyAccepted(inv)
                }
            }
            if (inv.status == InviteStatus.PENDING && inv.isExpired(now)) runCatching { DuelRepo.expireInvite(inv.id) }
            if (inv.status == InviteStatus.DECLINED && now - inv.createdAt < DuelInvite.TTL_MS + 60_000 && closedDeclined.add(inv.id)) {
                runCatching { DuelRepo.abandonLobby(inv.gameId) }
            }
        }
    }

    fun consumePrompt() { prompt.value = null }

    // ------------------------------------------------------------------ deep links

    /** medx://duel/CODE, medx://duel/invite/ID, https://medx.srihari.quest/duel/CODE */
    fun handleLink(uri: Uri?): Boolean {
        val link = uri?.toString()?.let { DuelCodes.parseLink(it) } ?: return false
        when (link) {
            is DuelLink.Code -> pendingCode.value = link.code
            is DuelLink.Invite -> Medx.scope.launch {
                runCatching { DuelRepo.invite(link.inviteId) }.getOrNull()?.let { inv ->
                    if (inv.isLive(ServerClock.now()) && inv.toUid == Medx.auth.uid) prompt.value = inv
                    else if (inv.status == InviteStatus.ACCEPTED) openRoom.value = inv.gameId
                }
            }
        }
        return true
    }

    fun shareText(code: String, settings: DuelSettings?): String =
        buildString {
            append("Duel me on MedX Elite")
            settings?.let { append(" — ${it.label}") }
            append(".\nRoom code: $code\nOpen in the app: medx://duel/$code")
        }

    // ------------------------------------------------------------------ notifications

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Duel invites", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "When a friend challenges you to a duel"
                },
            )
        }
    }

    private fun canNotify(c: Context) =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun pending(c: Context, uri: String, req: Int): PendingIntent {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(uri), c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun notifyInvite(inv: DuelInvite) {
        val c = appContext ?: return
        if (!canNotify(c)) return
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("${inv.fromName} challenged you to a duel")
            .setContentText("${inv.settings.label} · expires in 10 min")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setTimeoutAfter(maxOf(1_000L, inv.expiresAt - ServerClock.now()))
            .setContentIntent(pending(c, "medx://duel/invite/${inv.id}", inv.id.hashCode()))
            .build()
        runCatching { NotificationManagerCompat.from(c).notify(inv.id.hashCode(), n) }
    }

    private fun notifyAccepted(inv: DuelInvite) {
        val c = appContext ?: return
        if (!canNotify(c)) return
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("${inv.toName} accepted your duel")
            .setContentText("Tap to start — ${inv.settings.label}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending(c, "medx://duel/${inv.gameId}", inv.gameId.hashCode()))
            .build()
        runCatching { NotificationManagerCompat.from(c).notify(inv.gameId.hashCode(), n) }
    }
}

/**
 * FCM-ready hook. Nothing calls this yet: push while the app is killed needs the Firebase Messaging
 * SDK plus a server (Cloud Function on `medx_duel_invites` create) that sends
 * `{type:"duel_invite", inviteId}` / `{type:"duel_accepted", gameId}` data messages. A
 * FirebaseMessagingService would forward `onMessageReceived(data)` and `onNewToken` here.
 */
object DuelPush {
    fun onMessage(data: Map<String, String>) {
        when (data["type"]) {
            "duel_invite" -> data["inviteId"]?.let { id ->
                Medx.scope.launch { runCatching { DuelRepo.invite(id) }.getOrNull()?.let { DuelCenter.notifyInvite(it) } }
            }
            "duel_accepted" -> data["gameId"]?.let { DuelCenter.openRoom.value = it }
        }
    }

    /** Would store the token on publicProfiles/{uid}.fcmTokens for the server to target. */
    fun onNewToken(token: String) {
        Log.i("DuelPush", "FCM token received (${token.take(8)}…); server-side push not wired yet")
    }
}
