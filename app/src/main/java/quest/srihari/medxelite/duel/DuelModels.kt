package quest.srihari.medxelite.duel

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import quest.srihari.medxelite.data.asStringOrNull
import quest.srihari.medxelite.data.bool
import quest.srihari.medxelite.data.int
import quest.srihari.medxelite.data.jsonOf
import quest.srihari.medxelite.data.long
import quest.srihari.medxelite.data.obj
import quest.srihari.medxelite.data.str
import java.security.MessageDigest

/*
 * Firestore data model for Duels (see DUELS.md for the full schema and firestore.rules.duels.suggested).
 *
 *   medx_duels/{CODE}                 the match. Same collection and base fields as Faceoff
 *                                     (uid, hostProfile, guestUid, guestProfile, status, source, total,
 *                                     deckParts, qIndex, askedAt, log), plus format:"duel" and the new
 *                                     fields below. Faceoff docs have no `format`, so the two never mix.
 *   medx_duels/{CODE}/deck/{part}     the dealt questions, written once (10 per part)
 *   medx_duel_players/{CODE}__{uid}   one per player; each player writes only their own
 *   medx_duel_invites/{inviteId}      challenge from one user to another
 *   medx_duel_queue/{uid}             quick-match queue entry
 *   medx_duel_stats/{uid}             rating + W/L/D, written only by its owner
 *   publicProfiles/{uid}              minimal searchable profile (no plain email)
 */

object DuelPaths {
    const val GAMES = "medx_duels"
    const val PLAYERS = "medx_duel_players"
    const val INVITES = "medx_duel_invites"
    const val QUEUE = "medx_duel_queue"
    const val STATS = "medx_duel_stats"
    const val PROFILES = "publicProfiles"
    const val FORMAT = "duel"
    fun deck(code: String) = "$GAMES/$code/deck"
    fun player(code: String, uid: String) = "${code}__$uid"
}

class DuelError(message: String) : Exception(message)

data class PublicProfile(
    val uid: String,
    val displayName: String,
    val photoUrl: String? = null,
    val lastActiveAt: Long? = null,
    val inDuel: String? = null,
    val rating: Int? = null,
) {
    val initials: String get() = displayName.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase().ifEmpty { "?" }
    fun isOnline(now: Long) = lastActiveAt?.let { now - it < 3 * 60_000 } ?: false

    companion object {
        fun from(o: JsonObject) = o.str("uid")?.let {
            PublicProfile(it, o.str("displayName") ?: "MedX user", o.str("photoUrl"), o.long("lastActiveAt"), o.str("inDuel")?.ifBlank { null }, o.int("rating"))
        }

        fun emailHash(email: String): String {
            val d = MessageDigest.getInstance("SHA-256").digest(email.trim().lowercase().toByteArray(Charsets.UTF_8))
            return d.joinToString("") { "%02x".format(it) }
        }

        /** Word prefixes (2..12 chars) of the name, for `array-contains` search on any word. */
        fun searchKeys(name: String): List<String> {
            val clean = name.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").trim().replace(Regex("\\s+"), " ")
            if (clean.isBlank()) return emptyList()
            val keys = LinkedHashSet<String>()
            for (w in clean.split(" ")) for (n in 2..minOf(12, w.length)) keys += w.take(n)
            for (n in 2..minOf(20, clean.length)) keys += clean.take(n)
            return keys.toList()
        }

        fun normalizeQuery(q: String) = q.trim().lowercase().replace(Regex("\\s+"), " ").take(20)
    }
}

fun DuelSettings.toJson() = jsonOf("subjectId" to subjectId, "subjectName" to subjectName, "count" to count, "perQuestion" to perQuestion)

fun settingsFrom(o: JsonObject?): DuelSettings = if (o == null) DuelSettings() else DuelSettings(
    subjectId = o.str("subjectId")?.ifBlank { null },
    subjectName = o.str("subjectName") ?: "Mixed",
    count = o.int("count") ?: 10,
    perQuestion = o.int("perQuestion") ?: 30,
)

enum class InviteStatus(val raw: String) {
    PENDING("pending"), ACCEPTED("accepted"), DECLINED("declined"), CANCELLED("cancelled"), EXPIRED("expired");

    companion object {
        fun of(raw: String?) = entries.firstOrNull { it.raw == raw } ?: PENDING
    }
}

data class DuelInvite(
    val id: String,
    val fromUid: String,
    val fromName: String,
    val fromPhoto: String?,
    val toUid: String,
    val toName: String,
    val status: InviteStatus,
    val gameId: String,
    val settings: DuelSettings,
    val createdAt: Long,
    val expiresAt: Long,
    val rematchOf: String? = null,
) {
    fun isExpired(now: Long) = now >= expiresAt
    fun isLive(now: Long) = status == InviteStatus.PENDING && !isExpired(now)
    fun effectiveStatus(now: Long) = if (status == InviteStatus.PENDING && isExpired(now)) InviteStatus.EXPIRED else status

    companion object {
        const val TTL_MS = 10 * 60_000L

        fun from(o: JsonObject): DuelInvite? {
            val id = o.str("id") ?: return null
            return DuelInvite(
                id = id, fromUid = o.str("fromUid") ?: return null, fromName = o.str("fromName") ?: "Someone",
                fromPhoto = o.str("fromPhoto"), toUid = o.str("toUid") ?: return null, toName = o.str("toName") ?: "",
                status = InviteStatus.of(o.str("status")), gameId = o.str("gameId") ?: return null,
                settings = settingsFrom(o.obj("settings")), createdAt = o.long("createdAt") ?: 0,
                expiresAt = o.long("expiresAt") ?: 0, rematchOf = o.str("rematchOf"),
            )
        }
    }
}

/** A duel match document (format == "duel"). */
data class DuelGameDoc(
    val code: String,
    val hostUid: String,
    val guestUid: String?,
    val status: MatchStatus,
    val settings: DuelSettings,
    val total: Int,
    val deckParts: Int,
    val qIndex: Int,
    val askedAt: Long?,
    val names: Map<String, String>,
    val photos: Map<String, String>,
    val ratings: Map<String, Int>,
    val played: Map<String, Int>,
    val origin: String,
    val inviteId: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val finishedAtMs: Long?,
    val result: DuelResultDoc?,
    val updateTime: String? = null,
) {
    val snapshot get() = MatchSnapshot(status, total, settings.perQuestion, qIndex, askedAt, hostUid, guestUid)
    val uids get() = listOfNotNull(hostUid, guestUid)
    fun opponentOf(uid: String) = if (uid == hostUid) guestUid else hostUid
    fun nameOf(uid: String?) = uid?.let { names[it] }?.ifBlank { null } ?: "Opponent"

    companion object {
        fun from(code: String, f: JsonObject, updateTime: String? = null): DuelGameDoc? {
            if (f.str("format") != DuelPaths.FORMAT) return null
            val host = f.str("uid") ?: return null
            fun strMap(k: String) = f.obj(k)?.mapNotNull { (key, v) -> v.asStringOrNull()?.let { key to it } }?.toMap() ?: emptyMap()
            fun intMap(k: String) = f.obj(k)?.mapNotNull { (key, _) -> f.obj(k)?.int(key)?.let { key to it } }?.toMap() ?: emptyMap()
            return DuelGameDoc(
                code = code, hostUid = host, guestUid = f.str("guestUid")?.ifBlank { null },
                status = MatchStatus.of(f.str("status")), settings = settingsFrom(f.obj("settings")),
                total = f.int("total") ?: 0, deckParts = f.int("deckParts") ?: 1, qIndex = f.int("qIndex") ?: 0,
                askedAt = f.long("askedAt"), names = strMap("names"), photos = strMap("photos"),
                ratings = intMap("ratings"), played = intMap("played"), origin = f.str("origin") ?: "code",
                inviteId = f.str("inviteId"), createdAtMs = f.long("createdAtMs") ?: 0, updatedAtMs = f.long("updatedAtMs") ?: 0,
                finishedAtMs = f.long("finishedAtMs"), result = f.obj("result")?.let { DuelResultDoc.from(it) }, updateTime = updateTime,
            )
        }
    }
}

data class DuelResultDoc(val winner: String?, val reason: EndReason, val scores: Map<String, SideScore>, val eloDelta: Map<String, Int>) {
    fun toJson() = jsonOf(
        "winner" to (winner ?: ""), "reason" to reason.raw,
        "scores" to scores.mapValues { (_, s) -> mapOf("points" to s.points, "correct" to s.correct, "answered" to s.answered, "correctMs" to s.correctMs) },
        "eloDelta" to eloDelta,
    )

    val outcome get() = DuelOutcome(winner, reason, scores)

    companion object {
        fun from(o: JsonObject): DuelResultDoc {
            val scores = o.obj("scores")?.mapNotNull { (uid, v) ->
                val s = v as? JsonObject ?: return@mapNotNull null
                uid to SideScore(uid, s.int("points") ?: 0, s.int("correct") ?: 0, s.int("answered") ?: 0, s.long("correctMs") ?: 0)
            }?.toMap() ?: emptyMap()
            val elo = o.obj("eloDelta")?.let { e -> e.keys.mapNotNull { k -> e.int(k)?.let { k to it } }.toMap() } ?: emptyMap()
            return DuelResultDoc(o.str("winner")?.ifBlank { null }, EndReason.of(o.str("reason")), scores, elo)
        }

        /** Elo for both sides from the ratings snapshotted when the match started. */
        fun build(outcome: DuelOutcome, game: DuelGameDoc): DuelResultDoc {
            val a = game.hostUid
            val b = game.guestUid ?: ""
            val ra = game.ratings[a] ?: Elo.START
            val rb = game.ratings[b] ?: Elo.START
            val delta = mapOf(
                a to Elo.delta(ra, rb, outcome.scoreFor(a), game.played[a] ?: 10),
                b to Elo.delta(rb, ra, outcome.scoreFor(b), game.played[b] ?: 10),
            )
            return DuelResultDoc(outcome.winner, outcome.reason, outcome.scores, delta)
        }
    }
}

object PlayerCodec {
    fun side(o: JsonObject): DuelSide? {
        val uid = o.str("uid") ?: return null
        val answers = o.obj("answers")?.mapNotNull { (k, v) ->
            val a = v as? JsonObject ?: return@mapNotNull null
            val q = k.toIntOrNull() ?: return@mapNotNull null
            q to DuelAnswer(q, a.int("o"), a.bool("c") ?: false, a.long("ms") ?: 0, a.long("at") ?: 0, a.bool("t") ?: false)
        }?.toMap() ?: emptyMap()
        return DuelSide(uid, o.str("profile") ?: "", answers, o.bool("ready") ?: false, o.bool("left") ?: false, o.long("heartbeatAt"))
    }

    fun answerJson(a: DuelAnswer) = jsonOf("o" to a.optionId, "c" to a.correct, "ms" to a.ms, "at" to a.at, "t" to a.timedOut)
}

data class QueueEntry(val uid: String, val name: String, val rating: Int, val status: String, val gameId: String?, val createdAt: Long, val heartbeatAt: Long, val matchedBy: String?) {
    companion object {
        fun from(o: JsonObject) = o.str("uid")?.let {
            QueueEntry(it, o.str("name") ?: "", o.int("rating") ?: Elo.START, o.str("status") ?: "waiting", o.str("gameId"), o.long("createdAt") ?: 0, o.long("heartbeatAt") ?: 0, o.str("matchedBy"))
        }
    }
}

fun recordFrom(o: JsonObject?): DuelRecord = if (o == null) DuelRecord() else DuelRecord(
    rating = o.int("rating") ?: Elo.START, played = o.int("played") ?: 0, wins = o.int("wins") ?: 0,
    losses = o.int("losses") ?: 0, draws = o.int("draws") ?: 0, streak = o.int("streak") ?: 0,
    bestStreak = o.int("bestStreak") ?: 0, peak = o.int("peak") ?: (o.int("rating") ?: Elo.START),
)

fun processedFrom(o: JsonObject?): List<String> = (o?.get("processed") as? JsonArray)?.mapNotNull { it.asStringOrNull() } ?: emptyList()
