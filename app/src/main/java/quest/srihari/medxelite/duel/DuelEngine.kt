package quest.srihari.medxelite.duel

import quest.srihari.medxelite.data.Question
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

// Pure duel rules: no Android, no network, no clock of its own (every function takes `now`).
// Both phones run exactly this code over the same documents, so they always agree on the phase,
// the points and the winner. Unit-tested in DuelEngineTest.

/** Settings a challenger picks. `subjectId == null` means mixed (every subject). */
data class DuelSettings(
    val subjectId: String? = null,
    val subjectName: String = "Mixed",
    val count: Int = 10,
    val perQuestion: Int = 30,
) {
    val label: String get() = "$subjectName · $count Qs · ${perQuestion}s each"

    companion object {
        val COUNTS = listOf(5, 10, 20)
        val TIMES = listOf(15, 30, 45, 60)
        val QUICK = DuelSettings(null, "Mixed", 10, 20)
    }
}

/** One player's answer to one question. `ms` = time taken from the moment the question opened. */
data class DuelAnswer(
    val q: Int,
    val optionId: Int?,
    val correct: Boolean,
    val ms: Long,
    val at: Long,
    val timedOut: Boolean = false,
) {
    fun points(perQuestion: Int) = DuelScoring.points(correct && !timedOut, ms, perQuestion)
}

object DuelScoring {
    /** A right answer is worth 100, plus up to 100 more for speed (linear in the time left). */
    const val BASE = 100
    const val SPEED_MAX = 100
    const val MAX = BASE + SPEED_MAX

    fun points(correct: Boolean, ms: Long, perQuestion: Int): Int {
        if (!correct) return 0
        val limit = perQuestion * 1000L
        val left = (limit - ms.coerceIn(0, limit)).toDouble() / limit
        return BASE + (SPEED_MAX * left).roundToInt()
    }
}

/** Standard Elo, K = 40 for the first 10 duels (find your level fast), then 24. */
object Elo {
    const val START = 1200

    fun expected(rating: Int, opponent: Int): Double = 1.0 / (1.0 + 10.0.pow((opponent - rating) / 400.0))

    fun k(played: Int) = if (played < 10) 40 else 24

    /** `score`: 1 win, 0.5 draw, 0 loss. */
    fun delta(rating: Int, opponent: Int, score: Double, played: Int = 10): Int =
        (k(played) * (score - expected(rating, opponent))).roundToInt()
}

data class DuelSide(
    val uid: String,
    val name: String,
    val answers: Map<Int, DuelAnswer> = emptyMap(),
    val ready: Boolean = false,
    val left: Boolean = false,
    val heartbeatAt: Long? = null,
)

enum class MatchStatus(val raw: String) {
    LOBBY("lobby"), LIVE("live"), DONE("done"), ABANDONED("abandoned");

    companion object {
        fun of(raw: String?) = entries.firstOrNull { it.raw == raw } ?: LOBBY
    }
}

enum class Phase { LOADING, LOBBY, COUNTDOWN, QUESTION, ANSWERED, REVEAL, FINISHED, CANCELLED }

data class SideScore(val uid: String, val points: Int, val correct: Int, val answered: Int, val correctMs: Long)

enum class EndReason(val raw: String) {
    COMPLETE("complete"), FORFEIT("forfeit"), ABANDONED("abandoned");

    companion object {
        fun of(raw: String?) = entries.firstOrNull { it.raw == raw } ?: COMPLETE
    }
}

data class DuelOutcome(val winner: String?, val reason: EndReason, val scores: Map<String, SideScore>) {
    val isDraw get() = winner == null
    fun scoreFor(uid: String): Double = when (winner) {
        null -> 0.5
        uid -> 1.0
        else -> 0.0
    }
}

/** What a client should write next, if anything. Every write is compare-and-set, so it's safe for both to try. */
sealed interface DuelAction {
    data object None : DuelAction
    data class Start(val askedAt: Long) : DuelAction
    data class Advance(val next: Int, val askedAt: Long) : DuelAction
    data class Finish(val outcome: DuelOutcome) : DuelAction
}

/** The live state both phones read. */
data class MatchSnapshot(
    val status: MatchStatus,
    val total: Int,
    val perQuestion: Int,
    val qIndex: Int,
    val askedAt: Long?,
    val hostUid: String,
    val guestUid: String?,
)

object DuelMachine {
    /** Synced pre-match countdown, and the short "get ready" before each later question. */
    const val START_COUNTDOWN_MS = 4_000L
    const val NEXT_GAP_MS = 1_200L
    /** How long the answer stays revealed before the next question. */
    const val REVEAL_MS = 2_600L
    /** No heartbeat for this long and the opponent shows as disconnected. */
    const val STALE_MS = 20_000L
    /** ...and after this long you may claim the win. */
    const val CLAIM_AFTER_MS = 60_000L
    const val HEARTBEAT_MS = 5_000L

    fun deadline(s: MatchSnapshot): Long? = s.askedAt?.let { it + s.perQuestion * 1000L }

    fun remainingMs(s: MatchSnapshot, now: Long): Long = deadline(s)?.let { (it - now).coerceIn(0, s.perQuestion * 1000L) } ?: s.perQuestion * 1000L

    /** When the reveal of the current question ends: both answered → last answer + reveal; else deadline + reveal. */
    fun revealEndsAt(s: MatchSnapshot, a: DuelSide?, b: DuelSide?): Long? {
        val d = deadline(s) ?: return null
        val ma = a?.answers?.get(s.qIndex)
        val mb = b?.answers?.get(s.qIndex)
        val closed = if (ma != null && mb != null) minOf(d, maxOf(ma.at, mb.at)) else d
        return closed + REVEAL_MS
    }

    fun phase(s: MatchSnapshot?, me: DuelSide?, them: DuelSide?, now: Long): Phase {
        s ?: return Phase.LOADING
        return when (s.status) {
            MatchStatus.DONE -> Phase.FINISHED
            MatchStatus.ABANDONED -> Phase.CANCELLED
            MatchStatus.LOBBY -> Phase.LOBBY
            MatchStatus.LIVE -> {
                val asked = s.askedAt ?: return Phase.LOBBY
                if (now < asked) return Phase.COUNTDOWN
                val mine = me?.answers?.get(s.qIndex)
                val theirs = them?.answers?.get(s.qIndex)
                val d = asked + s.perQuestion * 1000L
                when {
                    (mine != null && theirs != null) || now >= d -> Phase.REVEAL
                    mine != null -> Phase.ANSWERED
                    else -> Phase.QUESTION
                }
            }
        }
    }

    fun canStart(s: MatchSnapshot, host: DuelSide?, guest: DuelSide?) =
        s.status == MatchStatus.LOBBY && s.guestUid != null && host?.ready == true && guest?.ready == true && !host.left && !guest.left

    /** The single decision both phones make every tick. */
    fun nextAction(s: MatchSnapshot?, host: DuelSide?, guest: DuelSide?, now: Long): DuelAction {
        s ?: return DuelAction.None
        if (s.status == MatchStatus.LOBBY) {
            return if (canStart(s, host, guest)) DuelAction.Start(now + START_COUNTDOWN_MS) else DuelAction.None
        }
        if (s.status != MatchStatus.LIVE) return DuelAction.None
        // A player who leaves forfeits on the spot.
        val quitter = listOfNotNull(host, guest).firstOrNull { it.left }
        if (quitter != null && host != null && guest != null) {
            val winner = if (quitter.uid == host.uid) guest.uid else host.uid
            return DuelAction.Finish(outcome(s, host, guest, EndReason.FORFEIT, forcedWinner = winner))
        }
        val ends = revealEndsAt(s, host, guest) ?: return DuelAction.None
        if (now < ends) return DuelAction.None
        val next = s.qIndex + 1
        return if (next >= s.total) {
            DuelAction.Finish(outcome(s, host ?: DuelSide(s.hostUid, ""), guest ?: DuelSide(s.guestUid ?: "", "")))
        } else {
            DuelAction.Advance(next, now + NEXT_GAP_MS)
        }
    }

    fun score(side: DuelSide, s: MatchSnapshot, upTo: Int = s.total): SideScore {
        val list = side.answers.values.filter { it.q < upTo }
        val right = list.filter { it.correct && !it.timedOut }
        return SideScore(
            uid = side.uid,
            points = list.sumOf { it.points(s.perQuestion) },
            correct = right.size,
            answered = list.count { !it.timedOut && it.optionId != null },
            correctMs = right.sumOf { it.ms },
        )
    }

    /** Most points wins; then most correct; then less time spent on correct answers; else a draw. */
    fun outcome(s: MatchSnapshot, a: DuelSide, b: DuelSide, reason: EndReason = EndReason.COMPLETE, forcedWinner: String? = null): DuelOutcome {
        val sa = score(a, s)
        val sb = score(b, s)
        val winner = forcedWinner ?: when {
            sa.points != sb.points -> if (sa.points > sb.points) a.uid else b.uid
            sa.correct != sb.correct -> if (sa.correct > sb.correct) a.uid else b.uid
            sa.correct > 0 && sa.correctMs != sb.correctMs -> if (sa.correctMs < sb.correctMs) a.uid else b.uid
            else -> null
        }
        return DuelOutcome(winner, reason, mapOf(a.uid to sa, b.uid to sb))
    }

    fun isStale(side: DuelSide?, now: Long, after: Long = STALE_MS) = side?.heartbeatAt?.let { now - it > after } ?: false
}

/** Deals one identical deck from a pool: valid MCQs only, de-duplicated, shuffled by a seed. */
object DuelDeck {
    fun playable(q: Question) = q.options.size >= 2 && q.hasAnswerKey && q.displayText.isNotBlank()

    fun deal(pool: List<Question>, count: Int, seed: Long): List<Question> {
        val seen = HashSet<Int>()
        val unique = pool.filter { playable(it) && seen.add(it.id) }
        return unique.shuffled(Random(seed)).take(count)
    }
}

/** Room codes avoid look-alike characters (no 0/O, 1/I/L). */
object DuelCodes {
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    fun newCode(random: Random = Random.Default) = (1..6).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
    fun normalize(raw: String) = raw.uppercase().filter { it.isLetterOrDigit() }.take(8)
    fun isPlausible(code: String) = code.length in 4..8

    /** medx://duel/CODE, medx://duel/invite/ID and https://medx.srihari.quest/duel/CODE. */
    fun parseLink(uri: String): DuelLink? {
        val u = uri.trim()
        val path = when {
            u.startsWith("medx://duel/", ignoreCase = true) -> u.substring("medx://duel/".length)
            u.contains("medx.srihari.quest/duel/", ignoreCase = true) -> u.substringAfter("/duel/")
            else -> return null
        }.substringBefore("?").trim('/')
        if (path.startsWith("invite/")) return path.removePrefix("invite/").takeIf { it.isNotBlank() }?.let { DuelLink.Invite(it) }
        val code = normalize(path)
        return if (isPlausible(code)) DuelLink.Code(code) else null
    }
}

sealed interface DuelLink {
    data class Code(val code: String) : DuelLink
    data class Invite(val inviteId: String) : DuelLink
}

/** Win/loss bookkeeping for the stats doc, pure so it can be tested. */
data class DuelRecord(
    val rating: Int = Elo.START,
    val played: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    /** +n = n wins in a row, -n = n losses in a row. */
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val peak: Int = Elo.START,
) {
    val winRate: Float get() = if (played == 0) 0f else wins.toFloat() / played

    fun apply(score: Double, opponentRating: Int, fixedDelta: Int? = null): Pair<DuelRecord, Int> {
        val d = fixedDelta ?: Elo.delta(rating, opponentRating, score, played)
        val newStreak = when {
            score == 1.0 -> if (streak > 0) streak + 1 else 1
            score == 0.0 -> if (streak < 0) streak - 1 else -1
            else -> 0
        }
        val r = rating + d
        return copy(
            rating = r, played = played + 1,
            wins = wins + if (score == 1.0) 1 else 0,
            losses = losses + if (score == 0.0) 1 else 0,
            draws = draws + if (score == 0.5) 1 else 0,
            streak = newStreak, bestStreak = maxOf(bestStreak, newStreak), peak = maxOf(peak, r),
        ) to d
    }
}

fun ratingGap(a: Int, b: Int) = abs(a - b)
