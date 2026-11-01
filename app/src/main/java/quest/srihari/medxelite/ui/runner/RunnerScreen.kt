package quest.srihari.medxelite.ui.runner

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import quest.srihari.medxelite.Medx
import quest.srihari.medxelite.data.Question
import quest.srihari.medxelite.data.QuestionOption
import quest.srihari.medxelite.data.RunnerPayload
import quest.srihari.medxelite.ui.Navigator
import quest.srihari.medxelite.ui.components.EmptyState
import quest.srihari.medxelite.ui.components.HtmlText
import quest.srihari.medxelite.ui.components.LoadingState
import quest.srihari.medxelite.ui.components.Pill
import quest.srihari.medxelite.ui.components.formatDuration
import quest.srihari.medxelite.ui.theme.Mx

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunnerScreen(nav: Navigator, payload: RunnerPayload?) {
    if (payload == null) {
        EmptyState("Sitting unavailable", "Open the paper again to start.", actionTitle = "Back") { nav.back() }
        return
    }
    val context = LocalContext.current
    val view = LocalView.current
    val state = remember(payload) { RunnerState(payload, context.applicationContext) }
    val scope = rememberCoroutineScope()
    val bookmarks by Medx.activity.bookmarks.collectAsState()
    val haptics by Medx.prefs.hapticsOn.collectAsState()
    var showExit by remember { mutableStateOf(false) }
    var showNavigator by remember { mutableStateOf(false) }
    var showSubmit by remember { mutableStateOf(false) }

    fun buzz(kind: Int = HapticFeedbackConstants.CLOCK_TICK) { if (haptics) view.performHapticFeedback(kind) }

    LaunchedEffect(state) { state.loadQuestions() }
    LaunchedEffect(state) {
        while (true) {
            delay(1000)
            if (state.tick()) buzz(HapticFeedbackConstants.LONG_PRESS)
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_PAUSE) state.persistSnapshot()
            if (e == Lifecycle.Event.ON_RESUME) state.tick()
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    if (state.finished) {
        SittingReview(
            name = payload.name,
            subject = payload.subject,
            sourceId = payload.id,
            questions = state.questions,
            responses = state.responses.toMap(),
            gradable = payload.gradable,
            elapsedSeconds = state.completedSeconds,
            sections = state.sectionLog,
            tags = payload.questionTags,
            onDone = { nav.back() },
        )
        return
    }

    fun requestClose() {
        if (state.load == RunnerLoad.Ready && state.responses.isNotEmpty()) showExit = true else nav.back()
    }
    BackHandler { requestClose() }

    fun isMarked(q: Question) = bookmarks.any { it.question.id == q.id && it.sourceId == payload.id }

    Scaffold(
        containerColor = Mx.colors.groupedBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Mx.colors.groupedBackground),
                navigationIcon = { IconButton(onClick = ::requestClose) { Icon(Icons.Filled.Close, "Close") } },
                title = {
                    Column {
                        val ready = state.load == RunnerLoad.Ready && state.questions.isNotEmpty()
                        Text(
                            if (ready) "Question ${state.currentIndex + 1} of ${state.questions.size}" else payload.name,
                            style = MaterialTheme.typography.titleMedium, maxLines = 1,
                        )
                        val parts = mutableListOf(if (state.isExam) "Exam" else "Revision")
                        if (state.isSectioned) parts.add("Block ${state.sectionIndex + 1} of ${state.sections.size}")
                        if (ready) parts.add(payload.name)
                        Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = Mx.colors.secondaryText, maxLines = 1)
                    }
                },
                actions = {
                    if (state.load == RunnerLoad.Ready) {
                        Clock(state)
                        val q = state.currentQuestion
                        if (q != null) {
                            val marked = isMarked(q)
                            IconButton(onClick = {
                                Medx.activity.toggleBookmark(q, payload.id, payload.name, payload.subject)
                                buzz()
                            }) {
                                Icon(if (marked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, "Bookmark", tint = if (marked) Mx.colors.flagged else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (state.load == RunnerLoad.Ready) {
                Row(
                    Modifier.fillMaxWidth().background(Mx.colors.card).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { state.goBack(); buzz() }, enabled = state.canGoBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Spacer(Modifier.width(4.dp)); Text("Previous")
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { showNavigator = true }) { Icon(Icons.Filled.GridView, "Questions") }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { buzz(); if (state.isLastQuestion) showSubmit = true else state.next() },
                        enabled = state.canAdvanceNow,
                        colors = if (state.isLastQuestion) ButtonDefaults.buttonColors(containerColor = Mx.colors.correct) else ButtonDefaults.buttonColors(),
                    ) {
                        Text(if (state.isLastQuestion) "Submit" else "Next")
                        Spacer(Modifier.width(4.dp))
                        Icon(if (state.isLastQuestion) Icons.Filled.Check else Icons.AutoMirrored.Filled.ArrowForward, null)
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (val l = state.load) {
                RunnerLoad.Loading -> LoadingState("Preparing your sitting", payload.name)
                is RunnerLoad.Unavailable -> EmptyState("Sitting unavailable", l.message, actionTitle = "Try again") {
                    scope.launch { state.loadQuestions() }
                }
                RunnerLoad.Ready -> {
                    val q = state.currentQuestion
                    if (q != null) {
                        Column(Modifier.fillMaxSize()) {
                            TimeBar(state)
                            QuestionPane(state, q, payload) { optionId ->
                                when (state.pick(q, optionId)) {
                                    true -> buzz(HapticFeedbackConstants.CONFIRM)
                                    false -> buzz(HapticFeedbackConstants.REJECT)
                                    null -> buzz()
                                }
                            }
                        }
                    }
                }
            }
            state.handover?.let { h -> HandoverOverlay(h) { state.beginActiveSection() } }
        }
    }

    if (showExit) {
        AlertDialog(
            onDismissRequest = { showExit = false },
            title = { Text("Leave sitting?") },
            text = { Text("Save and leave keeps your answers and the clock where they are, so opening this paper again picks up at question ${state.currentIndex + 1}.") },
            confirmButton = {
                TextButton(onClick = { state.persistSnapshot(); showExit = false; nav.back() }) { Text("Save and leave") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { state.clearSnapshot(); showExit = false; nav.back() }) { Text("Discard", color = Mx.colors.wrong) }
                    TextButton(onClick = { showExit = false }) { Text("Keep going") }
                }
            },
        )
    }

    state.resumeOffer?.let { snap ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Pick up where you left off?") },
            text = { Text(state.resumeSummary(snap)) },
            confirmButton = { TextButton(onClick = { state.applySnapshot(snap) }) { Text("Resume") } },
            dismissButton = { TextButton(onClick = { state.clearSnapshot(); state.resumeOffer = null }) { Text("Start over", color = Mx.colors.wrong) } },
        )
    }

    if (showSubmit) {
        val unanswered = state.questionsInSection.size - state.sectionAnswered
        val flagged = state.questionsInSection.count { isMarked(it) }
        val sectionMid = state.isSectioned && !state.isFinalSection
        AlertDialog(
            onDismissRequest = { showSubmit = false },
            title = { Text(if (sectionMid) "Submit ${state.activeSection.label}?" else "Submit the paper?") },
            text = {
                Column {
                    Text("${state.sectionAnswered} answered · $unanswered unanswered · $flagged bookmarked")
                    if (state.isExam) Text("Time left: ${formatDuration(state.remainingSeconds)}", color = Mx.colors.secondaryText)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (sectionMid) "A submitted block cannot be reopened. The next block's clock starts when you open it."
                        else "Your answers are scored and saved to your history.",
                        color = Mx.colors.secondaryText,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showSubmit = false; state.submitSection(); buzz(HapticFeedbackConstants.CONFIRM) }) { Text("Submit") } },
            dismissButton = {
                val first = state.firstUnansweredInSection()
                if (first != null) TextButton(onClick = { showSubmit = false; state.jump(first) }) { Text("Review unanswered") }
                else TextButton(onClick = { showSubmit = false }) { Text("Cancel") }
            },
        )
    }

    if (showNavigator) {
        NavigatorSheet(state, ::isMarked, onDismiss = { showNavigator = false }) { i ->
            showNavigator = false
            if (!state.jump(i)) buzz(HapticFeedbackConstants.REJECT)
        }
    }
}

@Composable
private fun Clock(state: RunnerState) {
    val color = when (state.phase) {
        ClockPhase.CALM -> MaterialTheme.colorScheme.onSurface
        ClockPhase.LOW -> Mx.colors.flagged
        ClockPhase.CRITICAL -> Mx.colors.wrong
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
        Icon(Icons.Filled.Timer, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(2.dp))
        Text(formatDuration(state.displaySeconds), color = color, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
private fun TimeBar(state: RunnerState) {
    val fraction = state.displaySeconds.toFloat() / maxOf(state.capacitySeconds, 1)
    val color by animateColorAsState(
        when (state.phase) {
            ClockPhase.CALM -> Mx.colors.accent
            ClockPhase.LOW -> Mx.colors.flagged
            ClockPhase.CRITICAL -> Mx.colors.wrong
        },
        label = "clock",
    )
    LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, color = color, trackColor = Mx.colors.separator, modifier = Modifier.fillMaxWidth().height(3.dp))
}

@Composable
private fun QuestionPane(state: RunnerState, q: Question, payload: RunnerPayload, onPick: (Int) -> Unit) {
    val scroll = rememberScrollState()
    LaunchedEffect(q.id) { scroll.scrollTo(0) }
    val response = state.responses[q.id]
    val revealed = !state.isExam && state.revealed[q.id] == true
    val locked = !state.isExam && response != null
    var dragTotal by remember { mutableStateOf(0f) }
    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(q.id) {
                detectHorizontalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onDragEnd = {
                        if (dragTotal < -160 && state.canAdvanceNow && !state.isLastQuestion) state.next()
                        else if (dragTotal > 160 && state.canGoBack) state.goBack()
                    },
                ) { _, amount -> dragTotal += amount }
            }
            .verticalScroll(scroll)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        val tag = payload.questionTags?.getOrNull(state.currentIndex)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!tag.isNullOrEmpty()) { Pill(tag); Spacer(Modifier.width(6.dp)) }
            if (!q.hasAnswerKey) Pill("No answer key", Mx.colors.flagged)
        }
        HtmlText(q.displayText, sizeSp = 18f, bold = true, extraImages = q.images.orEmpty())
        Spacer(Modifier.height(16.dp))
        q.options.forEachIndexed { i, opt ->
            OptionButton(
                option = opt, index = i,
                chosen = response?.chosenId == opt.id,
                correct = q.isCorrect(opt.id),
                revealed = revealed, locked = locked,
            ) { onPick(opt.id) }
            Spacer(Modifier.height(10.dp))
        }
        if (revealed) {
            if (response?.timedOut == true) {
                Text("Time's up — the answer is shown above.", color = Mx.colors.flagged, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
            }
            Explanation(q)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun OptionButton(
    option: QuestionOption,
    index: Int,
    chosen: Boolean,
    correct: Boolean,
    revealed: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
) {
    val accent = Mx.colors.accent
    val (border, fill) = when {
        revealed && correct -> Mx.colors.correct to Mx.colors.correct.copy(alpha = 0.12f)
        revealed && chosen -> Mx.colors.wrong to Mx.colors.wrong.copy(alpha = 0.12f)
        chosen -> accent to accent.copy(alpha = 0.12f)
        else -> Color.Transparent to Mx.colors.card
    }
    val letter = option.label.ifBlank { ('A' + index).toString() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(fill)
            .border(1.5.dp, border, RoundedCornerShape(14.dp))
            .clickable(enabled = !locked, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val badge = when {
            revealed && correct -> Mx.colors.correct
            revealed && chosen -> Mx.colors.wrong
            chosen -> accent
            else -> Mx.colors.separator
        }
        Box(Modifier.size(30.dp).clip(CircleShape).background(badge), contentAlignment = Alignment.Center) {
            Text(letter, color = if (chosen || (revealed && correct)) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        HtmlText(option.text, Modifier.weight(1f), sizeSp = 16f)
        if (revealed && correct) Icon(Icons.Filled.Check, null, tint = Mx.colors.correct)
        if (revealed && chosen && !correct) Icon(Icons.Filled.Close, null, tint = Mx.colors.wrong)
    }
}

@Composable
fun Explanation(q: Question) {
    val text = q.explanation
    if (text.isNullOrBlank() && q.reference.isNullOrBlank()) return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Mx.colors.card).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lightbulb, null, tint = Mx.colors.flagged, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Explanation", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        if (!text.isNullOrBlank()) HtmlText(text, sizeSp = 15f)
        if (!q.reference.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            HtmlText("<i>${q.reference}</i>", sizeSp = 13f, color = Mx.colors.secondaryText)
        }
    }
}

@Composable
private fun HandoverOverlay(h: Handover, onBegin: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp).widthIn(max = 420.dp).clip(RoundedCornerShape(24.dp)).background(Mx.colors.card).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("${h.closedLabel} submitted", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                if (h.gradable) "${h.score} correct · ${h.attempted} of ${h.total} attempted" else "${h.attempted} of ${h.total} attempted",
                color = Mx.colors.secondaryText,
            )
            Spacer(Modifier.height(16.dp))
            Text("Next: ${h.nextLabel}", fontWeight = FontWeight.SemiBold)
            Text("${h.nextCount} questions · ${h.nextMinutes} minutes · ${h.remainingSections} block${if (h.remainingSections == 1) "" else "s"} left", color = Mx.colors.secondaryText, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onBegin, modifier = Modifier.fillMaxWidth()) { Text("Start ${h.nextLabel}") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NavigatorSheet(state: RunnerState, isMarked: (Question) -> Boolean, onDismiss: () -> Unit, onJump: (Int) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(if (state.isSectioned) state.activeSection.label else "Questions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${state.sectionAnswered} of ${state.questionsInSection.size} answered", color = Mx.colors.secondaryText)
            Spacer(Modifier.height(12.dp))
            val s = state.activeSection
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                for (i in s.start until minOf(s.end, state.questions.size)) {
                    val q = state.questions[i]
                    val r = state.responses[q.id]
                    val locked = !state.isExam && i > state.furthestIndex
                    val fill = when {
                        r == null -> Mx.colors.card
                        !state.isExam && r.correct -> Mx.colors.correct
                        !state.isExam -> Mx.colors.wrong
                        r.chosenId != null -> Mx.colors.accent
                        else -> Mx.colors.card
                    }
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(fill)
                            .border(if (i == state.currentIndex) 2.dp else 0.5.dp, if (i == state.currentIndex) Mx.colors.accent else Mx.colors.separator, RoundedCornerShape(10.dp))
                            .clickable(enabled = !locked) { onJump(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${i + 1}", color = if (r != null) Color.White else if (locked) Mx.colors.tertiaryText else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        if (isMarked(q)) {
                            Box(Modifier.align(Alignment.TopEnd).padding(3.dp).size(7.dp).clip(CircleShape).background(Mx.colors.flagged))
                        }
                    }
                }
            }
        }
    }
}
