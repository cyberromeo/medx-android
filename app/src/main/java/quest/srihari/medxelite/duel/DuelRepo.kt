package quest.srihari.medxelite.duel

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import quest.srihari.medxelite.Medx
import quest.srihari.medxelite.auth.AuthSession
import quest.srihari.medxelite.data.BankSubject
import quest.srihari.medxelite.data.Catalog
import quest.srihari.medxelite.data.Firestore
import quest.srihari.medxelite.data.Firestore.Precondition
import quest.srihari.medxelite.data.Firestore.Write
import quest.srihari.medxelite.data.Load
import quest.srihari.medxelite.data.MedxDates
import quest.srihari.medxelite.data.Question
import quest.srihari.medxelite.data.ServerClock
import quest.srihari.medxelite.data.anyToJson
import quest.srihari.medxelite.data.jsonOf
import quest.srihari.medxelite.data.objects
import java.util.UUID
import kotlin.random.Random

/** Builds `{a:{b:v}}` plus the matching update mask from dotted paths, so a merge never clobbers sibling keys. */
internal fun pathFields(paths: Map<List<String>, Any?>): Pair<JsonObject, List<List<String>>> {
    val root = mutableMapOf<String, Any?>()
    for ((path, value) in paths) {
        var node = root
        path.dropLast(1).forEach { seg ->
            @Suppress("UNCHECKED_CAST")
            node = node.getOrPut(seg) { mutableMapOf<String, Any?>() } as MutableMap<String, Any?>
        }
        node[path.last()] = value
    }
    fun conv(m: Map<String, Any?>): JsonObject = JsonObject(
        m.mapNotNull { (k, v) ->
            @Suppress("UNCHECKED_CAST")
            val e: JsonElement = when (v) {
                null -> return@mapNotNull null // in the mask but absent = delete the field
                is MutableMap<*, *> -> conv(v as Map<String, Any?>)
                else -> anyToJson(v)
            }
            k to e
        }.toMap(),
    )
    return conv(root) to paths.keys.toList()
}

internal fun update(collection: String, id: String, paths: Map<List<String>, Any?>, pre: Precondition? = null): Write.Update {
    val (fields, mask) = pathFields(paths)
    return Write.Update(collection, id, fields, mask, pre)
}

internal fun Firestore.Tx.patch(collection: String, id: String, paths: Map<List<String>, Any?>) {
    add(update(collection, id, paths))
}

/**
 * Every Firestore read and write Duels makes. All state changes other players can race on are
 * transactions or compare-and-set on the document's updateTime; each player otherwise writes only
 * their own player row, so simultaneous answers never collide.
 */
object DuelRepo {
    private val fs get() = Medx.fs
    private fun me(): AuthSession = Medx.auth.current ?: throw DuelError("Sign in to duel.")
    private fun now() = ServerClock.now()

    // ================================================================ profiles + stats

    /** Creates/refreshes publicProfiles/{uid}. No plain email: only a SHA-256 for exact-match lookup. */
    suspend fun upsertMyProfile(inDuel: String? = null, rating: Int? = null) {
        val s = me()
        val paths = mutableMapOf<List<String>, Any?>(
            listOf("uid") to s.uid,
            listOf("displayName") to s.name,
            listOf("nameLower") to s.name.lowercase(),
            listOf("searchKeys") to PublicProfile.searchKeys(s.name),
            listOf("photoUrl") to s.photoUrl,
            listOf("lastActiveAt") to now(),
            listOf("updatedAt") to MedxDates.stamp(),
            listOf("inDuel") to (inDuel ?: ""),
        )
        if (s.email.isNotBlank()) paths[listOf("emailHash")] = PublicProfile.emailHash(s.email)
        if (rating != null) paths[listOf("rating")] = rating
        fs.commit(listOf(update(DuelPaths.PROFILES, s.uid, paths)))
    }

    suspend fun profile(uid: String): PublicProfile? =
        fs.fetchDocument(DuelPaths.PROFILES, uid, useCache = false)?.let { PublicProfile.from(it) }

    /** Name: any word's prefix (2+ letters). Email: exact match only, through its hash. */
    suspend fun searchPeople(raw: String): List<PublicProfile> {
        val myUid = me().uid
        val q = raw.trim()
        if (q.contains("@")) {
            if (!q.matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))) return emptyList()
            if (q.equals(me().email, ignoreCase = true)) throw DuelError("That's your own email — you can't duel yourself.")
            return fs.runQuery(DuelPaths.PROFILES, "emailHash", PublicProfile.emailHash(q), useCache = false)
                .mapNotNull { PublicProfile.from(it) }.filter { it.uid != myUid }
        }
        val key = PublicProfile.normalizeQuery(q)
        if (key.length < 2) return emptyList()
        val docs = fs.query(
            jsonOf(
                "from" to listOf(mapOf("collectionId" to DuelPaths.PROFILES)),
                "where" to mapOf("fieldFilter" to mapOf("field" to mapOf("fieldPath" to "searchKeys"), "op" to "ARRAY_CONTAINS", "value" to mapOf("stringValue" to key))),
                "limit" to 20,
            ),
        )
        return docs.mapNotNull { PublicProfile.from(it) }.filter { it.uid != myUid }.sortedBy { it.displayName.lowercase() }
    }

    suspend fun stats(uid: String = me().uid): DuelRecord =
        recordFrom(runCatching { fs.fetchDocument(DuelPaths.STATS, uid, useCache = false) }.getOrNull())

    /** Applies a finished duel to my own stats doc exactly once (the game id is remembered). */
    suspend fun recordResult(game: DuelGameDoc): DuelRecord? {
        val uid = me().uid
        val result = game.result ?: return null
        if (uid !in game.uids) return null
        val opp = game.opponentOf(uid) ?: return null
        return fs.runTransaction { tx ->
            val raw = tx.get(DuelPaths.STATS, uid)
            val processed = processedFrom(raw?.fields)
            val current = recordFrom(raw?.fields)
            if (game.code in processed) return@runTransaction current
            val (next, _) = current.apply(result.outcome.scoreFor(uid), game.ratings[opp] ?: Elo.START, result.eloDelta[uid])
            tx.set(
                DuelPaths.STATS, uid,
                jsonOf(
                    "uid" to uid, "name" to me().name, "rating" to next.rating, "played" to next.played, "wins" to next.wins,
                    "losses" to next.losses, "draws" to next.draws, "streak" to next.streak, "bestStreak" to next.bestStreak,
                    "peak" to next.peak, "processed" to (processed + game.code).takeLast(40), "updatedAt" to MedxDates.stamp(),
                    "lastPlayedAt" to now(),
                ),
            )
            next
        }.also { r -> runCatching { upsertMyProfile(rating = r?.rating) } }
    }

    // ================================================================ dealing

    /** Picks real QBank questions for the settings. Throws a friendly DuelError when a subject is empty. */
    suspend fun dealQuestions(settings: DuelSettings, seed: Long = Random.nextLong()): List<Question> {
        Catalog.loadBanks()
        val banks = (Catalog.banks.value as? Load.Ready)?.value ?: throw DuelError("The question bank couldn't load. Check your connection.")
        val subjects: List<BankSubject> = if (settings.subjectId == null) banks.filter { it.modules.isNotEmpty() } else banks.filter { it.id == settings.subjectId }
        if (subjects.isEmpty() || subjects.all { it.modules.isEmpty() }) {
            throw DuelError("${settings.subjectName} has no questions yet. Pick another subject or Mixed.")
        }
        val rnd = Random(seed)
        val modules = if (settings.subjectId == null) {
            subjects.shuffled(rnd).take(10).mapNotNull { s -> s.modules.filter { it.questionCount > 0 }.ifEmpty { s.modules }.randomOrNull(rnd) }
        } else {
            subjects.first().modules.filter { it.questionCount > 0 }.ifEmpty { subjects.first().modules }.shuffled(rnd).take(8)
        }
        val pool = mutableListOf<Question>()
        for (m in modules) {
            if (pool.count { DuelDeck.playable(it) } >= settings.count * 3) break
            runCatching { fs.fetchModule(m.id).questions }.getOrNull()?.let { pool += it }
        }
        val deck = DuelDeck.deal(pool, settings.count, seed)
        if (deck.isEmpty()) throw DuelError("No playable questions in ${settings.subjectName} right now. Try Mixed.")
        return deck
    }

    // ================================================================ games

    private fun sideFields(s: AuthSession, record: DuelRecord): Map<List<String>, Any?> = mapOf(
        listOf("names", s.uid) to s.firstName,
        listOf("photos", s.uid) to (s.photoUrl ?: ""),
        listOf("ratings", s.uid) to record.rating,
        listOf("played", s.uid) to record.played,
    )

    private fun playerDoc(code: String, s: AuthSession, ready: Boolean = false) = jsonOf(
        "uid" to s.uid, "gameId" to code, "profile" to s.firstName, "left" to false, "joinedAt" to MedxDates.stamp(),
        "format" to DuelPaths.FORMAT, "ready" to ready, "heartbeatAt" to now(), "answers" to JsonObject(emptyMap()),
    )

    /** Deals the deck and opens a lobby whose id is the room code. */
    suspend fun createLobby(settings: DuelSettings, origin: String, questions: List<Question>, inviteId: String? = null): String {
        val s = me()
        val record = stats(s.uid)
        repeat(6) {
            val code = DuelCodes.newCode()
            val parts = questions.chunked(10)
            val stamp = MedxDates.stamp()
            val t = now()
            val game = jsonOf(
                "uid" to s.uid, "hostProfile" to s.firstName, "status" to "lobby", "code" to code, "format" to DuelPaths.FORMAT, "version" to 2,
                "source" to mapOf("kind" to "qbank", "id" to (settings.subjectId ?: "mixed"), "name" to settings.label, "subject" to settings.subjectName),
                "settings" to settings.toJson(), "total" to questions.size, "deckParts" to parts.size, "qIndex" to 0,
                "log" to JsonArray(emptyList()), "participants" to listOf(s.uid),
                "names" to mapOf(s.uid to s.firstName), "photos" to mapOf(s.uid to (s.photoUrl ?: "")),
                "ratings" to mapOf(s.uid to record.rating), "played" to mapOf(s.uid to record.played),
                "origin" to origin, "inviteId" to inviteId,
                "createdAt" to stamp, "updatedAt" to stamp, "createdAtMs" to t, "updatedAtMs" to t,
            )
            val writes = mutableListOf<Write>()
            writes += Write.Update(DuelPaths.GAMES, code, game, precondition = Precondition.Exists(false))
            parts.forEachIndexed { i, part ->
                writes += Write.Update(DuelPaths.deck(code), i.toString(), jsonOf("part" to i, "questions" to JsonArray(part.map { it.toJson() })))
            }
            writes += Write.Update(DuelPaths.PLAYERS, DuelPaths.player(code, s.uid), playerDoc(code, s))
            try {
                fs.commit(writes)
                return code
            } catch (e: Firestore.PreconditionFailed) {
                // code already taken; deal a new one
            }
        }
        throw DuelError("Couldn't open a room. Try again.")
    }

    data class MatchBundle(val game: DuelGameDoc?, val host: DuelSide?, val guest: DuelSide?, val legacy: Boolean, val exists: Boolean)

    /** One round trip: the game and both player rows, with the game's updateTime for CAS. */
    suspend fun fetchMatch(code: String, knownGuest: String? = null): MatchBundle {
        val myUid = me().uid
        val first = fs.getRaw(DuelPaths.GAMES, code) ?: return MatchBundle(null, null, null, legacy = false, exists = false)
        val game = DuelGameDoc.from(code, first.fields, first.updateTime) ?: return MatchBundle(null, null, null, legacy = true, exists = true)
        val uids = listOfNotNull(game.hostUid, game.guestUid ?: knownGuest, myUid).distinct()
        val rows = fs.getRaw(uids.map { DuelPaths.PLAYERS to DuelPaths.player(code, it) })
        fun side(uid: String?) = uid?.let { rows["${DuelPaths.PLAYERS}/${DuelPaths.player(code, it)}"]?.fields }?.let { PlayerCodec.side(it) }
        return MatchBundle(game, side(game.hostUid), side(game.guestUid), legacy = false, exists = true)
    }

    suspend fun loadDeck(code: String, parts: Int): List<Question> {
        val docs = fs.batchGet((0 until maxOf(1, parts)).map { DuelPaths.deck(code) to it.toString() })
        return (0 until maxOf(1, parts)).flatMap { p -> docs[p.toString()]?.objects("questions")?.map { Question.from(it) } ?: emptyList() }
    }

    private fun joinWrites(tx: Firestore.Tx, game: DuelGameDoc, s: AuthSession, record: DuelRecord) {
        tx.patch(
            DuelPaths.GAMES, game.code,
            sideFields(s, record) + mapOf(
                listOf("guestUid") to s.uid, listOf("guestProfile") to s.firstName,
                listOf("participants") to listOf(game.hostUid, s.uid),
                listOf("updatedAt") to MedxDates.stamp(), listOf("updatedAtMs") to now(),
            ),
        )
        tx.set(DuelPaths.PLAYERS, DuelPaths.player(game.code, s.uid), playerDoc(game.code, s))
    }

    sealed interface JoinResult {
        data class Duel(val code: String) : JoinResult
        data class Legacy(val code: String) : JoinResult
    }

    /** Join by code. A Faceoff room code is handed back so the old room screen opens it. */
    suspend fun joinByCode(raw: String): JoinResult {
        val s = me()
        val code = DuelCodes.normalize(raw)
        if (!DuelCodes.isPlausible(code)) throw DuelError("Enter the 6-character code your friend shared.")
        val record = stats(s.uid)
        return fs.runTransaction { tx ->
            val doc = tx.get(DuelPaths.GAMES, code) ?: throw DuelError("No duel with code $code.")
            val game = DuelGameDoc.from(code, doc.fields, doc.updateTime) ?: return@runTransaction JoinResult.Legacy(code)
            when {
                game.hostUid == s.uid || game.guestUid == s.uid -> Unit // rejoin my own
                game.status == MatchStatus.DONE || game.status == MatchStatus.ABANDONED -> throw DuelError("That duel has ended.")
                game.guestUid != null -> throw DuelError("That duel already has two players.")
                game.status != MatchStatus.LOBBY -> throw DuelError("That duel has already started.")
                else -> joinWrites(tx, game, s, record)
            }
            JoinResult.Duel(code)
        }
    }

    suspend fun setReady(code: String, ready: Boolean = true) {
        val uid = me().uid
        fs.commit(listOf(update(DuelPaths.PLAYERS, DuelPaths.player(code, uid), mapOf(listOf("ready") to ready, listOf("heartbeatAt") to now(), listOf("left") to false))))
    }

    suspend fun heartbeat(code: String) {
        val uid = me().uid
        fs.commit(listOf(update(DuelPaths.PLAYERS, DuelPaths.player(code, uid), mapOf(listOf("heartbeatAt") to now()))))
    }

    /** My answer, in my own row only, under answers.{q}. Each player writes a different document, so two simultaneous answers never collide. */
    suspend fun answer(code: String, a: DuelAnswer) {
        val uid = me().uid
        fs.commit(listOf(update(DuelPaths.PLAYERS, DuelPaths.player(code, uid), mapOf(listOf("answers", a.q.toString()) to PlayerCodec.answerJson(a), listOf("heartbeatAt") to now()))))
    }

    /** Compare-and-set on the game: false when the other phone got there first (which is fine). */
    suspend fun apply(game: DuelGameDoc, action: DuelAction): Boolean {
        val pre = game.updateTime?.let { Precondition.UpdatedAt(it) } ?: return false
        val stamp = MedxDates.stamp()
        val t = now()
        val paths: Map<List<String>, Any?> = when (action) {
            DuelAction.None -> return false
            is DuelAction.Start -> mapOf(listOf("status") to "live", listOf("qIndex") to 0, listOf("askedAt") to action.askedAt)
            is DuelAction.Advance -> mapOf(listOf("qIndex") to action.next, listOf("askedAt") to action.askedAt)
            is DuelAction.Finish -> mapOf(
                listOf("status") to "done", listOf("result") to DuelResultDoc.build(action.outcome, game).toJson(),
                listOf("finishedAt") to stamp, listOf("finishedAtMs") to t,
            )
        } + mapOf(listOf("updatedAt") to stamp, listOf("updatedAtMs") to t)
        return try {
            fs.commit(listOf(update(DuelPaths.GAMES, game.code, paths, pre)))
            true
        } catch (e: Firestore.PreconditionFailed) {
            false
        }
    }

    /** Leave: in the lobby the room closes; mid-match it is a forfeit (written by whichever phone sees it first). */
    suspend fun leave(code: String) {
        val uid = me().uid
        fs.commit(listOf(update(DuelPaths.PLAYERS, DuelPaths.player(code, uid), mapOf(listOf("left") to true, listOf("ready") to false, listOf("leftAt") to MedxDates.stamp()))))
        val b = fetchMatch(code)
        val g = b.game ?: return
        when (g.status) {
            MatchStatus.LOBBY -> {
                runCatching {
                    fs.commit(listOf(update(DuelPaths.GAMES, code, mapOf(listOf("status") to "abandoned", listOf("updatedAtMs") to now()), g.updateTime?.let { Precondition.UpdatedAt(it) })))
                }
                if (g.hostUid == uid) g.inviteId?.let { id -> runCatching { cancelInvite(id) } }
            }
            MatchStatus.LIVE -> {
                val action = DuelMachine.nextAction(g.snapshot, b.host, b.guest, now())
                if (action is DuelAction.Finish) apply(g, action)
            }
            else -> Unit
        }
    }

    /** The opponent vanished (no heartbeat for a minute): end the match with me as the winner. */
    suspend fun claimWin(game: DuelGameDoc, host: DuelSide?, guest: DuelSide?): Boolean {
        val uid = me().uid
        val h = host ?: DuelSide(game.hostUid, "")
        val g = guest ?: DuelSide(game.guestUid ?: "", "")
        return apply(game, DuelAction.Finish(DuelMachine.outcome(game.snapshot, h, g, EndReason.ABANDONED, forcedWinner = uid)))
    }

    /** Every duel I'm in (Faceoff rooms have no `participants`, so they never show up here). */
    suspend fun myGames(): List<DuelGameDoc> {
        val uid = me().uid
        val docs = fs.query(
            jsonOf(
                "from" to listOf(mapOf("collectionId" to DuelPaths.GAMES)),
                "where" to mapOf("fieldFilter" to mapOf("field" to mapOf("fieldPath" to "participants"), "op" to "ARRAY_CONTAINS", "value" to mapOf("stringValue" to uid))),
                "limit" to 80,
            ),
        )
        return docs.mapNotNull { d -> (d["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.let { DuelGameDoc.from(it, d) } }
            .sortedByDescending { it.finishedAtMs ?: it.updatedAtMs }
    }

    // ================================================================ invites

    suspend fun incomingInvites(): List<DuelInvite> =
        fs.runQuery(DuelPaths.INVITES, "toUid", me().uid, useCache = false).mapNotNull { DuelInvite.from(it) }.sortedByDescending { it.createdAt }

    suspend fun outgoingInvites(): List<DuelInvite> =
        fs.runQuery(DuelPaths.INVITES, "fromUid", me().uid, useCache = false).mapNotNull { DuelInvite.from(it) }.sortedByDescending { it.createdAt }

    suspend fun invite(id: String): DuelInvite? = fs.fetchDocument(DuelPaths.INVITES, id, useCache = false)?.let { DuelInvite.from(it) }

    /** Deals the deck, opens the lobby and sends the invite. The inviter then waits in that lobby. */
    suspend fun sendInvite(to: PublicProfile, settings: DuelSettings, rematchOf: String? = null): DuelInvite {
        val s = me()
        if (to.uid == s.uid) throw DuelError("You can't duel yourself. Pick a friend.")
        val t = now()
        outgoingInvites().firstOrNull { it.toUid == to.uid && it.isLive(t) }?.let {
            throw DuelError("You already have a pending invite to ${to.displayName}.")
        }
        val id = "inv_" + UUID.randomUUID().toString().replace("-", "").take(20)
        val questions = dealQuestions(settings)
        val code = createLobby(settings, "invite", questions, inviteId = id)
        val inv = DuelInvite(id, s.uid, s.firstName, s.photoUrl, to.uid, to.displayName, InviteStatus.PENDING, code, settings, t, t + DuelInvite.TTL_MS, rematchOf)
        fs.commit(
            listOf(
                Write.Update(
                    DuelPaths.INVITES, id,
                    jsonOf(
                        "id" to id, "fromUid" to s.uid, "fromName" to s.firstName, "fromPhoto" to s.photoUrl, "toUid" to to.uid,
                        "toName" to to.displayName, "toPhoto" to to.photoUrl, "status" to "pending", "gameId" to code,
                        "settings" to settings.toJson(), "createdAt" to t, "expiresAt" to inv.expiresAt,
                        "participants" to listOf(s.uid, to.uid), "rematchOf" to rematchOf, "updatedAt" to MedxDates.stamp(),
                    ),
                    precondition = Precondition.Exists(false),
                ),
            ),
        )
        return inv
    }

    /** Accept: invite pending → accepted and I take the guest seat, atomically. */
    suspend fun acceptInvite(id: String): String {
        val s = me()
        val record = stats(s.uid)
        return fs.runTransaction { tx ->
            val raw = tx.get(DuelPaths.INVITES, id) ?: throw DuelError("That invite no longer exists.")
            val inv = DuelInvite.from(raw.fields) ?: throw DuelError("That invite is damaged.")
            if (inv.toUid != s.uid) throw DuelError("That invite isn't for you.")
            when (inv.effectiveStatus(now())) {
                InviteStatus.PENDING -> Unit
                InviteStatus.ACCEPTED -> return@runTransaction inv.gameId
                InviteStatus.EXPIRED -> throw DuelError("That invite expired. Ask ${inv.fromName} to send a new one.")
                InviteStatus.CANCELLED -> throw DuelError("${inv.fromName} cancelled that invite.")
                InviteStatus.DECLINED -> throw DuelError("You already declined that invite.")
            }
            val gRaw = tx.get(DuelPaths.GAMES, inv.gameId) ?: throw DuelError("The duel room was closed.")
            val game = DuelGameDoc.from(inv.gameId, gRaw.fields, gRaw.updateTime) ?: throw DuelError("The duel room was closed.")
            if (game.status != MatchStatus.LOBBY || (game.guestUid != null && game.guestUid != s.uid)) throw DuelError("${inv.fromName} already started another duel.")
            tx.patch(DuelPaths.INVITES, id, mapOf(listOf("status") to "accepted", listOf("respondedAt") to now(), listOf("updatedAt") to MedxDates.stamp()))
            if (game.guestUid == null) joinWrites(tx, game, s, record)
            inv.gameId
        }
    }

    private suspend fun closeInvite(id: String, to: InviteStatus, mustBeMine: (DuelInvite) -> Boolean) {
        fs.runTransaction { tx ->
            val raw = tx.get(DuelPaths.INVITES, id) ?: return@runTransaction
            val inv = DuelInvite.from(raw.fields) ?: return@runTransaction
            if (!mustBeMine(inv) || inv.status != InviteStatus.PENDING) return@runTransaction
            tx.patch(DuelPaths.INVITES, id, mapOf(listOf("status") to to.raw, listOf("respondedAt") to now(), listOf("updatedAt") to MedxDates.stamp()))
            // Nobody will ever join the room the invite dealt, so the inviter closes it too.
            // (A decline only touches the invite: the recipient isn't a player of that room, and the
            // inviter's phone closes it when it sees the decline — see DuelCenter.onOutgoing.)
            val g = if (inv.fromUid == me().uid) tx.get(DuelPaths.GAMES, inv.gameId)?.let { DuelGameDoc.from(inv.gameId, it.fields) } else null
            if (g != null && g.status == MatchStatus.LOBBY && g.guestUid == null) {
                tx.patch(DuelPaths.GAMES, inv.gameId, mapOf(listOf("status") to "abandoned", listOf("updatedAtMs") to now()))
            }
        }
    }

    suspend fun declineInvite(id: String) = closeInvite(id, InviteStatus.DECLINED) { it.toUid == me().uid }
    suspend fun cancelInvite(id: String) = closeInvite(id, InviteStatus.CANCELLED) { it.fromUid == me().uid }
    suspend fun expireInvite(id: String) = closeInvite(id, InviteStatus.EXPIRED) { it.fromUid == me().uid && it.isExpired(now()) }

    // ================================================================ quick match

    suspend fun enqueue(myGame: String) {
        val s = me()
        val r = stats(s.uid)
        val t = now()
        fs.commit(
            listOf(
                Write.Update(
                    DuelPaths.QUEUE, s.uid,
                    jsonOf(
                        "uid" to s.uid, "name" to s.firstName, "photoUrl" to s.photoUrl, "rating" to r.rating, "status" to "waiting",
                        "gameId" to myGame, "createdAt" to t, "heartbeatAt" to t, "settings" to DuelSettings.QUICK.toJson(),
                    ),
                ),
            ),
        )
    }

    suspend fun queueHeartbeat() {
        fs.commit(listOf(update(DuelPaths.QUEUE, me().uid, mapOf(listOf("heartbeatAt") to now()))))
    }

    suspend fun myQueueEntry(): QueueEntry? = fs.getRaw(DuelPaths.QUEUE, me().uid)?.fields?.let { QueueEntry.from(it) }

    suspend fun queueCandidates(myRating: Int): List<QueueEntry> {
        val uid = me().uid
        val t = now()
        return fs.runQuery(DuelPaths.QUEUE, "status", "waiting", useCache = false).mapNotNull { QueueEntry.from(it) }
            .filter { it.uid != uid && t - it.heartbeatAt < 15_000 && it.gameId != null }
            .sortedWith(compareBy<QueueEntry> { ratingGap(it.rating, myRating) / 100 }.thenBy { it.createdAt })
    }

    /**
     * Claims a waiting player: their queue entry → matched, mine → matched, and I take the guest seat
     * in the room they already dealt. One transaction, so two people claiming each other (or the same
     * third person) can't both succeed. Returns the room code, or null if they were taken.
     */
    suspend fun claim(candidate: QueueEntry): String? {
        val s = me()
        val record = stats(s.uid)
        return fs.runTransaction { tx ->
            val theirs = tx.get(DuelPaths.QUEUE, candidate.uid)?.fields?.let { QueueEntry.from(it) } ?: return@runTransaction null
            val mine = tx.get(DuelPaths.QUEUE, s.uid)?.fields?.let { QueueEntry.from(it) }
            if (mine?.status == "matched" && mine.gameId != null) return@runTransaction mine.gameId // someone claimed me first
            if (theirs.status != "waiting" || now() - theirs.heartbeatAt > 15_000) return@runTransaction null
            val code = theirs.gameId ?: return@runTransaction null
            val gRaw = tx.get(DuelPaths.GAMES, code) ?: return@runTransaction null
            val game = DuelGameDoc.from(code, gRaw.fields) ?: return@runTransaction null
            if (game.status != MatchStatus.LOBBY || game.guestUid != null) return@runTransaction null
            tx.patch(DuelPaths.QUEUE, candidate.uid, mapOf(listOf("status") to "matched", listOf("matchedBy") to s.uid, listOf("matchedAt") to now()))
            tx.set(DuelPaths.QUEUE, s.uid, jsonOf("uid" to s.uid, "name" to s.firstName, "status" to "matched", "gameId" to code, "matchedBy" to s.uid, "createdAt" to now(), "heartbeatAt" to now()))
            joinWrites(tx, game, s, record)
            code
        }
    }

    /** Leaves the queue. If I was matched in the meantime, returns that room instead. */
    suspend fun leaveQueue(): String? = fs.runTransaction { tx ->
        val mine = tx.get(DuelPaths.QUEUE, me().uid)?.fields?.let { QueueEntry.from(it) }
        if (mine?.status == "matched") return@runTransaction mine.gameId
        if (mine != null) tx.delete(DuelPaths.QUEUE, me().uid)
        null
    }

    suspend fun clearQueueEntry() = runCatching { fs.deleteDocument(DuelPaths.QUEUE, me().uid) }

    suspend fun abandonLobby(code: String) {
        runCatching {
            val raw = fs.getRaw(DuelPaths.GAMES, code) ?: return
            val g = DuelGameDoc.from(code, raw.fields, raw.updateTime) ?: return
            if (g.status == MatchStatus.LOBBY && g.guestUid == null && g.hostUid == me().uid) {
                fs.commit(listOf(update(DuelPaths.GAMES, code, mapOf(listOf("status") to "abandoned", listOf("updatedAtMs") to now()), raw.updateTime?.let { Precondition.UpdatedAt(it) })))
            }
        }
    }
}

@Suppress("unused")
private val keepJsonNull = JsonNull
