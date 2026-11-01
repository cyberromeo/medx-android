package quest.srihari.medxelite.ui.runner

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import quest.srihari.medxelite.Medx
import quest.srihari.medxelite.data.AttemptSection
import quest.srihari.medxelite.data.MedxDates
import quest.srihari.medxelite.data.MedxJson
import quest.srihari.medxelite.data.Question
import quest.srihari.medxelite.data.QuestionResponse
import quest.srihari.medxelite.data.RunnerPayload
import quest.srihari.medxelite.data.RunnerSection
import quest.srihari.medxelite.data.SittingAttempt
import quest.srihari.medxelite.data.SittingMode
import quest.srihari.medxelite.data.arr
import quest.srihari.medxelite.data.asIntLenient
import quest.srihari.medxelite.data.int
import quest.srihari.medxelite.data.jsonOf
import quest.srihari.medxelite.data.long
import quest.srihari.medxelite.data.objects
import quest.srihari.medxelite.data.str

sealed interface RunnerLoad {
    data object Loading : RunnerLoad
    data object Ready : RunnerLoad
    data class Unavailable(val message: String) : RunnerLoad
}

enum class ClockPhase { CALM, LOW, CRITICAL }

data class Handover(
    val closedLabel: String, val score: Int, val attempted: Int, val total: Int, val gradable: Boolean,
    val nextLabel: String, val nextCount: Int, val nextMinutes: Int, val remainingSections: Int,
)

/**
 * The sitting engine, ported from QuizRunnerView.swift: exam mode (one clock per block, answers at
 * the end, sectioned grand tests with no way back) and revision mode (60 s per question, instant
 * reveal). Timers are wall-clock deadlines so a locked phone settles correctly on return.
 */
class RunnerState(val payload: RunnerPayload, private val context: Context) {
    var load by mutableStateOf<RunnerLoad>(RunnerLoad.Loading)
    var questions by mutableStateOf<List<Question>>(emptyList())
    var currentIndex by mutableIntStateOf(0)
    var furthestIndex by mutableIntStateOf(0)
    val responses = mutableStateMapOf<Int, QuestionResponse>()
    val revealed = mutableStateMapOf<Int, Boolean>()
    val frozen = mutableStateMapOf<Int, Int>()
    var sections by mutableStateOf<List<RunnerSection>>(emptyList())
    var sectionIndex by mutableIntStateOf(0)
    var sectionLog by mutableStateOf<List<AttemptSection>>(emptyList())
    var handover by mutableStateOf<Handover?>(null)
    var finished by mutableStateOf(false)
    var completedSeconds by mutableIntStateOf(0)
    var resumeOffer by mutableStateOf<JsonObject?>(null)
    var now by mutableLongStateOf(System.currentTimeMillis())

    private var startedAt = System.currentTimeMillis()
    private var sectionStartedAt = System.currentTimeMillis()
    private var deadline = Long.MAX_VALUE
    private var ticksSinceSave = 0
    private var elapsedBefore = 0L

    val uid: String? get() = Medx.auth.uid
    val isExam get() = payload.mode == SittingMode.EXAM
    val isSectioned get() = sections.size > 1
    val isFinalSection get() = sectionIndex >= sections.size - 1
    val activeSection: RunnerSection
        get() = sections.getOrNull(sectionIndex) ?: RunnerSection("Section 1", 0, questions.size, maxOf(questions.size, 1))
    val currentQuestion: Question? get() = questions.getOrNull(currentIndex)
    val questionsInSection: List<Question>
        get() {
            val s = activeSection
            if (s.start >= questions.size) return emptyList()
            return questions.subList(s.start, minOf(s.end, questions.size))
        }
    val sectionAnswered get() = questionsInSection.count { responses[it.id]?.chosenId != null }
    val isLastQuestion get() = currentIndex >= activeSection.end - 1
    val canGoBack get() = currentIndex > activeSection.start
    val isCurrentRevealed get() = currentQuestion?.let { !isExam && revealed[it.id] == true } ?: false
    val canAdvanceNow get() = isExam || isCurrentRevealed
    val capacitySeconds get() = if (isExam) maxOf(activeSection.seconds, 1) else 60
    private val isClockRunning: Boolean
        get() = if (isExam) true else currentQuestion?.let { responses[it.id] == null } ?: false

    val remainingSeconds: Int
        get() {
            if (deadline == Long.MAX_VALUE) return capacitySeconds
            return maxOf(Math.ceil((deadline - now) / 1000.0).toInt(), 0)
        }

    /** What the clock shows: a revision question that has been answered stays frozen. */
    val displaySeconds: Int
        get() {
            val q = currentQuestion
            if (!isExam && q != null && responses[q.id] != null) return frozen[q.id] ?: 0
            return remainingSeconds
        }

    val phase: ClockPhase
        get() {
            val f = displaySeconds.toDouble() / maxOf(capacitySeconds, 1)
            return when {
                f <= 0.1 -> ClockPhase.CRITICAL
                f <= 0.25 -> ClockPhase.LOW
                else -> ClockPhase.CALM
            }
        }

    // ------------------------------------------------------------------ loading

    suspend fun loadQuestions() {
        if (load == RunnerLoad.Ready && questions.isNotEmpty()) return
        load = RunnerLoad.Loading
        try {
            val loaded: List<Question> = payload.questions?.takeIf { it.isNotEmpty() }
                ?: if (payload.kind == "qbank") Medx.fs.fetchModule(payload.id).questions
                else Medx.fs.fetchTestQuestions(payload.id)
            if (loaded.isEmpty()) {
                load = RunnerLoad.Unavailable("This paper has no questions yet. Please check back later.")
                return
            }
            questions = Question.uniqued(loaded)
            responses.clear(); revealed.clear(); frozen.clear()
            currentIndex = 0; furthestIndex = 0; sectionIndex = 0; sectionLog = emptyList()
            val requested = if (isExam) payload.sections.orEmpty() else emptyList()
            val resolved = RunnerSection.clamped(requested, questions.size)
            sections = if (resolved.size > 1) resolved else listOf(wholePaper(questions.size))
            startedAt = System.currentTimeMillis()
            sectionStartedAt = startedAt
            windClock(if (isExam) sections[0].seconds else 60)
            load = RunnerLoad.Ready
            val snap = loadSnapshot()
            if (snap != null) {
                if (snap.str("fingerprint") == fingerprint() && snap.str("mode") == payload.mode.raw) resumeOffer = snap
                else clearSnapshot()
            }
        } catch (e: Exception) {
            load = RunnerLoad.Unavailable("We couldn't load this sitting. Check your connection and try again.")
        }
    }

    private fun wholePaper(count: Int): RunnerSection {
        val total = maxOf(count, 1)
        val seconds = payload.examSeconds ?: (total * 60)
        return RunnerSection("Section 1", 0, total, maxOf(Math.round(seconds / 60.0).toInt(), 1))
    }

    private fun windClock(seconds: Int) {
        now = System.currentTimeMillis()
        deadline = now + maxOf(seconds, 0) * 1000L
    }

    // ------------------------------------------------------------------ ticking

    /** Once a second. Returns true when a timeout fired (for haptics). */
    fun tick(): Boolean {
        now = System.currentTimeMillis()
        if (load != RunnerLoad.Ready || finished || handover != null || resumeOffer != null) return false
        ticksSinceSave++
        if (ticksSinceSave >= 15) { ticksSinceSave = 0; persistSnapshot() }
        if (!isClockRunning) return false
        if (remainingSeconds <= 0) { handleTimeout(); return true }
        return false
    }

    private fun handleTimeout() {
        if (isExam) {
            submitSection()
        } else {
            val q = currentQuestion ?: return
            if (responses[q.id] == null) {
                frozen[q.id] = 0
                responses[q.id] = QuestionResponse(q.id, null, false, true)
                revealed[q.id] = true
            }
        }
    }

    // ------------------------------------------------------------------ answering & moving

    /** Returns null if ignored, true/false for correct in revision, true for exam selection. */
    fun pick(q: Question, optionId: Int): Boolean? {
        if (!isExam && responses[q.id] != null) return null
        if (isExam && responses[q.id]?.chosenId == optionId) {
            responses.remove(q.id)
            return null
        }
        val correct = q.isCorrect(optionId)
        if (!isExam) frozen[q.id] = remainingSeconds
        responses[q.id] = QuestionResponse(q.id, optionId, correct)
        if (!isExam) revealed[q.id] = true
        persistSnapshot()
        return correct
    }

    fun goBack() {
        if (!canGoBack) return
        currentIndex -= 1
        resetRevisionClock()
    }

    fun next() {
        if (currentIndex + 1 >= activeSection.end) return
        currentIndex += 1
        furthestIndex = maxOf(furthestIndex, currentIndex)
        resetRevisionClock()
    }

    fun jump(to: Int): Boolean {
        if (to !in questions.indices || to < activeSection.start || to >= activeSection.end || to == currentIndex) return false
        if (!isExam && to > furthestIndex) return false
        currentIndex = to
        furthestIndex = maxOf(furthestIndex, to)
        resetRevisionClock()
        return true
    }

    private fun resetRevisionClock() {
        if (isExam) return
        val q = currentQuestion ?: return
        if (responses[q.id] != null) return
        windClock(60)
    }

    fun firstUnansweredInSection(): Int? {
        val s = activeSection
        return (s.start until minOf(s.end, questions.size)).firstOrNull { responses[questions[it].id]?.chosenId == null }
    }

    // ------------------------------------------------------------------ sections & finishing

    fun submitSection() {
        if (!isSectioned || finished) { finish(); return }
        logSection()
        if (isFinalSection) { finish(); return }
        val closed = activeSection
        val row = sectionLog.lastOrNull()
        sectionIndex += 1
        currentIndex = activeSection.start
        furthestIndex = maxOf(furthestIndex, currentIndex)
        deadline = Long.MAX_VALUE
        handover = Handover(
            closed.label, row?.score ?: 0, row?.attempted ?: 0, closed.count, payload.gradable,
            activeSection.label, activeSection.count, activeSection.minutes, sections.size - sectionIndex,
        )
        persistSnapshot()
    }

    fun beginActiveSection() {
        handover = null
        sectionStartedAt = System.currentTimeMillis()
        windClock(activeSection.seconds)
    }

    private fun logSection() {
        if (!isSectioned) return
        if (sectionLog.any { it.index == sectionIndex }) return
        val slice = questionsInSection
        val rows = slice.mapNotNull { responses[it.id] }
        sectionLog = sectionLog + AttemptSection(
            sectionIndex, activeSection.label, slice.size, rows.count { it.correct },
            rows.count { it.chosenId != null }, ((System.currentTimeMillis() - sectionStartedAt) / 1000).toInt(),
        )
    }

    fun finish() {
        if (finished) return
        clearSnapshot()
        logSection()
        completedSeconds = (elapsedBefore + (System.currentTimeMillis() - startedAt) / 1000).toInt()
        finished = true
        save()
    }

    private fun save() {
        val session = Medx.auth.current ?: return
        val values = responses.values.toList()
        val attempt = SittingAttempt(
            id = SittingAttempt.newId(),
            uid = session.uid,
            profile = session.name,
            kind = payload.kind,
            sourceId = payload.id,
            name = payload.name,
            subject = payload.subject,
            mode = payload.mode.raw,
            gradable = payload.gradable,
            total = questions.size,
            score = values.count { it.correct },
            attempted = values.count { it.chosenId != null },
            durationSeconds = completedSeconds,
            finishedAt = MedxDates.stamp(),
            responses = values,
            sections = sectionLog.sortedBy { it.index }.takeIf { it.isNotEmpty() },
        )
        Medx.attempts.enqueue(attempt)
    }

    // ------------------------------------------------------------------ snapshots (save & leave)

    private val prefs get() = context.getSharedPreferences("medx.sittings", Context.MODE_PRIVATE)
    private fun snapKey() = "snap.${uid}.${payload.kind}.${payload.id}"
    private val persists get() = payload.kind != "duel" && uid != null

    fun fingerprint(): String = questions.joinToString(",") { it.id.toString() }.hashCode().toString() + ":" + questions.size

    fun persistSnapshot() {
        if (!persists || load != RunnerLoad.Ready || finished) return
        if (responses.isEmpty()) return
        val elapsed = elapsedBefore + (System.currentTimeMillis() - startedAt) / 1000
        val snap = jsonOf(
            "fingerprint" to fingerprint(),
            "mode" to payload.mode.raw,
            "currentIndex" to currentIndex,
            "furthestIndex" to furthestIndex,
            "responses" to JsonArray(responses.values.map { it.toJson() }),
            "revealed" to revealed.keys.toList(),
            "frozen" to frozen.mapKeys { it.key.toString() },
            "remainingSeconds" to if (handover != null) activeSection.seconds else remainingSeconds,
            "sectionIndex" to sectionIndex,
            "sectionLog" to JsonArray(sectionLog.map { it.toJson() }),
            "elapsedSeconds" to elapsed,
            "savedAt" to System.currentTimeMillis(),
        )
        prefs.edit().putString(snapKey(), snap.toString()).apply()
    }

    private fun loadSnapshot(): JsonObject? {
        if (!persists) return null
        val text = prefs.getString(snapKey(), null) ?: return null
        val snap = runCatching { MedxJson.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        // Stale after a week.
        if (System.currentTimeMillis() - (snap.long("savedAt") ?: 0) > 7L * 24 * 3600 * 1000) { clearSnapshot(); return null }
        return snap
    }

    fun clearSnapshot() {
        prefs.edit().remove(snapKey()).apply()
    }

    fun applySnapshot(s: JsonObject) {
        responses.clear()
        s.objects("responses").forEach { val r = QuestionResponse.from(it); responses[r.questionId] = r }
        revealed.clear()
        s.arr("revealed")?.forEach { e -> e.asIntLenient()?.let { revealed[it] = true } }
        frozen.clear()
        (s["frozen"] as? JsonObject)?.forEach { (k, v) -> k.toIntOrNull()?.let { id -> v.asIntLenient()?.let { frozen[id] = it } } }
        sectionIndex = (s.int("sectionIndex") ?: 0).coerceIn(0, maxOf(sections.size - 1, 0))
        sectionLog = s.objects("sectionLog").map { AttemptSection.from(it) }
        currentIndex = (s.int("currentIndex") ?: 0).coerceIn(activeSection.start, maxOf(activeSection.end - 1, activeSection.start))
        furthestIndex = maxOf(s.int("furthestIndex") ?: currentIndex, currentIndex)
        elapsedBefore = s.long("elapsedSeconds") ?: 0
        startedAt = System.currentTimeMillis()
        sectionStartedAt = startedAt
        val remaining = s.int("remainingSeconds") ?: capacitySeconds
        windClock(if (isExam) remaining else 60)
        if (!isExam) resetRevisionClock()
        resumeOffer = null
    }

    fun resumeSummary(s: JsonObject): String {
        val answered = s.objects("responses").size
        val at = (s.int("currentIndex") ?: 0) + 1
        return "You answered $answered of ${questions.size} and were on question $at."
    }
}
