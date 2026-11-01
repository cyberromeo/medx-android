package quest.srihari.medxelite.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private fun Context.medxPrefs() = getSharedPreferences("medx.prefs", Context.MODE_PRIVATE)

// ---------------------------------------------------------------- Theme + goals

enum class MedxAccent(val label: String, val hex: Long) {
    // Nothing red is the default. Older entries stay so saved preferences keep parsing.
    RED("Nothing red", 0xFFD71921),
    BLUE("Blue", 0xFF0A84FF), INDIGO("Indigo", 0xFF5E5CE6), PURPLE("Purple", 0xFFBF5AF2),
    PINK("Pink", 0xFFFF375F), TEAL("Teal", 0xFF40C8E0), GREEN("Green", 0xFF30D158),
    ORANGE("Orange", 0xFFFF9F0A), GRAPHITE("Graphite", 0xFF98989D),
}

enum class MedxAppearance(val label: String) { SYSTEM("Automatic"), LIGHT("Light"), DARK("Dark") }

class Prefs(context: Context) {
    private val p = context.medxPrefs()

    val accent = MutableStateFlow(runCatching { MedxAccent.valueOf(p.getString("accent", "RED")!!) }.getOrDefault(MedxAccent.RED))
    val appearance = MutableStateFlow(runCatching { MedxAppearance.valueOf(p.getString("appearance", "DARK")!!) }.getOrDefault(MedxAppearance.DARK))
    val dailyGoal = MutableStateFlow(p.getInt("goal", 50).takeIf { it > 0 } ?: 50)
    val examName = MutableStateFlow(p.getString("examName", "FMGE") ?: "FMGE")
    val examDate = MutableStateFlow(
        p.getString("examDate", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.of(2027, 1, 9),
    )
    val qbankBank = MutableStateFlow(p.getString("qbankBank", "arise") ?: "arise")
    val hapticsOn = MutableStateFlow(p.getBoolean("haptics", true))

    fun setAccent(a: MedxAccent) { accent.value = a; p.edit().putString("accent", a.name).apply() }
    fun setAppearance(a: MedxAppearance) { appearance.value = a; p.edit().putString("appearance", a.name).apply() }
    fun setGoal(g: Int) { dailyGoal.value = g; p.edit().putInt("goal", g).apply() }
    fun setExam(name: String, date: LocalDate) {
        examName.value = name; examDate.value = date
        p.edit().putString("examName", name).putString("examDate", date.toString()).apply()
    }
    fun setBank(raw: String) { qbankBank.value = raw; p.edit().putString("qbankBank", raw).apply() }
    fun setHaptics(on: Boolean) { hapticsOn.value = on; p.edit().putBoolean("haptics", on).apply() }
}

// ---------------------------------------------------------------- Study stats (MedxStudyStatsStore)

data class RevisionDue(val moduleId: String, val name: String, val subject: String, val sittings: Int, val dueDate: LocalDate)

data class StudyStats(
    val answeredToday: Int = 0,
    val correctToday: Int = 0,
    val streakDays: Int = 0,
    val sittings: Int = 0,
    val overallAccuracy: Int? = null,
    val weekSittings: Int = 0,
    val weekAnswered: Int = 0,
    val weekAccuracy: Int? = null,
    val weeklyAnswered: List<Int> = List(7) { 0 },
    val due: List<RevisionDue> = emptyList(),
    val recentAccuracy: List<Int> = emptyList(),
    val loaded: Boolean = false,
)

object StatsMath {
    private val intervals = listOf(1, 3, 7, 21, 45)

    fun compute(list: List<SittingAttempt>, zone: ZoneId = ZoneId.systemDefault()): StudyStats {
        val today = LocalDate.now(zone)
        val weekAgo = today.minusDays(7)
        var answered = 0
        var correct = 0
        val buckets = IntArray(7)
        val activeDays = mutableSetOf<LocalDate>()
        var attemptedAll = 0
        var correctAll = 0
        var weekS = 0
        var weekA = 0
        var weekC = 0
        data class Mod(var sittings: Int, var last: LocalDate, var name: String, var subject: String)
        val byModule = mutableMapOf<String, Mod>()
        for (a in list) {
            attemptedAll += a.attempted
            correctAll += a.score
            val finished = a.finishedInstant ?: continue
            val day = finished.atZone(zone).toLocalDate()
            if (!day.isBefore(weekAgo)) { weekS++; weekA += a.attempted; weekC += a.score }
            activeDays.add(day)
            if (day == today) { answered += a.attempted; correct += a.score }
            val offset = ChronoUnit.DAYS.between(day, today).toInt()
            if (offset in 0..6) buckets[6 - offset] += a.attempted
            if (a.kind == "qbank" && MedxBank.isModuleId(a.sourceId)) {
                val m = byModule.getOrPut(a.sourceId) { Mod(0, LocalDate.MIN, a.name, a.subject ?: "") }
                m.sittings++
                if (day.isAfter(m.last)) m.last = day
            }
        }
        var streak = 0
        if (activeDays.isNotEmpty()) {
            var cursor = today
            if (!activeDays.contains(cursor)) cursor = cursor.minusDays(1)
            while (activeDays.contains(cursor)) { streak++; cursor = cursor.minusDays(1) }
        }
        val due = byModule.mapNotNull { (id, m) ->
            val step = (m.sittings.coerceIn(1, intervals.size)) - 1
            val dueDate = m.last.plusDays(intervals[step].toLong())
            if (dueDate.isAfter(today)) null else RevisionDue(id, m.name, m.subject, m.sittings, dueDate)
        }.sortedBy { it.dueDate }
        val recent = list.filter { it.attempted > 0 && it.finishedInstant != null }
            .sortedBy { it.finishedInstant }.takeLast(12).map { it.accuracyPercentage }
        return StudyStats(
            answeredToday = answered, correctToday = correct, streakDays = streak, sittings = list.size,
            overallAccuracy = if (attemptedAll > 0) Math.round(correctAll * 100.0 / attemptedAll).toInt() else null,
            weekSittings = weekS, weekAnswered = weekA,
            weekAccuracy = if (weekA > 0) Math.round(weekC * 100.0 / weekA).toInt() else null,
            weeklyAnswered = buckets.toList(), due = due, recentAccuracy = recent, loaded = true,
        )
    }
}

// ---------------------------------------------------------------- Attempts + outbox

/**
 * Finished sittings: written to a local outbox first (counted at once), uploaded when the network
 * allows (MedxAttemptOutbox.swift). Keyed by uid, so two people on one phone never mix.
 */
class AttemptStore(
    context: Context,
    private val fs: Firestore,
    private val uidProvider: () -> String?,
) {
    private val p = context.getSharedPreferences("medx.attempts", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    val attempts = MutableStateFlow<List<SittingAttempt>>(emptyList())
    val stats = MutableStateFlow(StudyStats())
    val syncing = MutableStateFlow(false)
    val lastError = MutableStateFlow<String?>(null)

    private fun outboxKey(uid: String) = "outbox.$uid"
    private fun cacheKey(uid: String) = "cache.$uid"

    private fun readList(key: String): List<SittingAttempt> = p.getString(key, null)?.let { text ->
        runCatching { MedxJson.parseToJsonElement(text).jsonArray.map { SittingAttempt.from(it.jsonObject) } }.getOrNull()
    } ?: emptyList()

    private fun writeList(key: String, list: List<SittingAttempt>) {
        p.edit().putString(key, JsonArray(list.map { it.toJson() }).toString()).apply()
    }

    fun pending(uid: String) = readList(outboxKey(uid))

    fun isPending(a: SittingAttempt): Boolean {
        val uid = uidProvider() ?: return false
        return pending(uid).any { it.id == a.id }
    }

    /** Paint from the last copy kept on this phone. */
    fun loadLocal() {
        val uid = uidProvider() ?: return
        val merged = merge(readList(cacheKey(uid)), pending(uid))
        publish(merged)
    }

    private fun merge(remote: List<SittingAttempt>, pending: List<SittingAttempt>): List<SittingAttempt> {
        val ids = remote.mapNotNull { it.id }.toSet()
        return remote + pending.filter { it.id == null || it.id !in ids }
    }

    private fun publish(list: List<SittingAttempt>) {
        val sorted = list.sortedByDescending { it.finishedInstant ?: Instant.EPOCH }
        attempts.value = sorted
        stats.value = StatsMath.compute(sorted)
    }

    fun enqueue(attempt: SittingAttempt) {
        val uid = attempt.uid.ifEmpty { return }
        val row = if (attempt.id.isNullOrEmpty()) attempt.copy(id = SittingAttempt.newId()) else attempt
        val list = pending(uid).filter { it.id != row.id } + row
        writeList(outboxKey(uid), list)
        publish(attempts.value.filter { it.id != row.id } + row)
        scope.launch { flush() }
    }

    suspend fun flush() {
        val uid = uidProvider() ?: return
        mutex.withLock {
            for (row in pending(uid)) {
                try {
                    fs.saveAttempt(row)
                    writeList(outboxKey(uid), pending(uid).filter { it.id != row.id })
                } catch (e: Exception) {
                    lastError.value = e.message
                    break
                }
            }
        }
    }

    /** Flush the outbox, then read every attempt of this user from Firestore. */
    suspend fun refresh() {
        val uid = uidProvider() ?: return
        syncing.value = true
        try {
            flush()
            val remote = fs.fetchAttempts(uid)
            writeList(cacheKey(uid), remote)
            publish(merge(remote, pending(uid)))
            lastError.value = null
        } catch (e: Exception) {
            lastError.value = e.message
            if (attempts.value.isEmpty()) loadLocal()
        } finally {
            syncing.value = false
        }
    }

    fun delete(a: SittingAttempt) {
        val uid = uidProvider() ?: return
        writeList(outboxKey(uid), pending(uid).filter { it.id != a.id })
        writeList(cacheKey(uid), readList(cacheKey(uid)).filter { it.id != a.id })
        publish(attempts.value.filter { it.id != a.id })
        val id = a.id ?: return
        scope.launch { runCatching { fs.deleteAttempt(id) } }
    }

    fun reset() {
        attempts.value = emptyList()
        stats.value = StudyStats()
    }
}

// ---------------------------------------------------------------- Bookmarks + watch history

class ActivityStore(
    context: Context,
    private val fs: Firestore,
    private val uidProvider: () -> String?,
) {
    private val p = context.getSharedPreferences("medx.activity", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val bookmarks = MutableStateFlow<List<BookmarkedQuestion>>(emptyList())
    val history = MutableStateFlow<List<WatchHistoryEntry>>(emptyList())
    val syncing = MutableStateFlow(false)
    private var historyJob: Job? = null

    private fun key(kind: String, uid: String) = "$kind.$uid"

    fun loadLocal() {
        val uid = uidProvider() ?: return
        bookmarks.value = p.getString(key("bookmarks", uid), null)?.let { t ->
            runCatching { MedxJson.parseToJsonElement(t).jsonArray.mapNotNull { BookmarkedQuestion.from(it.jsonObject) } }.getOrNull()
        } ?: emptyList()
        history.value = p.getString(key("history", uid), null)?.let { t ->
            runCatching { MedxJson.parseToJsonElement(t).jsonArray.mapNotNull { WatchHistoryEntry.from(it.jsonObject) } }.getOrNull()
        } ?: emptyList()
    }

    private fun saveLocal() {
        val uid = uidProvider() ?: return
        p.edit()
            .putString(key("bookmarks", uid), JsonArray(bookmarks.value.map { it.toJson() }).toString())
            .putString(key("history", uid), JsonArray(history.value.map { it.toJson() }).toString())
            .apply()
    }

    private fun tombstones(uid: String): MutableSet<String> = p.getStringSet(key("tomb", uid), emptySet())!!.toMutableSet()
    private fun pendingSet(uid: String): MutableSet<String> = p.getStringSet(key("pending", uid), emptySet())!!.toMutableSet()

    suspend fun sync() {
        val uid = uidProvider() ?: return
        syncing.value = true
        try {
            val tomb = tombstones(uid)
            val pend = pendingSet(uid)
            // Push what never made it up, and finish deletions.
            for (b in bookmarks.value.filter { it.docId in pend }) {
                runCatching { fs.saveBookmark(b) }.onSuccess { pend.remove(b.docId) }
            }
            for (h in history.value.filter { it.docId in pend }) {
                runCatching { fs.saveWatchHistory(h) }.onSuccess { pend.remove(h.docId) }
            }
            for (doc in tomb.toList()) {
                val ok = runCatching { fs.deleteDocument("medx_bookmarks", doc) }.isSuccess &&
                    runCatching { fs.deleteDocument("medx_watch_history", doc) }.isSuccess
                if (ok) tomb.remove(doc)
            }
            p.edit().putStringSet(key("tomb", uid), tomb).putStringSet(key("pending", uid), pend).apply()

            val remoteB = fs.fetchBookmarks(uid).filter { it.docId !in tomb }
            val localOnly = bookmarks.value.filter { b -> remoteB.none { it.docId == b.docId } && b.docId in pend }
            bookmarks.value = (remoteB + localOnly).distinctBy { it.docId }.sortedByDescending { it.bookmarkedAt }

            val remoteH = fs.fetchWatchHistory(uid).filter { it.docId !in tomb }
            val merged = (history.value + remoteH).groupBy { it.video.id }.map { (_, rows) -> rows.maxBy { it.watchedAt } }
            history.value = merged.sortedByDescending { it.watchedAt }
            saveLocal()
        } catch (_: Exception) {
        } finally {
            syncing.value = false
        }
    }

    fun isBookmarked(questionId: Int, sourceId: String): Boolean =
        bookmarks.value.any { it.question.id == questionId && it.sourceId == sourceId }

    fun toggleBookmark(question: Question, sourceId: String, sourceName: String, subject: String) {
        val uid = uidProvider() ?: return
        val existing = bookmarks.value.firstOrNull { it.question.id == question.id && it.sourceId == sourceId }
        if (existing != null) {
            remove(existing)
            return
        }
        val b = BookmarkedQuestion(sourceId, uid, sourceName, subject, question, MedxDates.stamp())
        bookmarks.value = listOf(b) + bookmarks.value
        val pend = pendingSet(uid).apply { add(b.docId) }
        val tomb = tombstones(uid).apply { remove(b.docId) }
        p.edit().putStringSet(key("pending", uid), pend).putStringSet(key("tomb", uid), tomb).apply()
        saveLocal()
        scope.launch {
            runCatching { fs.saveBookmark(b) }.onSuccess {
                p.edit().putStringSet(key("pending", uid), pendingSet(uid).apply { remove(b.docId) }).apply()
            }
        }
    }

    fun remove(b: BookmarkedQuestion) {
        val uid = uidProvider() ?: return
        bookmarks.value = bookmarks.value.filter { it.docId != b.docId }
        p.edit()
            .putStringSet(key("pending", uid), pendingSet(uid).apply { remove(b.docId) })
            .putStringSet(key("tomb", uid), tombstones(uid).apply { add(b.docId) })
            .apply()
        saveLocal()
        scope.launch { runCatching { fs.deleteDocument("medx_bookmarks", b.docId) } }
    }

    fun clearBookmarks() {
        bookmarks.value.toList().forEach { remove(it) }
    }

    fun historyEntry(videoId: String) = history.value.firstOrNull { it.video.id == videoId }

    /** Recorded locally at once; uploaded at most every few seconds while a class plays. */
    fun recordProgress(video: RecordedVideo, positionSec: Double, durationSec: Double) {
        val uid = uidProvider() ?: return
        if (positionSec < 1) return
        val entry = WatchHistoryEntry(video, uid, positionSec, durationSec, MedxDates.stamp())
        history.value = listOf(entry) + history.value.filter { it.video.id != video.id }
        saveLocal()
        p.edit().putStringSet(key("pending", uid), pendingSet(uid).apply { add(entry.docId) }).apply()
        historyJob?.cancel()
        historyJob = scope.launch {
            delay(4000)
            runCatching { fs.saveWatchHistory(entry) }.onSuccess {
                p.edit().putStringSet(key("pending", uid), pendingSet(uid).apply { remove(entry.docId) }).apply()
            }
        }
    }

    fun removeHistory(e: WatchHistoryEntry) {
        val uid = uidProvider() ?: return
        history.value = history.value.filter { it.video.id != e.video.id }
        p.edit().putStringSet(key("tomb", uid), tombstones(uid).apply { add(e.docId) }).apply()
        saveLocal()
        scope.launch { runCatching { fs.deleteDocument("medx_watch_history", e.docId) } }
    }

    fun clearHistory() = history.value.toList().forEach { removeHistory(it) }

    fun reset() {
        bookmarks.value = emptyList()
        history.value = emptyList()
    }
}

// ---------------------------------------------------------------- Custom modules

class CustomModuleStore(
    context: Context,
    private val fs: Firestore,
    private val uidProvider: () -> String?,
) {
    private val p = context.getSharedPreferences("medx.custom", Context.MODE_PRIVATE)
    val modules = MutableStateFlow<List<CustomModule>>(emptyList())
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    private fun key(uid: String) = "modules.$uid"

    fun loadLocal() {
        val uid = uidProvider() ?: return
        modules.value = p.getString(key(uid), null)?.let { t ->
            runCatching { MedxJson.parseToJsonElement(t).jsonArray.map { CustomModule.from(it.jsonObject) } }.getOrNull()
        } ?: emptyList()
    }

    private fun saveLocal() {
        val uid = uidProvider() ?: return
        p.edit().putString(key(uid), JsonArray(modules.value.map { it.toJson() }).toString()).apply()
    }

    suspend fun refresh() {
        val uid = uidProvider() ?: return
        loading.value = true
        try {
            modules.value = fs.fetchCustomModules(uid).sortedByDescending { it.updatedAt }
            saveLocal()
            error.value = null
        } catch (e: Exception) {
            error.value = "Couldn't refresh custom modules."
        } finally {
            loading.value = false
        }
    }

    suspend fun save(m: CustomModule) {
        val updated = m.copy(updatedAt = MedxDates.stamp())
        modules.value = (listOf(updated) + modules.value.filter { it.id != m.id }).sortedByDescending { it.updatedAt }
        saveLocal()
        fs.saveCustomModule(updated)
    }

    suspend fun delete(m: CustomModule) {
        modules.value = modules.value.filter { it.id != m.id }
        saveLocal()
        fs.deleteCustomModule(m.id)
    }

    fun reset() { modules.value = emptyList() }
}
