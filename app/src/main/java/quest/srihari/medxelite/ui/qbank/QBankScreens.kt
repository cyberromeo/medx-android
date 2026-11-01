package quest.srihari.medxelite.ui.qbank

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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import quest.srihari.medxelite.ui.components.CardShape
import quest.srihari.medxelite.ui.components.RowShape
import quest.srihari.medxelite.ui.components.Card
import quest.srihari.medxelite.ui.components.IconTile
import quest.srihari.medxelite.ui.components.ProgressBar
import quest.srihari.medxelite.ui.components.ProgressRing
import quest.srihari.medxelite.ui.components.SegmentedTabs
import quest.srihari.medxelite.ui.components.Space
import quest.srihari.medxelite.ui.components.SubjectIcon
import quest.srihari.medxelite.ui.components.softShadow
import quest.srihari.medxelite.ui.components.tappable
import quest.srihari.medxelite.ui.theme.onTint
import quest.srihari.medxelite.ui.theme.tint
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
                IconButton(onClick = { nav.go("search") }) { Icon(Icons.Filled.Search, "Search questions") }
                IconButton(onClick = { nav.go("custom") }) { Icon(Icons.Filled.Tune, "Custom modules") }
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
                            SegmentedTabs(
                                MedxBank.entries.map { it.label },
                                MedxBank.entries.indexOf(bank),
                                Modifier.padding(horizontal = Space.margin, vertical = 8.dp),
                                enabled = { i -> MedxBank.entries[i] == MedxBank.ARISE || marrowAvailable },
                            ) { i -> Medx.prefs.setBank(MedxBank.entries[i].raw) }
                        }
                        item {
                            val q = subjects.sumOf { it.questionCount }
                            val m = subjects.sumOf { it.moduleCount }
                            OverviewStrip(bank.eyebrow, listOf("${subjects.size}" to "subjects", "$m" to "modules", "%,d".format(q) to "questions"))
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
                                            subtitle = "${s.moduleCount} modules · ${"%,d".format(s.questionCount)} questions" + if (done > 0) " · $done done" else "",
                                            leading = { SubjectBadge(s.name) },
                                            divider = i < subjects.size - 1,
                                            trailingContent = {
                                                if (done > 0) {
                                                    ProgressRing(done.toFloat() / maxOf(s.moduleCount, 1), 26.dp, 3.5.dp, Mx.colors.correct, Mx.colors.sunken)
                                                }
                                            },
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

@Composable
fun SubjectBadge(name: String, size: Int = 44) {
    SubjectIcon(name, size.dp)
}

/** Big numbers, small labels: the bank at a glance. */
@Composable
fun OverviewStrip(eyebrow: String, figures: List<Pair<String, String>>) {
    val c = Mx.colors
    Column(Modifier.padding(horizontal = Space.margin, vertical = 10.dp)) {
        Text(eyebrow, style = MaterialTheme.typography.labelLarge, color = c.secondaryText, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Row(
            Modifier.fillMaxWidth().softShadow().clip(CardShape).background(c.card).padding(vertical = 16.dp),
        ) {
            figures.forEachIndexed { i, (value, label) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(value, style = MaterialTheme.typography.titleLarge, color = c.ink, maxLines = 1)
                    Text(label, style = MaterialTheme.typography.labelSmall, color = c.secondaryText, maxLines = 1)
                }
                if (i < figures.size - 1) Box(Modifier.width(1.dp).height(36.dp).background(c.separator).align(Alignment.CenterVertically))
            }
        }
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
                Box(Modifier.padding(horizontal = Space.margin, vertical = 8.dp)) {
                    Card {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SubjectIcon(subject.name, 52.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("$practised of ${subject.moduleCount} modules practised", style = MaterialTheme.typography.titleMedium, color = Mx.colors.ink)
                                Text("${subject.chapters.size} chapters · ${"%,d".format(subject.questionCount)} questions", style = MaterialTheme.typography.bodySmall, color = Mx.colors.secondaryText)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        ProgressBar(practised.toFloat() / maxOf(subject.moduleCount, 1), color = Mx.colors.correct)
                    }
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
                                if (best != null) Pill("Best $best%", Mx.colors.correct)
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
    val c = Mx.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = state, containerColor = c.groupedBackground,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { Box(Modifier.padding(top = 12.dp, bottom = 8.dp).size(width = 40.dp, height = 5.dp).clip(RoundedCornerShape(50)).background(c.separator)) },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.margin).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = c.ink)
            Spacer(Modifier.height(4.dp))
            Text("$subtitle · $questionCount questions", style = MaterialTheme.typography.bodyMedium, color = c.secondaryText)
            Spacer(Modifier.height(20.dp))
            Text("How do you want to sit it?", style = MaterialTheme.typography.titleSmall, color = c.secondaryText)
            Spacer(Modifier.height(10.dp))
            ModeCard(SittingMode.EXAM, examBlurb ?: SittingMode.EXAM.description, Icons.Rounded.Timer, c.accent) { onStart(SittingMode.EXAM) }
            Spacer(Modifier.height(10.dp))
            ModeCard(SittingMode.REVISION, SittingMode.REVISION.description, Icons.Rounded.MenuBook, Hues.green) { onStart(SittingMode.REVISION) }
            if (history.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text("Your sittings", style = MaterialTheme.typography.titleSmall, color = c.secondaryText)
                Spacer(Modifier.height(10.dp))
                Column(Modifier.softShadow().clip(CardShape).background(c.card)) {
                    history.sortedByDescending { it.finishedAt }.take(5).forEachIndexed { i, a ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(a.finishedInstant?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("d MMM, h:mm a")) ?: "Earlier", style = MaterialTheme.typography.titleSmall, color = c.ink)
                                Text(if (a.mode == "exam") "Exam" else "Revision", style = MaterialTheme.typography.bodySmall, color = c.secondaryText)
                            }
                            Pill("${a.score}/${a.total}", if (a.total > 0 && a.score * 100 / a.total >= 60) c.correct else c.accent)
                        }
                        if (i < minOf(history.size, 5) - 1) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = c.separator, thickness = 1.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeCard(mode: SittingMode, blurb: String, icon: ImageVector, hue: Color, onClick: () -> Unit) {
    val c = Mx.colors
    Row(
        Modifier.fillMaxWidth().softShadow(RowShape, 6.dp).clip(RowShape).background(c.card).tappable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, hue, 48.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(mode.displayName, style = MaterialTheme.typography.titleMedium, color = c.ink)
            Spacer(Modifier.height(2.dp))
            Text(blurb, style = MaterialTheme.typography.bodySmall, color = c.secondaryText)
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(36.dp).clip(CircleShape).background(tint(hue)), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = onTint(hue), modifier = Modifier.size(18.dp))
        }
    }
}
