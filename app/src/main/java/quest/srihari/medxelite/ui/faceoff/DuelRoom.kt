package quest.srihari.medxelite.ui.faceoff

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import quest.srihari.medxelite.Medx
import quest.srihari.medxelite.data.MedxDates
import quest.srihari.medxelite.data.Question
import quest.srihari.medxelite.data.jsonOf
import quest.srihari.medxelite.data.objects

/** Creates a room: deals the deck, writes the game (id = room code) and the host's player row. */
suspend fun createDuelRoom(questions: List<Question>, sourceKind: String, sourceId: String, name: String, subject: String): String {
    val session = Medx.auth.current ?: error("Not signed in")
    val fs = Medx.fs
    var code = DuelRules.newCode()
    var tries = 0
    while (tries++ < 5 && fs.fetchDocument(DuelPath.GAMES, code, useCache = false) != null) code = DuelRules.newCode()
    val parts = questions.chunked(25)
    parts.forEachIndexed { i, part ->
        fs.writeDocument(DuelPath.deck(code), i.toString(), jsonOf("part" to i, "questions" to JsonArray(part.map { DuelRules.deckQuestion(it) })))
    }
    val now = MedxDates.stamp()
    fs.writeDocument(
        DuelPath.GAMES, code,
        jsonOf(
            "uid" to session.uid, "hostProfile" to session.firstName, "status" to "lobby", "code" to code,
            "source" to jsonOf("kind" to sourceKind, "id" to sourceId, "name" to name, "subject" to subject),
            "total" to questions.size, "deckParts" to parts.size, "qIndex" to 0, "log" to JsonArray(emptyList()),
            "createdAt" to now, "updatedAt" to now,
        ),
    )
    fs.writeDocument(DuelPath.PLAYERS, DuelPath.playerDoc(code, session.uid), DuelPlayer.blank(code, session.uid, session.firstName))
    return code
}

/**
 * One room, polled over REST (the iOS MedxDuelRestTransport + MedxDuelRoom). Every client writes
 * only its own player row; the host alone writes the game and closes each round once both sides
 * have voted to move on.
 */
class DuelRoom(val gameId: String, private val scope: CoroutineScope) {
    private val fs get() = Medx.fs
    val uid: String = Medx.auth.uid ?: ""

    var game by mutableStateOf<DuelGame?>(null)
    var mine by mutableStateOf<DuelPlayer?>(null)
    var theirs by mutableStateOf<DuelPlayer?>(null)
    var deck by mutableStateOf<List<Question>>(emptyList())
    var phase by mutableStateOf(DuelPhase.LOADING)
    var now by mutableStateOf(DuelRules.now())
    var failure by mutableStateOf<String?>(null)
    var notFound by mutableStateOf(false)

    private var jobs = mutableListOf<Job>()
    private var timedOutFor = -1
    private var advancedFor = -1
    private var savedFor: String? = null

    val isHost get() = game?.hostUid == uid
    val qIndex get() = game?.qIndex ?: 0
    val question: Question? get() = deck.getOrNull(qIndex)
    val myAnswer get() = mine?.answer(qIndex)
    val shownMine: DuelRound? get() = game?.askedAt?.let { DuelRules.shown(mine, qIndex, it, now) }
    val shownTheirs: DuelRound? get() = game?.askedAt?.let { DuelRules.shown(theirs, qIndex, it, now) }
    val iVoted get() = mine?.next == qIndex
    val theyVoted get() = theirs?.next == qIndex
    val remaining get() = game?.askedAt?.let { DuelRules.remaining(it, now) } ?: DuelRules.PER_QUESTION
    val armingIn get() = game?.askedAt?.let { if (now < it) maxOf(1, kotlin.math.ceil((it - now) / 1000).toInt()) else 0 } ?: 0
    val uids get() = listOfNotNull(game?.hostUid, game?.guestUid ?: theirs?.uid).distinct()
    val scores: Map<String, DuelScore> get() = DuelRules.finalScores(game?.log ?: emptyList(), uids)
    val theirName: String
        get() = theirs?.profile?.ifBlank { null } ?: (if (isHost) game?.guestProfile else game?.hostProfile) ?: "Opponent"

    fun start() {
        jobs += scope.launch {
            while (isActive) {
                poll()
                delay(
                    when (phase) {
                        DuelPhase.LOADING -> 500
                        DuelPhase.LOBBY -> 2000
                        DuelPhase.ARMING -> 1200
                        DuelPhase.ASKED -> 1500
                        DuelPhase.WAITING, DuelPhase.REVEAL -> 900
                        DuelPhase.DONE, DuelPhase.ABANDONED -> 6000
                    },
                )
            }
        }
        jobs += scope.launch {
            while (isActive) {
                tick()
                delay(250)
            }
        }
    }

    fun stop() { jobs.forEach { it.cancel() }; jobs.clear() }

    private suspend fun poll() {
        try {
            val g = game
            val paths = mutableListOf(DuelPath.GAMES to gameId, DuelPath.PLAYERS to DuelPath.playerDoc(gameId, uid))
            val other = when {
                g == null -> null
                g.hostUid != uid -> g.hostUid
                else -> g.guestUid ?: theirs?.uid
            }
            if (other != null) paths += DuelPath.PLAYERS to DuelPath.playerDoc(gameId, other)
            val docs = fs.batchGet(paths)
            val gameFields = docs[gameId]
            if (gameFields == null) {
                if (game == null) notFound = true
                return
            }
            game = DuelGame.from(gameId, gameFields)
            mine = docs[DuelPath.playerDoc(gameId, uid)]?.let { DuelPlayer.from(it) }
            if (other != null) theirs = docs[DuelPath.playerDoc(gameId, other)]?.let { DuelPlayer.from(it) }
            // The host meets a guest it has never heard of: anyone with the code may join.
            val gg = game
            if (gg != null && isHost && gg.status == DuelStatus.LOBBY && theirs == null) {
                runCatching { fs.runQuery(DuelPath.PLAYERS, "gameId", gameId, useCache = false) }.getOrNull()
                    ?.mapNotNull { DuelPlayer.from(it) }
                    ?.firstOrNull { it.uid != uid && !it.left }
                    ?.let { theirs = it }
            }
            if (deck.isEmpty() && gg != null && gg.total > 0) loadDeck(gg)
            failure = null
        } catch (e: Exception) {
            failure = "Faceoff could not reach the game. Check your connection."
        }
    }

    private suspend fun loadDeck(g: DuelGame) {
        val parts = maxOf(g.deckParts, 1)
        val docs = fs.batchGet((0 until parts).map { DuelPath.deck(gameId) to it.toString() })
        deck = (0 until parts).flatMap { p -> docs[p.toString()]?.objects("questions")?.map { Question.from(it) } ?: emptyList() }
    }

    private fun tick() {
        now = DuelRules.now()
        phase = DuelRules.phase(game, mine, theirs, now)
        // Write my own timeout once the clock is out.
        if (phase == DuelPhase.REVEAL && myAnswer == null && timedOutFor != qIndex && game?.status == DuelStatus.LIVE) {
            timedOutFor = qIndex
            val round = DuelRound.timedOut(qIndex)
            mine = mine?.copy(round = round)
            scope.launch { runCatching { fs.writeDocument(DuelPath.PLAYERS, DuelPath.playerDoc(gameId, uid), jsonOf("round" to round.toJson()), merge = true) } }
        }
        advanceIfHostAndReady()
        fileAttemptIfDone()
    }

    private fun advanceIfHostAndReady() {
        val g = game ?: return
        val their = theirs?.uid ?: g.guestUid ?: return
        if (!isHost || phase != DuelPhase.REVEAL || deck.isEmpty() || !iVoted || !theyVoted || advancedFor == qIndex) return
        advancedFor = qIndex
        val row = DuelRules.logRow(qIndex, question?.id, listOf(uid to shownMine, their to shownTheirs))
        val next = qIndex + 1
        val last = next >= maxOf(g.total, deck.size)
        val log = JsonArray((g.log.filter { it.qIndex != row.qIndex } + row).map { it.toJson() })
        scope.launch {
            try {
                if (last) {
                    val sc = DuelRules.finalScores(g.log + row, listOf(uid, their))
                    fs.writeDocument(
                        DuelPath.GAMES, gameId,
                        jsonOf(
                            "log" to log, "status" to "done", "finishedAt" to MedxDates.stamp(), "updatedAt" to MedxDates.stamp(),
                            "scores" to sc.mapValues { (_, s) ->
                                mapOf("points" to s.points, "correct" to s.correct, "answered" to s.answered, "firsts" to s.firsts, "rounds" to s.rounds, "fastest" to s.fastest)
                            },
                        ),
                        merge = true,
                    )
                } else {
                    fs.writeDocument(
                        DuelPath.GAMES, gameId,
                        jsonOf("log" to log, "qIndex" to next, "askedAt" to DuelRules.now() + DuelRules.ARM_WINDOW_MS, "updatedAt" to MedxDates.stamp()),
                        merge = true,
                    )
                }
                poll()
            } catch (e: Exception) {
                advancedFor = -1
                failure = "That round would not close. Tap Next again."
            }
        }
    }

    private fun fileAttemptIfDone() {
        val g = game ?: return
        if (phase != DuelPhase.DONE || g.log.isEmpty() || savedFor == gameId) return
        savedFor = gameId
        Medx.attempts.enqueue(DuelRules.attempt(g, uid, scores[uid] ?: DuelScore()))
    }

    // ------------------------------------------------------------------ moves

    fun answer(optionId: Int) {
        val q = question ?: return
        if (phase != DuelPhase.ASKED) return
        val round = DuelRound(qIndex, optionId, remaining, q.isCorrect(optionId))
        mine = mine?.copy(round = round)
        scope.launch {
            runCatching { fs.writeDocument(DuelPath.PLAYERS, DuelPath.playerDoc(gameId, uid), jsonOf("round" to round.toJson()), merge = true) }
                .onFailure { failure = "That answer did not send. Check your connection." }
        }
    }

    fun voteNext() {
        if (phase != DuelPhase.REVEAL || iVoted) return
        val idx = qIndex
        mine = mine?.copy(next = idx)
        scope.launch { runCatching { fs.writeDocument(DuelPath.PLAYERS, DuelPath.playerDoc(gameId, uid), jsonOf("next" to idx), merge = true) } }
    }

    fun join() {
        val session = Medx.auth.current ?: return
        if (isHost || mine != null) return
        scope.launch {
            runCatching { fs.writeDocument(DuelPath.PLAYERS, DuelPath.playerDoc(gameId, uid), DuelPlayer.blank(gameId, uid, session.firstName)) }
                .onSuccess { poll() }
                .onFailure { failure = "Joining did not go through." }
        }
    }

    fun startGame() {
        val t = theirs ?: return
        if (!isHost || game?.status != DuelStatus.LOBBY || t.left) return
        scope.launch {
            runCatching {
                fs.writeDocument(
                    DuelPath.GAMES, gameId,
                    jsonOf(
                        "guestUid" to t.uid, "guestProfile" to t.profile, "status" to "live", "qIndex" to 0,
                        "askedAt" to DuelRules.now() + DuelRules.ARM_WINDOW_MS, "updatedAt" to MedxDates.stamp(),
                    ),
                    merge = true,
                )
            }.onSuccess { poll() }.onFailure { failure = "The game would not start." }
        }
    }

    suspend fun leave() {
        runCatching { fs.writeDocument(DuelPath.PLAYERS, DuelPath.playerDoc(gameId, uid), jsonOf("left" to true, "leftAt" to MedxDates.stamp()), merge = true) }
        if (isHost && game?.status != DuelStatus.DONE) {
            runCatching { fs.writeDocument(DuelPath.GAMES, gameId, jsonOf("status" to "abandoned", "updatedAt" to MedxDates.stamp()), merge = true) }
        }
        stop()
    }

    fun sourceLabel() = game?.let { "${it.sourceName} · ${it.total} questions" } ?: ""
    fun hostName() = game?.hostProfile?.ifBlank { null } ?: "Host"
}
