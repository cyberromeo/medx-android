package quest.srihari.medxelite.ui.faceoff

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import quest.srihari.medxelite.Medx
import quest.srihari.medxelite.data.MedxDates
import quest.srihari.medxelite.data.Question
import quest.srihari.medxelite.data.QuestionOption
import quest.srihari.medxelite.data.QuestionResponse
import quest.srihari.medxelite.data.SittingAttempt
import quest.srihari.medxelite.data.bool
import quest.srihari.medxelite.data.double
import quest.srihari.medxelite.data.int
import quest.srihari.medxelite.data.jsonOf
import quest.srihari.medxelite.data.obj
import quest.srihari.medxelite.data.objects
import quest.srihari.medxelite.data.str
import kotlin.math.ceil
import kotlin.random.Random

// Faceoff, ported from Models/Duel.swift + Services/MedxDuelRules.swift + MedxDuelRestTransport.swift.
// Same three documents and the same scoring; the one change is how two people meet: instead of
// "the other hard-coded profile", a host creates a room whose id IS a short code, and anyone
// signed in to the project joins by typing it.
//
//   medx_duels/{CODE}                host owns it, both read it
//   medx_duels/{CODE}/deck/{part}    written once at the deal
//   medx_duel_players/{CODE}__{uid}  each player writes only their own

object DuelPath {
    const val GAMES = "medx_duels"
    const val PLAYERS = "medx_duel_players"
    fun deck(gameId: String) = "medx_duels/$gameId/deck"
    fun playerDoc(gameId: String, uid: String) = "${gameId}__$uid"
}

enum class DuelStatus { LOBBY, LIVE, DONE, ABANDONED }
enum class DuelPhase { LOADING, LOBBY, ARMING, ASKED, WAITING, REVEAL, DONE, ABANDONED }

data class DuelRound(val qIndex: Int, val optionId: Int?, val remaining: Int, val correct: Boolean, val timedOut: Boolean = false) {
    val attempted get() = !timedOut && optionId != null
    fun toJson() = jsonOf(
        "qIndex" to qIndex, "remaining" to maxOf(0, remaining), "correct" to correct, "timedOut" to timedOut,
        "at" to System.currentTimeMillis().toDouble(), "optionId" to optionId,
    )

    companion object {
        fun timedOut(q: Int) = DuelRound(q, null, 0, false, true)
        fun from(o: JsonObject) = DuelRound(o.int("qIndex") ?: -1, o.int("optionId"), o.int("remaining") ?: 0, o.bool("correct") ?: false, o.bool("timedOut") ?: false)
    }
}

data class DuelPlayer(val uid: String, val profile: String, val left: Boolean, val round: DuelRound?, val next: Int?) {
    fun answer(q: Int) = round?.takeIf { it.qIndex == q }

    companion object {
        fun from(o: JsonObject): DuelPlayer? {
            val uid = o.str("uid")?.ifEmpty { null } ?: return null
            return DuelPlayer(uid, o.str("profile") ?: "", o.bool("left") ?: false, o.obj("round")?.let { DuelRound.from(it) }, o.int("next"))
        }

        fun blank(gameId: String, uid: String, profile: String) = jsonOf(
            "uid" to uid, "gameId" to gameId, "profile" to profile, "left" to false, "joinedAt" to MedxDates.stamp(),
        )
    }
}

data class AnswerRow(val optionId: Int?, val correct: Boolean, val timedOut: Boolean, val remaining: Int?, val points: Int) {
    fun toJson() = jsonOf("optionId" to optionId, "correct" to correct, "timedOut" to timedOut, "remaining" to remaining, "points" to points)
}

data class LogRow(val qIndex: Int, val questionId: Int?, val first: String?, val winner: String?, val answers: Map<String, AnswerRow>) {
    fun toJson() = jsonOf(
        "qIndex" to qIndex, "questionId" to questionId, "first" to first, "winner" to winner,
        "answers" to JsonObject(answers.mapValues { it.value.toJson() }),
    )

    companion object {
        fun from(o: JsonObject) = LogRow(
            o.int("qIndex") ?: 0, o.int("questionId"), o.str("first"), o.str("winner"),
            (o.obj("answers") ?: JsonObject(emptyMap())).mapNotNull { (uid, v) ->
                val e = v as? JsonObject ?: return@mapNotNull null
                uid to AnswerRow(e.int("optionId"), e.bool("correct") ?: false, e.bool("timedOut") ?: false, e.int("remaining"), e.int("points") ?: 0)
            }.toMap(),
        )
    }
}

data class DuelScore(var points: Int = 0, var correct: Int = 0, var answered: Int = 0, var firsts: Int = 0, var rounds: Int = 0, var fastest: Int? = null)

data class DuelGame(
    val id: String,
    val hostUid: String,
    val hostProfile: String,
    val guestUid: String?,
    val guestProfile: String?,
    val status: DuelStatus,
    val sourceName: String,
    val sourceId: String,
    val sourceSubject: String,
    val total: Int,
    val deckParts: Int,
    val qIndex: Int,
    val askedAt: Double?,
    val log: List<LogRow>,
    val createdAt: String,
    val finishedAt: String?,
) {
    companion object {
        fun from(id: String, f: JsonObject): DuelGame? {
            val host = f.str("uid")?.ifEmpty { null } ?: return null
            val src = f.obj("source")
            return DuelGame(
                id = id, hostUid = host, hostProfile = f.str("hostProfile") ?: "",
                guestUid = f.str("guestUid"), guestProfile = f.str("guestProfile"),
                status = runCatching { DuelStatus.valueOf((f.str("status") ?: "lobby").uppercase()) }.getOrDefault(DuelStatus.LOBBY),
                sourceName = src?.str("name") ?: "Faceoff", sourceId = src?.str("id") ?: "", sourceSubject = src?.str("subject") ?: "",
                total = f.int("total") ?: 0, deckParts = f.int("deckParts") ?: 1, qIndex = f.int("qIndex") ?: 0,
                askedAt = f.double("askedAt"), log = f.objects("log").map { LogRow.from(it) }.sortedBy { it.qIndex },
                createdAt = f.str("createdAt") ?: "", finishedAt = f.str("finishedAt"),
            )
        }
    }
}

object DuelRules {
    const val PER_QUESTION = 60
    const val BASE_POINTS = 40
    const val MAX_POINTS = BASE_POINTS + PER_QUESTION
    const val ARM_WINDOW_MS = 3200.0
    val LENGTHS = listOf(10, 20, 30, 0)
    private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun newCode() = (1..6).map { CODE_ALPHABET[Random.nextInt(CODE_ALPHABET.length)] }.joinToString("")
    fun now() = System.currentTimeMillis().toDouble()
    fun deadline(askedAt: Double) = askedAt + PER_QUESTION * 1000
    fun remaining(askedAt: Double, now: Double = now()) = maxOf(0, minOf(PER_QUESTION, ceil((deadline(askedAt) - now) / 1000).toInt()))
    fun score(a: DuelRound?) = if (a == null || !a.correct) 0 else BASE_POINTS + a.remaining.coerceIn(0, PER_QUESTION)

    fun shown(p: DuelPlayer?, q: Int, askedAt: Double, now: Double = now()): DuelRound? =
        p?.answer(q) ?: if (now >= deadline(askedAt)) DuelRound.timedOut(q) else null

    fun phase(game: DuelGame?, mine: DuelPlayer?, theirs: DuelPlayer?, now: Double = now()): DuelPhase {
        game ?: return DuelPhase.LOADING
        if (game.status == DuelStatus.DONE) return DuelPhase.DONE
        if (game.status == DuelStatus.ABANDONED) return DuelPhase.ABANDONED
        val askedAt = game.askedAt
        if (game.status != DuelStatus.LIVE || askedAt == null) return DuelPhase.LOBBY
        if (now < askedAt) return DuelPhase.ARMING
        val iA = mine?.answer(game.qIndex) != null
        val tA = theirs?.answer(game.qIndex) != null
        if (iA && tA) return DuelPhase.REVEAL
        if (now >= deadline(askedAt)) return DuelPhase.REVEAL
        return if (iA) DuelPhase.WAITING else DuelPhase.ASKED
    }

    fun logRow(qIndex: Int, questionId: Int?, sides: List<Pair<String, DuelRound?>>): LogRow {
        val rows = mutableMapOf<String, AnswerRow>()
        val live = mutableListOf<Pair<String, Int>>()
        for ((uid, a) in sides) {
            val attempted = a?.attempted ?: false
            val rem = if (attempted) maxOf(0, a?.remaining ?: 0) else null
            if (rem != null) live.add(uid to rem)
            rows[uid] = AnswerRow(a?.optionId, a?.correct ?: false, a?.timedOut ?: true, rem, score(a))
        }
        val first = when {
            live.size == 1 -> live[0].first
            live.size == 2 && live[0].second != live[1].second -> if (live[0].second > live[1].second) live[0].first else live[1].first
            else -> null
        }
        val best = rows.values.maxOfOrNull { it.points } ?: 0
        val top = rows.filter { it.value.points == best && best > 0 }
        return LogRow(qIndex, questionId, first, if (top.size == 1) top.keys.first() else null, rows)
    }

    fun finalScores(log: List<LogRow>, uids: List<String>): Map<String, DuelScore> {
        val out = uids.associateWith { DuelScore() }
        for (row in log) for (uid in uids) {
            val s = out[uid] ?: continue
            val a = row.answers[uid]
            s.rounds++
            s.points += a?.points ?: 0
            if (a?.correct == true) s.correct++
            if (a != null && !a.timedOut && a.optionId != null) {
                s.answered++
                if (a.correct) {
                    val took = PER_QUESTION - (a.remaining ?: 0)
                    if (s.fastest == null || took < s.fastest!!) s.fastest = took
                }
            }
            if (row.first == uid) s.firsts++
        }
        return out
    }

    fun attempt(game: DuelGame, uid: String, score: DuelScore): SittingAttempt {
        val responses = game.log.mapNotNull { row ->
            val a = row.answers[uid] ?: return@mapNotNull null
            QuestionResponse(row.questionId ?: row.qIndex, a.optionId, a.correct, a.timedOut)
        }
        val seconds = game.log.sumOf { row -> row.answers[uid]?.let { PER_QUESTION - (it.remaining ?: 0) } ?: PER_QUESTION }
        return SittingAttempt(
            id = "duel_${game.id}_$uid", uid = uid, profile = Medx.auth.current?.name, kind = "duel",
            sourceId = game.sourceId.ifEmpty { game.id }, name = game.sourceName, subject = game.sourceSubject.ifEmpty { game.sourceName },
            mode = "faceoff", gradable = true, total = maxOf(game.total, responses.size), score = score.correct,
            attempted = score.answered, durationSeconds = seconds, finishedAt = game.finishedAt ?: MedxDates.stamp(), responses = responses,
        )
    }

    /** The deck strips anything not needed to play (MedxDuelQuestion). */
    fun deckQuestion(q: Question) = jsonOf(
        "id" to q.id, "html" to (q.html ?: q.plain ?: ""), "plain" to (q.plain ?: ""),
        "options" to JsonArray(q.options.map { QuestionOption(it.id, it.label.ifEmpty { "?" }, it.text, null).toJson() }),
        "correctIds" to q.correctOptionIds, "explanation" to (q.explanation ?: ""), "reference" to (q.reference ?: ""),
    )
}
