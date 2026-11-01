package quest.srihari.medxelite.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

// Ports of the iOS models (Models/*.swift). Each has a lenient `from(JsonObject)` that mirrors the
// Swift `init(from:)`, and a `toJson()` that writes the same shape back to Firestore / the cache.

object MedxDates {
    fun parse(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return try {
            Instant.parse(value)
        } catch (_: Exception) {
            try {
                java.time.OffsetDateTime.parse(value).toInstant()
            } catch (_: Exception) {
                null
            }
        }
    }

    /** RFC 3339, whole seconds, UTC — the spelling the iOS app writes. */
    fun stamp(instant: Instant = Instant.now()): String =
        DateTimeFormatter.ISO_INSTANT.format(instant.truncatedTo(ChronoUnit.SECONDS))
}

// ---------------------------------------------------------------- QBank

enum class MedxBank(val label: String, val eyebrow: String) {
    ARISE("Arise", "ARISE · Online Dec 26"),
    MARROW("Marrow", "Marrow · FMGE");

    val raw: String get() = name.lowercase()

    companion object {
        const val MARROW_PREFIX = "mw_"
        const val ARISE_PREFIX = "qb_"
        fun of(id: String) = if (id.startsWith(MARROW_PREFIX)) MARROW else ARISE
        fun isModuleId(id: String) = id.startsWith(ARISE_PREFIX) || id.startsWith(MARROW_PREFIX)
        fun fromRaw(raw: String?) = entries.firstOrNull { it.raw == raw }
    }
}

data class QuestionOption(val id: Int, val label: String, val text: String, val correct: Boolean?) {
    fun toJson() = jsonOf("id" to id, "label" to label, "text" to text, "correct" to correct)

    companion object {
        fun from(o: JsonObject) = QuestionOption(
            id = o.el("id")?.asIntLenient() ?: 0,
            label = o.str("label") ?: "",
            text = o.str("text") ?: "",
            correct = o.bool("correct"),
        )
    }
}

data class Question(
    val id: Int,
    val lqId: Int? = null,
    val number: Int? = null,
    val html: String? = null,
    val plain: String? = null,
    val type: String? = null,
    val answerType: String? = null,
    val options: List<QuestionOption> = emptyList(),
    val correctIds: List<Int> = emptyList(),
    val explanation: String? = null,
    val reference: String? = null,
    val images: List<String>? = null,
) {
    val displayText: String get() = html ?: plain ?: ""

    fun isCorrect(optionId: Int) = correctIds.contains(optionId) || options.any { it.id == optionId && it.correct == true }

    val hasAnswerKey: Boolean get() = correctIds.isNotEmpty() || options.any { it.correct == true }

    val correctOptionIds: List<Int>
        get() = if (correctIds.isNotEmpty()) correctIds else options.filter { it.correct == true }.map { it.id }

    fun toJson() = jsonOf(
        "id" to id, "lqId" to lqId, "number" to number, "html" to html, "plain" to plain,
        "type" to type, "answerType" to answerType,
        "options" to JsonArray(options.map { it.toJson() }),
        "correctIds" to correctIds,
        "explanation" to explanation, "reference" to reference, "images" to images,
    )

    companion object {
        fun from(o: JsonObject): Question {
            val id = o.el("id")?.asIntLenient()?.takeIf { it != 0 }
                ?: o.el("questionId")?.asIntLenient()
                ?: o.el("qid")?.asIntLenient()
                ?: o.str("_id")?.let { OptionIds.int(it) }
                ?: 0
            return Question(
                id = id,
                lqId = o.int("lqId"),
                number = o.int("number"),
                html = o.str("html"),
                plain = o.str("plain"),
                type = o.str("type"),
                answerType = o.str("answerType"),
                options = o.objects("options").map { QuestionOption.from(it) },
                correctIds = o.arr("correctIds")?.mapNotNull { it.asIntLenient() } ?: emptyList(),
                explanation = o.str("explanation"),
                reference = o.str("reference"),
                images = o.arr("images")?.mapNotNull { it.asStringOrNull() },
            )
        }

        /** One id per question (MedxQuestionIdentity.uniqued). */
        fun uniqued(questions: List<Question>): List<Question> {
            val ids = questions.map { it.id }
            if (!ids.contains(0) && ids.toSet().size == ids.size) return questions
            val counts = ids.groupingBy { it }.eachCount()
            val taken = ids.filter { it != 0 && counts[it] == 1 }.toMutableSet()
            return questions.mapIndexed { index, q ->
                if (q.id != 0 && counts[q.id] == 1) return@mapIndexed q
                var candidate: Int
                val lq = q.lqId
                if (lq != null && lq != 0 && !taken.contains(lq)) {
                    candidate = lq
                } else {
                    candidate = 900_000_000 + index + 1
                    while (taken.contains(candidate)) candidate++
                }
                taken.add(candidate)
                q.copy(id = candidate)
            }
        }
    }
}

data class ModuleSummary(val id: String, val name: String, val questionCount: Int, val chapter: String?) {
    fun toJson() = jsonOf("id" to id, "name" to name, "questionCount" to questionCount, "chapter" to chapter)

    companion object {
        fun from(o: JsonObject) = ModuleSummary(
            id = o.str("id") ?: UUID.randomUUID().toString(),
            name = o.str("name") ?: "",
            questionCount = o.int("questionCount") ?: 0,
            chapter = o.str("chapter"),
        )
    }
}

data class BankChapter(val id: String, val name: String, val modules: List<ModuleSummary>) {
    fun toJson() = jsonOf("id" to id, "name" to name, "modules" to JsonArray(modules.map { it.toJson() }))

    companion object {
        fun from(o: JsonObject) = BankChapter(
            id = o.str("id") ?: UUID.randomUUID().toString(),
            name = o.str("name") ?: "",
            modules = o.objects("modules").map { ModuleSummary.from(it) },
        )
    }
}

/** Both banks (ARISE `medx_qbank_subjects` + Marrow `medx_meta/qbank_fmge`) as one tree. */
data class BankSubject(
    val id: String,
    val bank: MedxBank,
    val name: String,
    val slug: String?,
    val moduleCount: Int,
    val questionCount: Int,
    val chapters: List<BankChapter>,
) {
    val modules: List<ModuleSummary> get() = chapters.flatMap { it.modules }

    fun toJson() = jsonOf(
        "id" to id, "bank" to bank.raw, "name" to name, "slug" to slug,
        "moduleCount" to moduleCount, "questionCount" to questionCount,
        "chapters" to JsonArray(chapters.map { it.toJson() }),
    )

    companion object {
        /** A Marrow subject from the index doc, or a cached BankSubject. */
        fun from(o: JsonObject): BankSubject {
            val id = o.str("id") ?: UUID.randomUUID().toString()
            return BankSubject(
                id = id,
                bank = MedxBank.of(id),
                name = o.str("name") ?: "",
                slug = o.str("slug"),
                moduleCount = o.int("moduleCount") ?: 0,
                questionCount = o.int("questionCount") ?: 0,
                chapters = o.objects("chapters").map { BankChapter.from(it) },
            )
        }

        /** An ARISE subject document from `medx_qbank_subjects`. */
        fun fromArise(o: JsonObject): BankSubject {
            val sid = o.int("subjectId") ?: 0
            val modulesAll = o.objects("chapters").map { BankChapter.from(it) }
            return BankSubject(
                id = sid.toString(),
                bank = MedxBank.ARISE,
                name = o.str("name") ?: "",
                slug = o.str("slug"),
                moduleCount = o.int("moduleCount") ?: 0,
                questionCount = o.int("questionCount") ?: 0,
                chapters = modulesAll,
            )
        }
    }
}

data class ModuleDetail(
    val moduleId: String,
    val subject: String?,
    val chapter: String?,
    val name: String,
    val description: String?,
    val questions: List<Question>,
    val partCount: Int?,
) {
    fun toJson() = jsonOf(
        "moduleId" to moduleId, "subject" to subject, "chapter" to chapter, "name" to name,
        "description" to description, "partCount" to partCount,
        "questions" to JsonArray(questions.map { it.toJson() }),
    )

    companion object {
        fun from(o: JsonObject) = ModuleDetail(
            moduleId = o.str("moduleId") ?: o.str("id") ?: "",
            subject = o.str("subject"),
            chapter = o.str("chapter"),
            name = o.str("name") ?: "Module",
            description = o.str("description"),
            questions = o.objects("questions").map { Question.from(it) },
            partCount = o.int("partCount"),
        )
    }
}

// ---------------------------------------------------------------- Attempts

enum class SittingMode(val raw: String, val displayName: String, val description: String) {
    EXAM("exam", "Exam Mode", "1 minute per question total timer. Grade and explanations revealed at the end."),
    REVISION("revision", "Revision Mode", "60s per question timer. Instant answer reveal & explanations as you answer.");
}

data class RunnerSection(val label: String, val start: Int, val count: Int, val minutes: Int) {
    val end get() = start + count
    val seconds get() = maxOf(minutes, 1) * 60

    companion object {
        fun clamped(sections: List<RunnerSection>, total: Int): List<RunnerSection> {
            var cursor = 0
            val out = mutableListOf<RunnerSection>()
            for (s in sections) {
                if (cursor >= total) break
                val length = minOf(s.count, total - cursor)
                out.add(RunnerSection(s.label, cursor, length, s.minutes))
                cursor += length
            }
            if (cursor < total && out.isNotEmpty()) {
                val extra = total - cursor
                val last = out.last()
                out[out.size - 1] = last.copy(count = last.count + extra, minutes = last.minutes + extra)
            }
            return out
        }
    }
}

data class RunnerPayload(
    val kind: String, // qbank, test, series, custom, duel
    val id: String,
    val name: String,
    val subject: String,
    val mode: SittingMode,
    val gradable: Boolean = true,
    val questions: List<Question>? = null,
    val sections: List<RunnerSection>? = null,
    val examSeconds: Int? = null,
    val questionTags: List<String>? = null,
)

data class QuestionResponse(val questionId: Int, val chosenId: Int?, val correct: Boolean, val timedOut: Boolean? = null) {
    fun toJson() = jsonOf("questionId" to questionId, "chosenId" to chosenId, "correct" to correct, "timedOut" to timedOut)

    companion object {
        fun from(o: JsonObject) = QuestionResponse(
            questionId = o.el("questionId")?.asIntLenient() ?: 0,
            chosenId = o.int("chosenId"),
            correct = o.bool("correct") ?: false,
            timedOut = o.bool("timedOut"),
        )
    }
}

data class AttemptSection(val index: Int, val label: String, val total: Int, val score: Int, val attempted: Int, val seconds: Int) {
    fun toJson() = jsonOf("index" to index, "label" to label, "total" to total, "score" to score, "attempted" to attempted, "seconds" to seconds)

    companion object {
        fun from(o: JsonObject) = AttemptSection(
            o.int("index") ?: 0, o.str("label") ?: "Section", o.int("total") ?: 0,
            o.int("score") ?: 0, o.int("attempted") ?: 0, o.int("seconds") ?: 0,
        )
    }
}

data class SittingAttempt(
    val id: String?,
    val uid: String,
    val profile: String?,
    val kind: String,
    val sourceId: String,
    val name: String,
    val subject: String?,
    val mode: String,
    val gradable: Boolean?,
    val total: Int,
    val score: Int,
    val attempted: Int,
    val durationSeconds: Int?,
    val finishedAt: String?,
    val responses: List<QuestionResponse>,
    val sections: List<AttemptSection>? = null,
) {
    val accuracyPercentage: Int get() = if (attempted > 0) Math.round(score * 100.0 / attempted).toInt() else 0
    val totalPercentage: Int get() = if (total > 0) Math.round(score * 100.0 / total).toInt() else 0
    val finishedInstant: Instant? get() = MedxDates.parse(finishedAt)

    fun toJson() = jsonOf(
        "id" to id, "uid" to uid, "profile" to profile, "kind" to kind, "sourceId" to sourceId,
        "name" to name, "subject" to subject, "mode" to mode, "gradable" to gradable,
        "total" to total, "score" to score, "attempted" to attempted,
        "durationSeconds" to durationSeconds, "finishedAt" to finishedAt,
        "responses" to JsonArray(responses.map { it.toJson() }),
        "sections" to sections?.let { s -> JsonArray(s.map { it.toJson() }) },
    )

    companion object {
        fun newId() = "att_" + UUID.randomUUID().toString().lowercase()

        fun from(o: JsonObject) = SittingAttempt(
            id = o.str("id"),
            uid = o.str("uid") ?: "",
            profile = o.str("profile"),
            kind = o.str("kind") ?: "qbank",
            sourceId = o.str("sourceId") ?: "",
            name = o.str("name") ?: "Sitting",
            subject = o.str("subject"),
            mode = o.str("mode") ?: "exam",
            gradable = o.bool("gradable"),
            total = o.int("total") ?: 0,
            score = o.int("score") ?: 0,
            attempted = o.int("attempted") ?: 0,
            durationSeconds = o.int("durationSeconds"),
            finishedAt = o.str("finishedAt"),
            responses = o.objects("responses").map { QuestionResponse.from(it) },
            sections = o.objects("sections").map { AttemptSection.from(it) }.takeIf { it.isNotEmpty() },
        )
    }
}

enum class AttemptKind(val raw: String, val label: String) {
    QBANK("qbank", "QBank"), TEST("test", "Batch paper"), SERIES("series", "Marrow test"),
    CUSTOM("custom", "Custom module"), DUEL("duel", "Faceoff");

    companion object {
        fun label(raw: String) = entries.firstOrNull { it.raw == raw }?.label ?: "Sitting"
    }
}

// ---------------------------------------------------------------- Tests / series

data class BatchTest(
    val testId: String,
    val batch: String?,
    val name: String,
    val subject: String,
    val testType: String?,
    val questionCount: Int,
    val officialTimeMins: Int,
    val gradable: Boolean,
) {
    fun toJson() = jsonOf(
        "testId" to testId, "batch" to batch, "name" to name, "subject" to subject, "testType" to testType,
        "questionCount" to questionCount, "officialTimeMins" to officialTimeMins, "gradable" to gradable,
    )

    companion object {
        fun from(o: JsonObject): BatchTest {
            val qc = o.int("questionCount") ?: 0
            return BatchTest(
                testId = o.str("testId") ?: o.str("id") ?: UUID.randomUUID().toString(),
                batch = o.str("batch"),
                name = o.str("name") ?: "Test",
                subject = o.str("subject") ?: "",
                testType = o.str("testType"),
                questionCount = qc,
                officialTimeMins = o.int("officialTimeMins") ?: qc,
                gradable = o.bool("gradable") ?: false,
            )
        }
    }
}

enum class SeriesGroup(val raw: String, val label: String) {
    GRAND("grand", "GTs"), MINI("mini", "Mini tests"), SUBJECT("subject", "Subject tests");

    companion object {
        fun of(raw: String?) = entries.firstOrNull { it.raw == raw } ?: SUBJECT
    }
}

data class SeriesPaper(
    val id: String,
    val title: String,
    val group: SeriesGroup,
    val year: String?,
    val questions: Int,
    val durationMin: Int,
    val startAt: Double,
    val isPaid: Boolean,
    val hasQuestions: Boolean,
) {
    val startInstant: Instant? get() = if (startAt > 0) Instant.ofEpochMilli(startAt.toLong()) else null
    val line: String get() = "$questions q · ${if (durationMin > 0) durationMin else questions} min"

    fun toJson() = jsonOf(
        "id" to id, "title" to title, "type" to group.raw, "year" to year, "questions" to questions,
        "durationMin" to durationMin, "startAt" to startAt, "isPaid" to isPaid, "hasQuestions" to hasQuestions,
    )

    companion object {
        fun from(o: JsonObject) = SeriesPaper(
            id = o.str("id") ?: UUID.randomUUID().toString(),
            title = o.str("title") ?: "Paper",
            group = SeriesGroup.of(o.str("type")),
            year = o.str("year"),
            questions = o.int("questions") ?: 0,
            durationMin = o.int("durationMin") ?: 0,
            startAt = o.double("startAt") ?: 0.0,
            isPaid = o.bool("isPaid") ?: false,
            hasQuestions = o.bool("hasQuestions") ?: true,
        )
    }
}

object SeriesRules {
    const val SECTION_SIZE = 50

    fun sections(paper: SeriesPaper): List<RunnerSection>? {
        val total = paper.questions
        if (paper.group != SeriesGroup.GRAND || total <= SECTION_SIZE) return null
        val count = Math.ceil(total.toDouble() / SECTION_SIZE).toInt()
        return (0 until count).map { i ->
            val start = i * SECTION_SIZE
            val length = minOf(SECTION_SIZE, total - start)
            RunnerSection("Section ${i + 1}", start, length, length)
        }
    }

    fun examBlurb(paper: SeriesPaper): String {
        val sections = sections(paper)
        val first = sections?.firstOrNull()
        if (sections == null || first == null) {
            val minutes = if (paper.durationMin > 0) paper.durationMin else paper.questions
            return "One $minutes-minute timer for the paper. Answers after you submit."
        }
        return "${sections.size} sections of ${first.count}, ${first.minutes} minutes each. No going back once a section is submitted."
    }
}

// ---------------------------------------------------------------- Flashcards

data class FlashcardCard(
    val id: Int,
    val name: String,
    val description: String?,
    val chapter: String?,
    val mobilePortrait: String?,
    val mobileLandscape: String?,
    val tabletPortrait: String?,
    val tabletLandscape: String?,
) {
    fun imageUrl(landscape: Boolean, tablet: Boolean): String? {
        val any = mobilePortrait ?: tabletPortrait ?: mobileLandscape ?: tabletLandscape
        val pick = when {
            tablet && landscape -> tabletLandscape
            tablet -> tabletPortrait
            landscape -> mobileLandscape
            else -> mobilePortrait
        } ?: any
        return pick?.let { resolveCdn(it, quest.srihari.medxelite.Config.FLASHCARD_CDN_BASE) }
    }

    fun toJson() = jsonOf(
        "id" to id, "name" to name, "description" to description, "chapter" to chapter,
        "variants" to jsonOf(
            "mobilePortrait" to mobilePortrait, "mobileLandscape" to mobileLandscape,
            "tabletPortrait" to tabletPortrait, "tabletLandscape" to tabletLandscape,
        ),
    )

    companion object {
        fun from(o: JsonObject): FlashcardCard {
            val v = o.obj("variants")
            return FlashcardCard(
                id = o.el("id")?.asIntLenient() ?: 0,
                name = o.str("name") ?: "",
                description = o.str("description"),
                chapter = o.str("chapter"),
                mobilePortrait = v?.str("mobilePortrait"),
                mobileLandscape = v?.str("mobileLandscape"),
                tabletPortrait = v?.str("tabletPortrait"),
                tabletLandscape = v?.str("tabletLandscape"),
            )
        }
    }
}

data class FlashcardSubject(val subjectId: Int, val name: String, val cardCount: Int, val cards: List<FlashcardCard>) {
    fun toJson() = jsonOf("subjectId" to subjectId, "name" to name, "cardCount" to cardCount, "cards" to JsonArray(cards.map { it.toJson() }))

    companion object {
        fun from(o: JsonObject): FlashcardSubject {
            val cards = o.objects("cards").map { FlashcardCard.from(it) }
            return FlashcardSubject(
                subjectId = o.int("subjectId") ?: o.int("id") ?: 0,
                name = o.str("name") ?: "",
                cardCount = o.int("cardCount") ?: cards.size,
                cards = cards,
            )
        }
    }
}

/** Absolute, protocol-relative or bare CDN-relative paths. Data URIs are skipped. */
fun resolveCdn(raw: String, base: String = quest.srihari.medxelite.Config.IMAGE_CDN_BASE): String? {
    var value = raw.trim()
    if (value.contains(",") && value.contains(" ")) value = value.substringBefore(",")
    value = value.split(Regex("\\s+")).firstOrNull() ?: value
    if (value.isEmpty() || value.lowercase().startsWith("data:")) return null
    return when {
        value.startsWith("//") -> "https:$value"
        value.lowercase().startsWith("http") -> value
        else -> base + "/" + value.removePrefix("/")
    }
}

// ---------------------------------------------------------------- Videos

data class RecordedVideo(
    val id: String,
    val source: String? = null,
    val batchId: String? = null,
    val batch: String? = null,
    val subjectId: String? = null,
    val subject: String,
    val title: String,
    val faculty: String? = null,
    val durationSeconds: Int? = null,
    val duration: String? = null,
    val streamUrl: String,
    val kind: String? = null,
) {
    val displayTitle: String get() = title.replace(" \u2014 ", " - ").replace("\u2014", "-")

    val formattedDuration: String
        get() {
            val secs = durationSeconds ?: 0
            if (secs <= 0) return duration ?: ""
            val h = secs / 3600
            val m = (secs % 3600) / 60
            return if (h > 0) "${h}h ${m}m" else "${m}m"
        }

    fun toJson() = jsonOf(
        "id" to id, "source" to source, "batchId" to batchId, "batch" to batch, "subjectId" to subjectId,
        "subject" to subject, "title" to title, "faculty" to faculty, "durationSeconds" to durationSeconds,
        "duration" to duration, "streamUrl" to streamUrl, "kind" to kind,
    )

    companion object {
        fun from(o: JsonObject) = RecordedVideo(
            id = o.str("id") ?: UUID.randomUUID().toString(),
            source = o.str("source"),
            batchId = o.str("batchId"),
            batch = o.str("batch"),
            subjectId = o.str("subjectId"),
            subject = o.str("subject") ?: "Subject",
            title = o.str("title") ?: "Class Video",
            faculty = o.str("faculty"),
            durationSeconds = o.int("durationSeconds"),
            duration = o.str("duration"),
            streamUrl = o.str("streamUrl") ?: "",
            kind = o.str("kind"),
        )
    }
}

data class VodItem(
    val id: String,
    val rawKey: String,
    val folder: String,
    val streamUrl: String,
    val thumbnailUrl: String,
    val subject: String,
    val faculty: String,
    val batch: String,
    val durationSeconds: Int,
    val uploadedAtRaw: String?,
) {
    val uploadedAt: Instant? get() = MedxDates.parse(uploadedAtRaw)

    val title: String
        get() {
            val opaque = rawKey.length >= 19 && rawKey.lowercase().startsWith("rec") &&
                rawKey.drop(3).all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
            if (!opaque) return humanise(rawKey)
            return when {
                folder.lowercase().startsWith("standalone-") -> "Standalone recording"
                folder.isEmpty() -> "Recording"
                else -> "Class ${folder.uppercase()}"
            }
        }

    val asRecordedVideo: RecordedVideo
        get() = RecordedVideo(
            id = id, source = "VOD", batch = batch.ifEmpty { null },
            subject = subject.ifEmpty { "VOD bucket" }, title = title,
            faculty = faculty.ifEmpty { null }, durationSeconds = durationSeconds.takeIf { it > 0 },
            streamUrl = streamUrl, kind = "hls",
        )

    companion object {
        fun humanise(key: String): String {
            var text = key
            for (suffix in listOf(".m3u8", ".mp4", ".ts")) if (text.lowercase().endsWith(suffix)) text = text.dropLast(suffix.length)
            text = text.replace("_", " ").replace("-", " ").replace("/", " ")
                .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
                .replace(Regex("([A-Za-z])([0-9])"), "$1 $2")
                .replace(Regex("\\s+"), " ").trim()
            return text.ifEmpty { key }
        }

        fun from(f: JsonObject, fallbackId: String): VodItem? {
            val id = f.str("id")?.ifEmpty { null } ?: fallbackId
            if (id.isEmpty()) return null
            val key = (f.str("title")?.ifEmpty { null } ?: f.str("fileKey") ?: "").ifEmpty { id }
            val precise = f.int("durationPreciseSeconds") ?: 0
            return VodItem(
                id = id, rawKey = key, folder = f.str("folder") ?: "", streamUrl = f.str("streamUrl") ?: "",
                thumbnailUrl = f.str("thumbnailUrl") ?: "", subject = f.str("subject") ?: "",
                faculty = f.str("faculty") ?: "", batch = f.str("batch") ?: "",
                durationSeconds = if (precise > 0) precise else (f.int("durationSeconds") ?: 0),
                uploadedAtRaw = f.str("uploadedAt"),
            )
        }
    }
}

// ---------------------------------------------------------------- Activity

data class BookmarkedQuestion(
    val sourceId: String,
    val ownerId: String,
    val sourceName: String,
    val subject: String,
    val question: Question,
    val bookmarkedAt: String,
) {
    val key: String get() = "$sourceId-${question.id}"
    val docId: String get() = "${ownerId}_${sourceId}_${question.id}".replace("/", "_").replace(" ", "_")

    val previewText: String
        get() = (question.plain ?: question.html ?: "Question ${question.number ?: question.id}")
            .replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ").replace(Regex("\\s+"), " ").trim()

    fun toJson() = jsonOf(
        "sourceId" to sourceId, "ownerId" to ownerId, "sourceName" to sourceName,
        "subject" to subject, "question" to question.toJson(), "bookmarkedAt" to bookmarkedAt,
    )

    companion object {
        fun from(o: JsonObject): BookmarkedQuestion? {
            val q = o.obj("question") ?: return null
            return BookmarkedQuestion(
                sourceId = o.str("sourceId") ?: "",
                ownerId = o.str("ownerId") ?: "",
                sourceName = o.str("sourceName") ?: "",
                subject = o.str("subject") ?: "",
                question = Question.from(q),
                bookmarkedAt = o.str("bookmarkedAt") ?: MedxDates.stamp(),
            )
        }
    }
}

data class WatchHistoryEntry(
    val video: RecordedVideo,
    val ownerId: String,
    val positionSeconds: Double,
    val durationSeconds: Double,
    val watchedAt: String,
) {
    val docId: String get() = "${ownerId}_${video.id}".replace("/", "_")
    val progress: Double get() = if (durationSeconds > 0) (positionSeconds / durationSeconds).coerceIn(0.0, 1.0) else 0.0
    val isCompleted: Boolean get() = progress >= 0.95
    val resumePosition: Double
        get() = if (durationSeconds > 0 && positionSeconds >= durationSeconds - 15) 0.0
        else if (positionSeconds > 5) positionSeconds else 0.0
    val formattedResumeTime: String
        get() {
            val t = maxOf(positionSeconds.toInt(), 0)
            return "%d:%02d".format(t / 60, t % 60)
        }

    fun toJson() = jsonOf(
        "video" to video.toJson(), "ownerId" to ownerId, "positionSeconds" to positionSeconds,
        "durationSeconds" to durationSeconds, "watchedAt" to watchedAt,
    )

    companion object {
        fun from(o: JsonObject): WatchHistoryEntry? {
            val v = o.obj("video") ?: return null
            val video = RecordedVideo.from(v)
            return WatchHistoryEntry(
                video = video,
                ownerId = o.str("ownerId") ?: "",
                positionSeconds = o.double("positionSeconds") ?: 0.0,
                durationSeconds = o.double("durationSeconds") ?: (video.durationSeconds ?: 0).toDouble(),
                watchedAt = o.str("watchedAt") ?: MedxDates.stamp(),
            )
        }
    }
}

// ---------------------------------------------------------------- Custom modules

data class ModuleSource(
    val moduleId: String,
    val name: String,
    val chapter: String,
    val subject: String,
    val bank: MedxBank,
    val questionCount: Int,
) {
    fun toJson() = jsonOf(
        "type" to "module", "moduleId" to moduleId, "name" to name, "chapter" to chapter,
        "subject" to subject, "bank" to bank.raw, "questionCount" to questionCount,
    )

    companion object {
        fun from(o: JsonObject): ModuleSource {
            val mid = o.str("moduleId") ?: ""
            return ModuleSource(
                moduleId = mid, name = o.str("name") ?: "Module", chapter = o.str("chapter") ?: "",
                subject = o.str("subject") ?: "", bank = MedxBank.fromRaw(o.str("bank")) ?: MedxBank.of(mid),
                questionCount = o.int("questionCount") ?: 0,
            )
        }
    }
}

data class CustomModule(
    val id: String,
    val uid: String,
    val name: String,
    val note: String,
    val sources: List<ModuleSource>,
    val shuffle: Boolean,
    val limit: Int?,
    val createdAt: String,
    val updatedAt: String,
) {
    val questionCount: Int get() = sources.sumOf { it.questionCount }
    val effectiveCount: Int get() = limit?.takeIf { it > 0 }?.let { minOf(questionCount, it) } ?: questionCount

    fun toJson() = jsonOf(
        "id" to id, "uid" to uid, "name" to name, "note" to note,
        "sources" to JsonArray(sources.map { it.toJson() }), "shuffle" to shuffle,
        "limit" to (limit ?: 0), "createdAt" to createdAt, "updatedAt" to updatedAt,
    )

    companion object {
        fun newId(): String {
            val stamp = System.currentTimeMillis().toString(36)
            val salt = UUID.randomUUID().toString().replace("-", "").take(4).lowercase()
            return "c$stamp$salt"
        }

        fun from(o: JsonObject): CustomModule {
            val now = MedxDates.stamp()
            val created = o.str("createdAt") ?: now
            return CustomModule(
                id = o.str("id") ?: newId(),
                uid = o.str("uid") ?: "",
                name = o.str("name") ?: "",
                note = o.str("note") ?: "",
                sources = o.objects("sources").map { ModuleSource.from(it) },
                shuffle = o.bool("shuffle") ?: true,
                limit = o.int("limit")?.takeIf { it > 0 },
                createdAt = created,
                updatedAt = o.str("updatedAt") ?: created,
            )
        }
    }
}
