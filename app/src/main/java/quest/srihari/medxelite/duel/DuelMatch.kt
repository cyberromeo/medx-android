package quest.srihari.medxelite.duel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import quest.srihari.medxelite.Medx
import quest.srihari.medxelite.data.MedxDates
import quest.srihari.medxelite.data.Question
import quest.srihari.medxelite.data.QuestionResponse
import quest.srihari.medxelite.data.ServerClock
import quest.srihari.medxelite.data.SittingAttempt

/**
 * One live duel on this phone. It polls the game + both player rows (one batchGet), runs the shared
 * [DuelMachine] every 100 ms, and performs whatever compare-and-set write the machine asks for.
 * Both phones do the same, so whichever reaches Firestore first moves the match on and the other's
 * write is rejected harmlessly. Everything lives in Firestore, so killing the app and opening the
 * duel again resumes it exactly where it is.
 */
class DuelMatch(val code: String, private val scope: CoroutineScope) {
    val uid: String = Medx.auth.uid ?: ""

    var game by mutableStateOf<DuelGameDoc?>(null); private set
    var host by mutableStateOf<DuelSide?>(null); private set
    var guest by mutableStateOf<DuelSide?>(null); private set
    var deck by mutableStateOf<List<Question>>(emptyList()); private set
    var phase by mutableStateOf(Phase.LOADING); private set
    var now by mutableStateOf(ServerClock.now()); private set
    var notFound by mutableStateOf(false); private set
    var legacy by mutableStateOf(false); private set
    var offline by mutableStateOf(false); private set
    var notice by mutableStateOf<String?>(null)
    var recorded by mutableStateOf<DuelRecord?>(null); private set
    /** My pick for the current question before Firestore confirms it (instant feedback). */
    var pendingPick by mutableStateOf<DuelAnswer?>(null); private set

    private val jobs = mutableListOf<Job>()
    private var lastAttempt: String? = null
    private var lastPollOk = ServerClock.now()
    private var readySent = false
    private var savedAttempt = false

    val me: DuelSide? get() = if (game?.hostUid == uid) host else guest
    val them: DuelSide? get() = if (game?.hostUid == uid) guest else host
    val themUid: String? get() = game?.opponentOf(uid)
    val themName: String get() = game?.nameOf(themUid) ?: "Opponent"
    val myName: String get() = game?.nameOf(uid) ?: "You"
    val qIndex get() = game?.qIndex ?: 0
    val question: Question? get() = deck.getOrNull(qIndex)
    val myAnswer: DuelAnswer? get() = me?.answers?.get(qIndex) ?: pendingPick?.takeIf { it.q == qIndex }
    val theirAnswer: DuelAnswer? get() = them?.answers?.get(qIndex)
    val perQuestion get() = game?.settings?.perQuestion ?: 30
    val remainingMs: Long get() = game?.snapshot?.let { DuelMachine.remainingMs(it, now) } ?: perQuestion * 1000L
    val countdown: Int get() = game?.askedAt?.let { if (now < it) ((it - now + 999) / 1000).toInt() else 0 } ?: 0
    val opponentStale get() = phase !in setOf(Phase.FINISHED, Phase.CANCELLED, Phase.LOADING) && DuelMachine.isStale(them, now)
    val canClaimWin get() = game?.status == MatchStatus.LIVE && DuelMachine.isStale(them, now, DuelMachine.CLAIM_AFTER_MS)
    val isHost get() = game?.hostUid == uid

    /** Test/preview hook: show a fixed state without touching the network. */
    internal fun install(game: DuelGameDoc, host: DuelSide?, guest: DuelSide?, deck: List<Question>, now: Long, pick: DuelAnswer? = null) {
        this.game = game; this.host = host; this.guest = guest; this.deck = deck; this.now = now; this.pendingPick = pick
        val shownMe = me?.let { m -> pick?.let { p -> m.copy(answers = m.answers + (p.q to p)) } ?: m }
        phase = DuelMachine.phase(game.snapshot, shownMe, them, now)
    }

    fun scoreOf(side: DuelSide?): SideScore? = side?.let { s -> game?.snapshot?.let { DuelMachine.score(s, it) } }

    fun start() {
        jobs += scope.launch {
            while (isActive) {
                poll()
                delay(
                    when (phase) {
                        Phase.LOADING -> 600
                        Phase.LOBBY -> 1_200
                        Phase.COUNTDOWN, Phase.QUESTION, Phase.ANSWERED, Phase.REVEAL -> 700
                        Phase.FINISHED, Phase.CANCELLED -> 8_000
                    },
                )
            }
        }
        jobs += scope.launch {
            while (isActive) {
                tick()
                delay(100)
            }
        }
        jobs += scope.launch {
            while (isActive) {
                delay(DuelMachine.HEARTBEAT_MS)
                if (phase in setOf(Phase.LOBBY, Phase.COUNTDOWN, Phase.QUESTION, Phase.ANSWERED, Phase.REVEAL)) runCatching { DuelRepo.heartbeat(code) }
            }
        }
        DuelCenter.inMatch.value = code
    }

    fun stop() {
        jobs.forEach { it.cancel() }; jobs.clear()
        if (DuelCenter.inMatch.value == code) DuelCenter.inMatch.value = null
    }

    private suspend fun poll() {
        try {
            val b = DuelRepo.fetchMatch(code)
            lastPollOk = ServerClock.now()
            offline = false
            if (!b.exists) { if (game == null) notFound = true; return }
            if (b.legacy) { legacy = true; return }
            val g = b.game ?: return
            game = g; host = b.host; guest = b.guest
            if (pendingPick != null && me?.answers?.containsKey(pendingPick!!.q) == true) pendingPick = null
            if (deck.isEmpty() && g.total > 0) deck = DuelRepo.loadDeck(code, g.deckParts)
            // I'm in this room: mark ready once (also on rejoin after a restart).
            if (!readySent && uid in g.uids && g.status != MatchStatus.DONE && g.status != MatchStatus.ABANDONED) {
                readySent = true
                runCatching { DuelRepo.setReady(code) }.onFailure { readySent = false }
            }
            if (g.status == MatchStatus.DONE) onFinished(g)
        } catch (e: Exception) {
            if (ServerClock.now() - lastPollOk > 4_000) offline = true
        }
    }

    private fun tick() {
        now = ServerClock.now()
        val g = game
        // My own tap counts at once for what I see; the decisions below use only confirmed rows.
        val shownMe = me?.let { m -> pendingPick?.let { p -> m.copy(answers = m.answers + (p.q to p)) } ?: m }
        phase = DuelMachine.phase(g?.snapshot, shownMe, them, now)
        if (g == null || offline) return
        val action = DuelMachine.nextAction(g.snapshot, host, guest, now)
        if (action == DuelAction.None) return
        val key = "${g.status}:${g.qIndex}:${action::class.simpleName}:${g.updateTime}"
        if (key == lastAttempt) return
        lastAttempt = key
        scope.launch {
            runCatching { DuelRepo.apply(g, action) }
            poll()
        }
    }

    fun answer(optionId: Int) {
        val q = question ?: return
        val g = game ?: return
        if (phase != Phase.QUESTION || myAnswer != null) return
        val asked = g.askedAt ?: return
        val t = ServerClock.now()
        val a = DuelAnswer(qIndex, optionId, q.isCorrect(optionId), (t - asked).coerceIn(0, perQuestion * 1000L), t)
        pendingPick = a
        scope.launch {
            runCatching { DuelRepo.answer(code, a) }
                .onFailure { notice = "Your answer didn't reach the server. Retrying…"; retryAnswer(a) }
        }
    }

    private suspend fun retryAnswer(a: DuelAnswer) {
        repeat(3) {
            delay(800)
            if (runCatching { DuelRepo.answer(code, a) }.isSuccess) { notice = null; return }
        }
        notice = "Connection lost — this answer may count as a timeout."
    }

    suspend fun leave() {
        runCatching { DuelRepo.leave(code) }
        stop()
    }

    fun claimWin() {
        val g = game ?: return
        scope.launch { runCatching { DuelRepo.claimWin(g, host, guest) }; poll() }
    }

    private fun onFinished(g: DuelGameDoc) {
        if (savedAttempt || g.result == null) return
        savedAttempt = true
        scope.launch {
            recorded = runCatching { DuelRepo.recordResult(g) }.getOrNull()
            runCatching { DuelCenter.refresh(full = true) }
        }
        // Also file it in the activity log, like any other sitting.
        val mine = me ?: return
        if (deck.isEmpty()) return
        val sc = DuelMachine.score(mine, g.snapshot)
        Medx.attempts.enqueue(
            SittingAttempt(
                id = "duel2_${g.code}_$uid", uid = uid, profile = Medx.auth.current?.name, kind = "duel",
                sourceId = g.code, name = "Duel vs ${themName}", subject = g.settings.subjectName, mode = "duel", gradable = true,
                total = g.total, score = sc.correct, attempted = sc.answered,
                durationSeconds = (mine.answers.values.sumOf { it.ms } / 1000).toInt(), finishedAt = MedxDates.stamp(),
                responses = deck.mapIndexed { i, q -> val a = mine.answers[i]; QuestionResponse(q.id, a?.optionId, a?.correct == true, a == null || a.timedOut) },
            ),
        )
    }
}
