package quest.srihari.medxelite.ui.qbank

import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.ui.unit.sp
import quest.srihari.medxelite.ui.components.GlassSegmented
import quest.srihari.medxelite.ui.components.IconChip
import quest.srihari.medxelite.ui.components.subjectIcon
import quest.srihari.medxelite.ui.components.glass
import androidx.compose.material.icons.rounded.Hub
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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
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
                IconButton(onClick = { nav.go("search") }) { Icon(Icons.Rounded.Search, "Search questions") }
                IconButton(onClick = { nav.go("custom") }) { Icon(Icons.Rounded.Tune, "Custom modules") }
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
                            GlassSegmented(
                                MedxBank.entries.map { it.label }, MedxBank.entries.indexOf(bank),
                                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                enabled = { i -> MedxBank.entries[i] == MedxBank.ARISE || marrowAvailable },
                            ) { i -> Medx.prefs.setBank(MedxBank.entries[i].raw) }
                        }
                        item {
                            val q = subjects.sumOf { it.questionCount }
                            val m = subjects.sumOf { it.moduleCount }
                            BankStats(bank.eyebrow, subjects.size, m, q)
                        }
                        if (subjects.isEmpty()) {
                            item { EmptyState("No subjects yet", "This bank hasn't been published yet.") }
                        } else {
                            item {
                                GroupedSection("Subjects", icon = Icons.Rounded.Hub) {
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

@Composable
fun SubjectBadge(name: String, size: Int = 36) {
    IconChip(subjectIcon(name), Hues.forName(name), size.dp)
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
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text("$practised of ${subject.moduleCount} modules practised", style = MaterialTheme.typography.titleMedium)
                    Text("${subject.chapters.size} chapters · ${"%,d".format(subject.questionCount)} questions", color = Mx.colors.secondaryText)
                    Spacer(Modifier.height(10.dp))
                    quest.srihari.medxelite.ui.components.GradientBar(practised.toFloat() / maxOf(subject.moduleCount, 1), Modifier.fillMaxWidth(), height = 5.dp)
                }
            }
            items(subject.chapters, key = { it.id }) { chapter ->
                GroupedSection(chapter.name, icon = Icons.Rounded.Layers) {
                    chapter.modules.forEachIndexed { i, m ->
                        val sittings = bySource[m.id].orEmpty()
                        val best = sittings.maxOfOrNull { it.accuracyPercentage }
                        MedxRow(
                            title = m.name,
                            subtitle = "${m.questionCount} questions" + if (sittings.isNotEmpty()) " · ${sittings.size} sitting${if (sittings.size == 1) "" else "s"}" else "",
                            divider = i < chapter.modules.size - 1,
                            trailingContent = {
                                if (best != null) {
                                    Icon(Icons.Rounded.CheckCircle, null, tint = Mx.colors.correct, modifier = Modifier.size(16.dp))
                                    Text(" $best%", style = MaterialTheme.typography.labelMedium, color = Mx.colors.secondaryText)
                                }
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
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Mx.colors.raised) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("$subtitle · $questionCount questions", color = Mx.colors.secondaryText)
            Spacer(Modifier.height(16.dp))
            ModeCard(SittingMode.EXAM, examBlurb ?: SittingMode.EXAM.description, Icons.Rounded.Timer, Mx.colors.accent) { onStart(SittingMode.EXAM) }
            Spacer(Modifier.height(10.dp))
            ModeCard(SittingMode.REVISION, SittingMode.REVISION.description, Icons.Rounded.MenuBook, Hues.green) { onStart(SittingMode.REVISION) }
            if (history.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("YOUR SITTINGS", style = MaterialTheme.typography.labelMedium, color = Mx.colors.secondaryText)
                Spacer(Modifier.height(6.dp))
                Column(Modifier.glass(RoundedCornerShape(20.dp))) {
                    history.sortedByDescending { it.finishedAt }.take(5).forEachIndexed { i, a ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(a.finishedInstant?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("d MMM, h:mm a")) ?: "Earlier", style = MaterialTheme.typography.bodyMedium)
                                Text(if (a.mode == "exam") "Exam" else "Revision", style = MaterialTheme.typography.bodySmall, color = Mx.colors.secondaryText)
                            }
                            Pill("${a.score}/${a.total}")
                        }
                        if (i < minOf(history.size, 5) - 1) HorizontalDivider(color = Mx.colors.separator, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeCard(mode: SittingMode, blurb: String, icon: ImageVector, hue: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconChip(icon, hue, 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(mode.displayName, fontWeight = FontWeight.SemiBold)
            Text(blurb, style = MaterialTheme.typography.bodySmall, color = Mx.colors.secondaryText)
        }
        Icon(Icons.Rounded.Bolt, null, tint = hue)
    }
}

/** Glass strip of the bank's totals with tabular figures. */
@Composable
private fun BankStats(eyebrow: String, subjects: Int, modules: Int, questions: Int) {
    val c = Mx.colors
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelMedium, color = c.secondaryText, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple("%,d".format(subjects), "subjects", Icons.Rounded.Hub),
                Triple("%,d".format(modules), "modules", Icons.Rounded.ViewModule),
                Triple("%,d".format(questions), "questions", Icons.Rounded.Quiz),
            ).forEach { (v, l, i) ->
                Column(Modifier.weight(1f).glass(RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 12.dp)) {
                    Icon(i, null, tint = c.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.height(6.dp))
                    quest.srihari.medxelite.ui.components.GradientText(v, quest.srihari.medxelite.ui.theme.StatStyle.copy(fontSize = 20.sp), maxLines = 1)
                    Text(l, style = MaterialTheme.typography.labelSmall, color = c.secondaryText)
                }
            }
        }
    }
}
