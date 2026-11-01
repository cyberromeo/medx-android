package quest.srihari.medxelite.ui.runner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import quest.srihari.medxelite.ui.components.DotDivider
import quest.srihari.medxelite.ui.components.DotText
import quest.srihari.medxelite.ui.components.DotValue
import quest.srihari.medxelite.ui.components.NCircleButton
import quest.srihari.medxelite.ui.components.NLabel
import quest.srihari.medxelite.ui.components.NPill
import quest.srihari.medxelite.ui.components.NWidget
import quest.srihari.medxelite.ui.components.RecDot
import quest.srihari.medxelite.ui.components.WidgetHeader
import quest.srihari.medxelite.ui.components.WidgetShape
import quest.srihari.medxelite.ui.theme.NType
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Timer
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
            val ready = state.load == RunnerLoad.Ready && state.questions.isNotEmpty()
            Column(Modifier.fillMaxWidth().background(Mx.colors.groupedBackground).statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NCircleButton(Icons.Outlined.Close, "Close", ::requestClose)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        if (ready) {
                            DotValue("%02d".format(state.currentIndex + 1), "/ ${state.questions.size}", size = 34.sp)
                            Spacer(Modifier.height(4.dp))
                        } else {
                            Text(payload.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        val parts = mutableListOf(if (state.isExam) "Exam" else "Revision")
                        if (state.isSectioned) parts.add("Block ${state.sectionIndex + 1}/${state.sections.size}")
                        if (ready) parts.add(payload.name)
                        NLabel(parts.joinToString(" · "), size = 10.sp)
                    }
                    if (state.load == RunnerLoad.Ready) {
                        Clock(state)
                        Spacer(Modifier.width(8.dp))
                        val q = state.currentQuestion
                        if (q != null) {
                            val marked = isMarked(q)
                            NCircleButton(
                                if (marked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, "Bookmark",
                                {
                                    Medx.activity.toggleBookmark(q, payload.id, payload.name, payload.subject)
                                    buzz()
                                },
                                tint = if (marked) Mx.colors.accent else null,
                            )
                        }
                    }
                }
                if (ready) {
                    QuestionDots(state, Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
                    TimeBar(state)
                }
            }
        },
        bottomBar = {
            if (state.load == RunnerLoad.Ready) {
                Column(Modifier.fillMaxWidth().background(Mx.colors.groupedBackground)) {
                    HorizontalDivider(color = Mx.colors.hairline, thickness = 1.dp)
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NCircleButton(Icons.AutoMirrored.Outlined.ArrowBack, "Previous", { state.goBack(); buzz() }, enabled = state.canGoBack, size = 52.dp)
                        Spacer(Modifier.width(10.dp))
                        NCircleButton(Icons.Outlined.GridView, "Questions", { showNavigator = true }, size = 52.dp)
                        Spacer(Modifier.weight(1f))
                        NPill(
                            if (state.isLastQuestion) "Submit" else "Next",
                            onClick = { buzz(); if (state.isLastQuestion) showSubmit = true else state.next() },
                            enabled = state.canAdvanceNow,
                            accent = state.isLastQuestion,
                            trailingIcon = if (state.isLastQuestion) Icons.Outlined.Check else Icons.AutoMirrored.Outlined.ArrowForward,
                            modifier = Modifier.widthIn(min = 140.dp).height(52.dp),
                        )
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
        ClockPhase.CALM -> Mx.colors.ink
        ClockPhase.LOW -> Mx.colors.accent
        ClockPhase.CRITICAL -> Mx.colors.wrong
    }
    Row(
        Modifier.clip(CircleShape).border(1.dp, if (state.phase == ClockPhase.CALM) Mx.colors.hairline else color, CircleShape).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.phase != ClockPhase.CALM) { RecDot(size = 6.dp, color = color); Spacer(Modifier.width(6.dp)) }
        Text(
            formatDuration(state.displaySeconds), color = color,
            style = TextStyle(fontFamily = NType.mono, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.5.sp),
        )
    }
}

@Composable
private fun TimeBar(state: RunnerState) {
    val fraction = state.displaySeconds.toFloat() / maxOf(state.capacitySeconds, 1)
    val color = when (state.phase) {
        ClockPhase.CALM -> Mx.colors.ink.copy(alpha = 0.28f)
        ClockPhase.LOW -> Mx.colors.accent
        ClockPhase.CRITICAL -> Mx.colors.wrong
    }
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) }, color = color, trackColor = Mx.colors.hairline,
        drawStopIndicator = {}, gapSize = 0.dp,
        modifier = Modifier.fillMaxWidth().height(1.5.dp),
    )
}

/** One dot per question in the current block: lit = answered, red = wrong, ring = current. */
@Composable
private fun QuestionDots(state: RunnerState, modifier: Modifier = Modifier) {
    val c = Mx.colors
    val s = state.activeSection
    val end = minOf(s.end, state.questions.size)
    val count = end - s.start
    if (count <= 0) return
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val maxR = 4.dp.toPx()
        val step = if (count > 1) size.width / count else size.width
        val r = minOf(maxR, step * 0.32f)
        for (k in 0 until count) {
            val i = s.start + k
            val q = state.questions[i]
            val resp = state.responses[q.id]
            val cx = step * k + step / 2
            val cy = size.height / 2
            val color = when {
                resp == null || resp.chosenId == null -> c.dotOff
                !state.isExam && !resp.correct -> c.wrong
                else -> c.ink
            }
            if (i == state.currentIndex) {
                drawCircle(c.ink, r + 1.5.dp.toPx(), androidx.compose.ui.geometry.Offset(cx, cy), style = Stroke(1.5.dp.toPx()))
                if (resp != null && resp.chosenId != null) drawCircle(color, r * 0.7f, androidx.compose.ui.geometry.Offset(cx, cy))
            } else {
                drawCircle(color, r, androidx.compose.ui.geometry.Offset(cx, cy))
            }
        }
    }
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
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        val tag = payload.questionTags?.getOrNull(state.currentIndex)
        if (!tag.isNullOrEmpty() || !q.hasAnswerKey) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!tag.isNullOrEmpty()) { Pill(tag); Spacer(Modifier.width(6.dp)) }
                if (!q.hasAnswerKey) Pill("No answer key", Mx.colors.flagged)
            }
            Spacer(Modifier.height(10.dp))
        }
        HtmlText(q.displayText, sizeSp = 18f, bold = true, extraImages = q.images.orEmpty())
        Spacer(Modifier.height(20.dp))
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
                NLabel("Time's up · answer shown above", color = Mx.colors.accent)
                Spacer(Modifier.height(8.dp))
            }
            Explanation(q)
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Answer option. Selected (not yet revealed) and correct are an inverted ink fill; a wrong pick is
 * outlined in red with a red letter chip; everything else is a flat widget row with a hairline.
 */
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
    val c = Mx.colors
    val shape = RoundedCornerShape(22.dp)
    val inverted = (revealed && correct) || (!revealed && chosen)
    val wrongPick = revealed && chosen && !correct
    val fill = when {
        inverted -> c.ink
        else -> c.card
    }
    val border = when {
        inverted -> c.ink
        wrongPick -> c.wrong
        else -> c.hairline
    }
    val textColor = if (inverted) c.onInk else MaterialTheme.colorScheme.onSurface
    val dim = revealed && !correct && !chosen
    val letter = option.label.ifBlank { ('A' + index).toString() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(shape)
            .background(fill)
            .border(if (wrongPick) 1.5.dp else 1.dp, border, shape)
            .clickable(enabled = !locked, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (chipBg, chipFg, chipBorder) = when {
            inverted -> Triple(c.onInk, c.ink, c.onInk)
            wrongPick -> Triple(c.wrong, Color.White, c.wrong)
            else -> Triple(Color.Transparent, c.ink, c.separator)
        }
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(chipBg).border(1.dp, chipBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(letter, color = chipFg, style = TextStyle(fontFamily = NType.mono, fontWeight = FontWeight.Bold, fontSize = 15.sp))
        }
        Spacer(Modifier.width(14.dp))
        HtmlText(option.text, Modifier.weight(1f), sizeSp = 16f, color = if (dim) c.secondaryText else textColor)
        if (revealed && correct) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Outlined.Check, "Correct answer", tint = c.onInk, modifier = Modifier.size(22.dp))
        }
        if (wrongPick) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Outlined.Close, "Your answer, wrong", tint = c.wrong, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun Explanation(q: Question) {
    val text = q.explanation
    if (text.isNullOrBlank() && q.reference.isNullOrBlank()) return
    NWidget(Modifier.fillMaxWidth()) {
        WidgetHeader("Explanation")
        Spacer(Modifier.height(10.dp))
        if (!text.isNullOrBlank()) HtmlText(text, sizeSp = 15f)
        if (!q.reference.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            DotDivider()
            Spacer(Modifier.height(10.dp))
            HtmlText("<i>${q.reference}</i>", sizeSp = 13f, color = Mx.colors.secondaryText)
        }
    }
}

@Composable
private fun HandoverOverlay(h: Handover, onBegin: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp).widthIn(max = 420.dp).clip(WidgetShape).background(Mx.colors.card).border(1.dp, Mx.colors.hairline, WidgetShape).padding(24.dp),
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
            NPill("Start ${h.nextLabel}", onBegin, Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NavigatorSheet(state: RunnerState, isMarked: (Question) -> Boolean, onDismiss: () -> Unit, onJump: (Int) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Mx.colors.card) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            WidgetHeader(if (state.isSectioned) state.activeSection.label else "Questions")
            Spacer(Modifier.height(6.dp))
            DotValue("${state.sectionAnswered}", "/ ${state.questionsInSection.size} answered", size = 44.sp)
            Spacer(Modifier.height(12.dp))
            val s = state.activeSection
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                for (i in s.start until minOf(s.end, state.questions.size)) {
                    val q = state.questions[i]
                    val r = state.responses[q.id]
                    val locked = !state.isExam && i > state.furthestIndex
                    val fill = when {
                        r == null -> Mx.colors.card
                        !state.isExam && r.correct -> Mx.colors.ink
                        !state.isExam -> Mx.colors.wrong
                        r.chosenId != null -> Mx.colors.ink
                        else -> Mx.colors.card
                    }
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(fill)
                            .border(if (i == state.currentIndex) 2.dp else 1.dp, if (i == state.currentIndex) Mx.colors.accent else Mx.colors.hairline, CircleShape)
                            .clickable(enabled = !locked) { onJump(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        val onFill = when {
                            r == null -> if (locked) Mx.colors.tertiaryText else MaterialTheme.colorScheme.onSurface
                            fill == Mx.colors.wrong -> Color.White
                            fill == Mx.colors.ink -> Mx.colors.onInk
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Text("${i + 1}", color = onFill, style = TextStyle(fontFamily = NType.mono, fontWeight = FontWeight.Bold, fontSize = 14.sp))
                        if (isMarked(q)) {
                            Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(8.dp).clip(CircleShape).background(Mx.colors.accent).border(1.5.dp, Mx.colors.card, CircleShape))
                        }
                    }
                }
            }
        }
    }
}
