package quest.srihari.medxelite.ui.qbank

import androidx.compose.foundation.border
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.ui.unit.sp
import quest.srihari.medxelite.ui.components.DotProgress
import quest.srihari.medxelite.ui.components.DotStat
import quest.srihari.medxelite.ui.components.DotText
import quest.srihari.medxelite.ui.components.DotValue
import quest.srihari.medxelite.ui.components.NChip
import quest.srihari.medxelite.ui.components.NLabel
import quest.srihari.medxelite.ui.components.NSegmented
import quest.srihari.medxelite.ui.components.NWidget
import quest.srihari.medxelite.ui.components.WidgetHeader
import quest.srihari.medxelite.ui.components.WidgetShape
import quest.srihari.medxelite.ui.theme.NType
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import quest.srihari.medxelite.Medx
import quest.srihari.medxelite.data.BankSubject
import quest.srihari.medxelite.data.Catalog
import quest.srihari.medxelite.data.Load
import quest.srihari.medxelite.data.MedxBank
import quest.srihari.medxelite.data.ModuleSummary
import quest.srihari.medxelite.data.RunnerPayload
import quest.srihari.medxelite.data.SittingAttempt
import quest.srihari.medxelite.data.SittingMode
import quest.srihari.medxelite.ui.Navigator
import quest.srihari.medxelite.ui.components.EmptyState
import quest.srihari.medxelite.ui.components.GroupedSection
import quest.srihari.medxelite.ui.components.LoadingState
import quest.srihari.medxelite.ui.components.MedxRow
import quest.srihari.medxelite.ui.components.MedxTopBar
import quest.srihari.medxelite.ui.components.Pill
import quest.srihari.medxelite.ui.theme.Hues
import quest.srihari.medxelite.ui.theme.Mx
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QBankScreen(nav: Navigator, modifier: Modifier = Modifier) {
    val banks by Catalog.banks.collectAsState()
    val bankRaw by Medx.prefs.qbankBank.collectAsState()
    val attempts by Medx.attempts.attempts.collectAsState()
    val bank = MedxBank.fromRaw(bankRaw) ?: MedxBank.ARISE
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    LaunchedEffect(Unit) { Catalog.loadBanks() }

    val practised = remember(attempts) { attempts.filter { it.kind == "qbank" }.map { it.sourceId }.toSet() }

    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        containerColor = Mx.colors.groupedBackground,
        topBar = {
            MedxTopBar("QBank", large = true, scrollBehavior = scroll, actions = {
                IconButton(onClick = { nav.go("search") }) { Icon(Icons.Outlined.Search, "Search questions") }
                IconButton(onClick = { nav.go("custom") }) { Icon(Icons.Outlined.Tune, "Custom modules") }
            })
        },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; Catalog.loadBanks(force = true); refreshing = false } },
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) {
            when (val state = banks) {
                is Load.Loading -> LoadingState("Loading the question bank")
                is Load.Failed -> EmptyState("Couldn't load QBank", state.message, actionTitle = "Try again") {
                    scope.launch { Catalog.loadBanks(force = true) }
                }
                is Load.Ready -> {
                    val subjects = state.value.filter { it.bank == bank }
                    val marrowAvailable = state.value.any { it.bank == MedxBank.MARROW }
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        item {
                            NSegmented(
                                MedxBank.entries, bank, { it.label }, { Medx.prefs.setBank(it.raw) },
                                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                enabled = { it == MedxBank.ARISE || marrowAvailable },
                            )
                        }
                        item {
                            val q = subjects.sumOf { it.questionCount }
                            val m = subjects.sumOf { it.moduleCount }
                            NWidget(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
                                WidgetHeader(bank.eyebrow)
                                Spacer(Modifier.height(14.dp))
                                Row(Modifier.fillMaxWidth()) {
                                    DotStat("${subjects.size}", "subjects", Modifier.weight(0.8f))
                                    DotStat("$m", "modules", Modifier.weight(1f))
                                    DotStat(compactCount(q), "questions", Modifier.weight(1.1f))
                                }
                            }
                        }
                        if (subjects.isEmpty()) {
                            item { EmptyState("No subjects yet", "This bank hasn't been published yet.") }
                        } else {
                            item {
                                GroupedSection("Subjects") {
                                    subjects.forEachIndexed { i, s ->
                                        val done = s.modules.count { practised.contains(it.id) }
                                        MedxRow(
                                            title = s.name,
                                            subtitle = "${s.moduleCount} modules · ${"%,d".format(s.questionCount)} questions",
                                            leading = { SubjectBadge(s.name) },
                                            trailing = if (done > 0) "$done of ${s.moduleCount}" else null,
                                            divider = i < subjects.size - 1,
                                        ) { nav.go("qbank/subject/${s.id}") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 12,345 -> "12.3K" so big counts fit a dot-matrix stat. */
fun compactCount(n: Int): String = when {
    n >= 100_000 -> "${n / 1000}K"
    n >= 10_000 -> "%.1fK".format(n / 1000.0)
    else -> n.toString()
}

/** Two-letter subject monogram on a raised disc. */
@Composable
fun SubjectBadge(name: String, size: Int = 38) {
    val c = Mx.colors
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(c.raised).border(1.dp, c.hairline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.filter { it.isLetter() }.take(2).uppercase(), color = c.ink,
            style = androidx.compose.ui.text.TextStyle(fontFamily = NType.mono, fontWeight = FontWeight.Bold, fontSize = (size * 0.32f).sp, letterSpacing = 0.5.sp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectScreen(nav: Navigator, subjectId: String) {
    val banks by Catalog.banks.collectAsState()
    val attempts by Medx.attempts.attempts.collectAsState()
    LaunchedEffect(Unit) { Catalog.loadBanks() }
    val subject = (banks as? Load.Ready)?.value?.firstOrNull { it.id == subjectId }
    var pick by remember { mutableStateOf<ModuleSummary?>(null) }
    val bySource = remember(attempts) { attempts.filter { it.kind == "qbank" }.groupBy { it.sourceId } }

    Scaffold(
        containerColor = Mx.colors.groupedBackground,
        topBar = { MedxTopBar(subject?.name ?: "Subject", onBack = nav::back) },
    ) { pad ->
        if (subject == null) {
            Box(Modifier.padding(pad)) { LoadingState() }
            return@Scaffold
        }
        val practised = subject.modules.count { bySource.containsKey(it.id) }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                NWidget(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
                    WidgetHeader("Progress", trailing = { NLabel("${subject.chapters.size} chapters") })
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        DotValue("$practised", "/ ${subject.moduleCount}", size = 56.sp)
                        Spacer(Modifier.width(12.dp))
                        NLabel("modules practised", Modifier.padding(bottom = 10.dp).weight(1f), maxLines = 2)
                    }
                    Spacer(Modifier.height(12.dp))
                    DotProgress(practised.toFloat() / maxOf(subject.moduleCount, 1), dots = 32, dotSize = 5.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("${"%,d".format(subject.questionCount)} questions across ${subject.chapters.size} chapters", style = MaterialTheme.typography.bodySmall, color = Mx.colors.secondaryText)
                }
            }
            items(subject.chapters, key = { it.id }) { chapter ->
                GroupedSection(chapter.name) {
                    chapter.modules.forEachIndexed { i, m ->
                        val sittings = bySource[m.id].orEmpty()
                        val best = sittings.maxOfOrNull { it.accuracyPercentage }
                        MedxRow(
                            title = m.name,
                            subtitle = "${m.questionCount} questions" + if (sittings.isNotEmpty()) " · ${sittings.size} sitting${if (sittings.size == 1) "" else "s"}" else "",
                            divider = i < chapter.modules.size - 1,
                            trailingContent = {
                                if (best != null) NChip("$best%")
                            },
                        ) { pick = m }
                    }
                }
            }
        }
    }

    pick?.let { m ->
        StartSessionSheet(
            title = m.name,
            subtitle = subject?.name ?: "",
            questionCount = m.questionCount,
            history = bySource[m.id].orEmpty(),
            onDismiss = { pick = null },
        ) { mode ->
            pick = null
            nav.startSitting(RunnerPayload("qbank", m.id, m.name, subject?.name ?: "", mode))
        }
    }
}

/** Exam / Revision picker before a sitting (StartSessionSheet.swift). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartSessionSheet(
    title: String,
    subtitle: String,
    questionCount: Int,
    history: List<SittingAttempt> = emptyList(),
    examBlurb: String? = null,
    onDismiss: () -> Unit,
    onStart: (SittingMode) -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Mx.colors.groupedBackground) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            NLabel(subtitle)
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                DotValue("$questionCount", "questions", size = 44.sp)
            }
            Spacer(Modifier.height(18.dp))
            ModeCard(SittingMode.EXAM, examBlurb ?: SittingMode.EXAM.description, Icons.Outlined.Timer, filled = true) { onStart(SittingMode.EXAM) }
            Spacer(Modifier.height(10.dp))
            ModeCard(SittingMode.REVISION, SittingMode.REVISION.description, Icons.Outlined.MenuBook, filled = false) { onStart(SittingMode.REVISION) }
            if (history.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                NLabel("Your sittings", Modifier.padding(start = 6.dp))
                Spacer(Modifier.height(8.dp))
                Column(Modifier.clip(RoundedCornerShape(24.dp)).background(Mx.colors.card).border(1.dp, Mx.colors.hairline, RoundedCornerShape(24.dp)).padding(vertical = 4.dp)) {
                    history.sortedByDescending { it.finishedAt }.take(5).forEachIndexed { i, a ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(a.finishedInstant?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("d MMM, h:mm a")) ?: "Earlier", style = MaterialTheme.typography.bodyMedium)
                                Text(if (a.mode == "exam") "Exam" else "Revision", style = MaterialTheme.typography.bodySmall, color = Mx.colors.secondaryText)
                            }
                            Pill("${a.score}/${a.total}")
                        }
                        if (i < minOf(history.size, 5) - 1) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Mx.colors.hairline, thickness = 1.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeCard(mode: SittingMode, blurb: String, icon: ImageVector, filled: Boolean, onClick: () -> Unit) {
    val c = Mx.colors
    Row(
        Modifier.fillMaxWidth().clip(WidgetShape).background(c.card).border(1.dp, if (filled) c.ink.copy(alpha = 0.5f) else c.hairline, WidgetShape)
            .clickable(onClick = onClick).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(if (filled) c.ink else c.raised).border(1.dp, if (filled) c.ink else c.hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (filled) c.onInk else c.ink, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(mode.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(blurb, style = MaterialTheme.typography.bodySmall, color = c.secondaryText)
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = if (filled) c.accent else c.tertiaryText, modifier = Modifier.size(20.dp))
    }
}
