package quest.srihari.medxelite.screenshots

import kotlinx.coroutines.flow.MutableStateFlow
import quest.srihari.medxelite.Medx
import quest.srihari.medxelite.auth.AuthSession
import quest.srihari.medxelite.data.BankChapter
import quest.srihari.medxelite.data.BankSubject
import quest.srihari.medxelite.data.Catalog
import quest.srihari.medxelite.data.FlashcardSubject
import quest.srihari.medxelite.data.Load
import quest.srihari.medxelite.data.MedxAccent
import quest.srihari.medxelite.data.MedxAppearance
import quest.srihari.medxelite.data.MedxBank
import quest.srihari.medxelite.data.MedxDates
import quest.srihari.medxelite.data.ModuleSummary
import quest.srihari.medxelite.data.Question
import quest.srihari.medxelite.data.QuestionOption
import quest.srihari.medxelite.data.QuestionResponse
import quest.srihari.medxelite.data.RecordedVideo
import quest.srihari.medxelite.data.SittingAttempt
import quest.srihari.medxelite.data.StatsMath
import quest.srihari.medxelite.data.WatchHistoryEntry
import java.time.LocalDate
import java.time.ZoneId

/**
 * Sample data for the screenshot tests. Everything here is made up and lives only in test
 * sources: it is pushed straight into the app's in-memory state flows, so no screen touches the
 * network or Firebase while it is captured.
 */
object DemoData {
    const val UID = "demo-user"

    val session = AuthSession(
        idToken = "demo", refreshToken = "demo", uid = UID, email = "sri@medx.example",
        displayName = "Sri Prabakaran", photoUrl = null, provider = "google.com",
        expiresAtMs = Long.MAX_VALUE,
    )

    // ---------------------------------------------------------------- QBank tree

    private var moduleSeq = 4100
    private fun mod(name: String, q: Int, chapter: String) = ModuleSummary("qb_${moduleSeq++}", name, q, chapter)
    private fun chapter(name: String, vararg modules: Pair<String, Int>) =
        BankChapter("ch_${name.hashCode()}", name, modules.map { (n, q) -> mod(n, q, name) })

    val pharmacologyChapters = listOf(
        chapter(
            "General Pharmacology",
            "Pharmacokinetics" to 48, "Pharmacodynamics" to 36, "Drug Interactions & ADRs" to 30,
        ),
        chapter(
            "Autonomic Nervous System",
            "Cholinergic Drugs" to 42, "Anticholinergics" to 28, "Adrenergic Drugs" to 45, "Alpha & Beta Blockers" to 38,
        ),
        chapter(
            "Cardiovascular Pharmacology",
            "Antihypertensives" to 52, "Antianginal Drugs" to 26, "Heart Failure" to 31, "Antiarrhythmics" to 34,
        ),
        chapter(
            "Chemotherapy",
            "Antibiotics I: Cell Wall Inhibitors" to 44, "Antitubercular Drugs" to 39, "Antimalarials" to 27,
        ),
    )

    private val ariseNames = listOf(
        "Anatomy" to 1240, "Physiology" to 980, "Biochemistry" to 860, "Pathology" to 1420,
        "Pharmacology" to 1310, "Microbiology" to 1105, "Forensic Medicine" to 540, "Community Medicine" to 1380,
        "ENT" to 610, "Ophthalmology" to 690, "Medicine" to 1820, "Surgery" to 1650,
        "Obstetrics & Gynaecology" to 1290, "Pediatrics" to 870, "Orthopedics" to 460, "Dermatology" to 380,
        "Psychiatry" to 340, "Anesthesia" to 290, "Radiology" to 310,
    )

    val subjects: List<BankSubject> = ariseNames.mapIndexed { i, (name, q) ->
        val chapters = if (name == "Pharmacology") pharmacologyChapters else emptyList()
        BankSubject(
            id = (i + 1).toString(), bank = MedxBank.ARISE, name = name, slug = name.lowercase(),
            moduleCount = if (name == "Pharmacology") 64 else 18 + (q / 40), questionCount = q, chapters = chapters,
        )
    } + listOf(
        BankSubject("mw_1", MedxBank.MARROW, "Anatomy", null, 30, 900, emptyList()),
    )

    val pharmacology get() = subjects.first { it.name == "Pharmacology" }

    // ---------------------------------------------------------------- Questions (Pharmacology)

    private fun q(id: Int, stem: String, options: List<String>, correct: Int, explanation: String, reference: String? = null) =
        Question(
            id = id, number = id, html = stem,
            options = options.mapIndexed { i, t -> QuestionOption(id * 10 + i, ('A' + i).toString(), t, i == correct) },
            correctIds = listOf(id * 10 + correct), explanation = explanation, reference = reference,
        )

    val questions = listOf(
        q(1, "Which of the following drugs is a <b>non-selective</b> beta blocker?",
            listOf("Atenolol", "Metoprolol", "Propranolol", "Bisoprolol"), 2,
            "Propranolol blocks both β1 and β2 receptors. Atenolol, metoprolol and bisoprolol are β1-selective (cardioselective)."),
        q(2, "Drug of choice for anaphylactic shock is:",
            listOf("Intravenous hydrocortisone", "Intramuscular adrenaline", "Intravenous chlorpheniramine", "Nebulised salbutamol"), 1,
            "IM adrenaline 0.5 mg (1:1000) into the anterolateral thigh is first-line. Steroids and antihistamines are adjuncts only."),
        q(3, "Atropine is used in organophosphate poisoning because it:",
            listOf("Reactivates acetylcholinesterase", "Blocks nicotinic receptors at the NMJ", "Blocks muscarinic receptors", "Increases acetylcholine breakdown"), 2,
            "Atropine antagonises the muscarinic effects (bronchorrhoea, bradycardia, miosis). Pralidoxime reactivates the enzyme; neither atropine nor oximes fix nicotinic paralysis fully."),
        q(4, "A 58-year-old man with hypertension develops a persistent <b>dry cough</b> two weeks after starting a new drug. The most likely drug is:",
            listOf("Amlodipine", "Enalapril", "Losartan", "Hydrochlorothiazide"), 1,
            "ACE inhibitors block the breakdown of bradykinin, which accumulates in the airway and causes a dry cough in up to 10–15% of patients. Switching to an ARB such as losartan avoids it, because ARBs do not affect bradykinin.",
            "KD Tripathi, Essentials of Medical Pharmacology, 8th ed., Ch. 36"),
        q(5, "Which antitubercular drug causes <b>red-orange discolouration</b> of urine?",
            listOf("Isoniazid", "Rifampicin", "Ethambutol", "Pyrazinamide"), 1,
            "Rifampicin colours urine, sweat and tears orange-red; patients should be warned in advance."),
        q(6, "Optic neuritis with red-green colour blindness is a side effect of:",
            listOf("Ethambutol", "Streptomycin", "Isoniazid", "Rifampicin"), 0,
            "Ethambutol causes dose-related retrobulbar neuritis; visual acuity and colour vision are checked at baseline."),
        q(7, "Digoxin toxicity is precipitated by:",
            listOf("Hyperkalaemia", "Hypokalaemia", "Hypercalcaemia only", "Hypermagnesaemia"), 1,
            "Low potassium increases digoxin binding to Na⁺/K⁺-ATPase, so toxicity is more likely. Hypercalcaemia and hypomagnesaemia also predispose."),
        q(8, "The antimalarial of choice for <i>P. falciparum</i> in the first trimester (India, NVBDCP) is:",
            listOf("Artesunate + SP", "Quinine + clindamycin", "Chloroquine", "Primaquine"), 1,
            "In the first trimester NVBDCP recommends quinine with clindamycin; ACT is used from the second trimester."),
        q(9, "Zero-order kinetics at therapeutic doses is seen with:",
            listOf("Phenytoin", "Paracetamol", "Gentamicin", "Warfarin"), 0,
            "Phenytoin, alcohol and high-dose aspirin saturate their metabolising enzymes, so a fixed amount is eliminated per unit time."),
        q(10, "Which of these is a <b>loop diuretic</b>?",
            listOf("Spironolactone", "Furosemide", "Acetazolamide", "Amiloride"), 1,
            "Furosemide inhibits the Na⁺-K⁺-2Cl⁻ cotransporter in the thick ascending limb of the loop of Henle."),
    )

    /** 7 right, 2 wrong, 1 skipped. */
    val responses: Map<Int, QuestionResponse> = questions.associate { q ->
        val right = q.correctIds.first()
        val r = when (q.id) {
            4 -> QuestionResponse(q.id, q.id * 10 + 2, false) // chose losartan
            7 -> QuestionResponse(q.id, q.id * 10 + 0, false)
            9 -> QuestionResponse(q.id, null, false)
            else -> QuestionResponse(q.id, right, true)
        }
        q.id to r
    }

    // ---------------------------------------------------------------- Attempts

    private fun attempt(daysAgo: Long, hour: Int, module: ModuleSummary, subject: String, total: Int, attempted: Int, score: Int, mode: String = "revision"): SittingAttempt {
        val at = LocalDate.now().minusDays(daysAgo).atTime(hour, 15).atZone(ZoneId.systemDefault()).toInstant()
        return SittingAttempt(
            id = "att_demo_${module.id}_$daysAgo", uid = UID, profile = session.name, kind = "qbank",
            sourceId = module.id, name = module.name, subject = subject, mode = mode, gradable = true,
            total = total, score = score, attempted = attempted, durationSeconds = attempted * 48,
            finishedAt = MedxDates.stamp(at), responses = emptyList(),
        )
    }

    val attempts: List<SittingAttempt> by lazy {
        val m = pharmacologyChapters.flatMap { it.modules }
        listOf(
            attempt(0, 9, m[4], "Pharmacology", 20, 20, 16),
            attempt(0, 11, m[6], "Pharmacology", 16, 14, 11),
            attempt(1, 19, m[3], "Pharmacology", 24, 24, 18),
            attempt(2, 20, m[0], "Pharmacology", 48, 40, 29),
            attempt(3, 18, m[1], "Pharmacology", 36, 30, 25),
            attempt(3, 21, m[7], "Pharmacology", 26, 26, 17),
            attempt(4, 17, m[8], "Pharmacology", 30, 28, 22),
            attempt(5, 20, m[11], "Pharmacology", 39, 35, 26),
            attempt(6, 19, m[2], "Pharmacology", 30, 22, 18, mode = "exam"),
            attempt(8, 19, m[9], "Pharmacology", 26, 26, 20),
            attempt(22, 19, m[12], "Pharmacology", 27, 27, 21),
        )
    }

    val history = listOf(
        WatchHistoryEntry(
            video = RecordedVideo(
                id = "vid_demo_1", subject = "Pharmacology", title = "Autonomic Nervous System — Part 2",
                faculty = "Dr. Gobind Rai Garg", durationSeconds = 5520, streamUrl = "",
            ),
            ownerId = UID, positionSeconds = 2310.0, durationSeconds = 5520.0, watchedAt = MedxDates.stamp(),
        ),
    )

    val flashcards = listOf(
        "Anatomy" to 186, "Biochemistry" to 142, "Community Medicine" to 205, "ENT" to 74,
        "Forensic Medicine" to 96, "Medicine" to 238, "Microbiology" to 171, "Ophthalmology" to 88,
        "Pathology" to 214, "Pharmacology" to 197, "Physiology" to 133, "Surgery" to 159,
    ).mapIndexed { i, (n, c) -> FlashcardSubject(i + 1, n, c, emptyList()) }

    /** Push the sample state into the app's singletons. Call after [Medx.init]. */
    @Suppress("UNCHECKED_CAST")
    fun install(signedIn: Boolean = true) {
        (Medx.auth.session as MutableStateFlow<AuthSession?>).value = if (signedIn) session else null
        Medx.prefs.accent.value = MedxAccent.AURORA
        Medx.prefs.appearance.value = MedxAppearance.DARK
        Medx.prefs.dailyGoal.value = 50
        Medx.prefs.examName.value = "FMGE"
        Medx.prefs.examDate.value = LocalDate.of(2027, 1, 9)
        Medx.prefs.qbankBank.value = MedxBank.ARISE.raw
        Medx.attempts.attempts.value = attempts
        Medx.attempts.stats.value = StatsMath.compute(attempts)
        Medx.attempts.syncing.value = false
        Medx.activity.history.value = history
        Medx.activity.bookmarks.value = emptyList()
        Catalog.banks.value = Load.Ready(subjects)
        Catalog.flashcards.value = Load.Ready(flashcards)
    }
}
