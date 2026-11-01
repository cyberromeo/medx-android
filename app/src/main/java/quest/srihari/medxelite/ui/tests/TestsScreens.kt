package quest.srihari.medxelite.ui.tests

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.sp
import quest.srihari.medxelite.ui.components.DotStat
import quest.srihari.medxelite.ui.components.NSegmented
import quest.srihari.medxelite.ui.components.NWidget
import quest.srihari.medxelite.ui.components.WidgetHeader
import quest.srihari.medxelite.ui.qbank.compactCount
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import quest.srihari.medxelite.Medx
import quest.srihari.medxelite.data.BatchTest
import quest.srihari.medxelite.data.Catalog
import quest.srihari.medxelite.data.Load
import quest.srihari.medxelite.data.RunnerPayload
import quest.srihari.medxelite.data.SeriesGroup
import quest.srihari.medxelite.data.SeriesPaper
import quest.srihari.medxelite.data.SeriesRules
import quest.srihari.medxelite.data.SittingAttempt
import quest.srihari.medxelite.data.SittingMode
import quest.srihari.medxelite.ui.Navigator
import quest.srihari.medxelite.ui.components.EmptyState
import quest.srihari.medxelite.ui.components.GroupedSection
import quest.srihari.medxelite.ui.components.LoadingState
import quest.srihari.medxelite.ui.components.MedxRow
import quest.srihari.medxelite.ui.components.MedxTopBar
import quest.srihari.medxelite.ui.qbank.StartSessionSheet
import quest.srihari.medxelite.ui.theme.Hues
import quest.srihari.medxelite.ui.theme.Mx
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The Marrow FMGE test series, three groups bucketed by month (TestsListView.swift). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestsScreen(nav: Navigator, modifier: Modifier = Modifier) {
    val series by Catalog.series.collectAsState()
    val attempts by Medx.attempts.attempts.collectAsState()
    var group by rememberSaveable { mutableStateOf(SeriesGroup.GRAND) }
    var pick by remember { mutableStateOf<SeriesPaper?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    LaunchedEffect(Unit) { Catalog.loadSeries() }
    val bySource = remember(attempts) { attempts.filter { it.kind == "series" || it.kind == "test" }.groupBy { it.sourceId } }

    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        containerColor = Mx.colors.groupedBackground,
        topBar = {
            MedxTopBar("Tests", large = true, scrollBehavior = scroll, actions = {
                IconButton(onClick = { nav.go("batch") }) { Icon(Icons.Outlined.FolderOpen, "Batch papers") }
            })
        },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; Catalog.loadSeries(true); refreshing = false } },
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) {
            when (val s = series) {
                is Load.Loading -> LoadingState("Loading the series")
                is Load.Failed -> EmptyState("Couldn't load the series", s.message, actionTitle = "Try again") { scope.launch { Catalog.loadSeries(true) } }
                is Load.Ready -> {
                    val papers = s.value.filter { it.group == group }
                    val months = papers.sortedByDescending { it.startAt }.groupBy { p ->
                        p.startInstant?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("MMMM yyyy")) ?: "Undated"
                    }
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        item {
                            val done = s.value.count { bySource.containsKey(it.id) }
                            NWidget(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
                                WidgetHeader("Marrow FMGE test series")
                                Spacer(Modifier.height(14.dp))
                                Row(Modifier.fillMaxWidth()) {
                                    DotStat("${s.value.size}", "papers", Modifier.weight(1f))
                                    DotStat(compactCount(s.value.sumOf { it.questions }), "questions", Modifier.weight(1.2f))
                                    DotStat("$done", "attempted", Modifier.weight(1f), color = if (done > 0) Mx.colors.accent else Mx.colors.ink)
                                }
                            }
                        }
                        item {
                            NSegmented(
                                SeriesGroup.entries, group, { g -> "${g.label} ${s.value.count { it.group == g }}" }, { group = it },
                                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        if (papers.isEmpty()) item { EmptyState("Nothing here yet", "No papers in this group.") }
                        months.forEach { (label, list) ->
                            item(key = "${group.raw}-$label") {
                                GroupedSection(label) {
                                    list.forEachIndexed { i, p ->
                                        val best = bySource[p.id]?.maxByOrNull { it.score }
                                        MedxRow(
                                            title = p.title,
                                            subtitle = p.line + (best?.let { " · best ${it.score}/${it.total}" } ?: ""),
                                            icon = when (p.group) {
                                                SeriesGroup.GRAND -> Icons.Outlined.EmojiEvents
                                                SeriesGroup.MINI -> Icons.Outlined.Bolt
                                                SeriesGroup.SUBJECT -> Icons.Outlined.MenuBook
                                            },
                                            iconTint = when (p.group) {
                                                SeriesGroup.GRAND -> Hues.orange
                                                SeriesGroup.MINI -> Hues.purple
                                                SeriesGroup.SUBJECT -> Hues.forName(p.title)
                                            },
                                            divider = i < list.size - 1,
                                        ) { pick = p }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pick?.let { p ->
        StartSessionSheet(
            title = p.title, subtitle = p.group.label, questionCount = p.questions,
            history = bySource[p.id].orEmpty(), examBlurb = SeriesRules.examBlurb(p),
            onDismiss = { pick = null },
        ) { mode ->
            pick = null
            nav.startSitting(
                RunnerPayload(
                    kind = "series", id = p.id, name = p.title, subject = p.title, mode = mode, gradable = true,
                    sections = if (mode == SittingMode.EXAM) SeriesRules.sections(p) else null,
                    examSeconds = if (p.durationMin > 0) p.durationMin * 60 else null,
                ),
            )
        }
    }
}

/** ARISE batch papers from `medx_tests` (BatchPapersView.swift). */
@Composable
fun BatchPapersScreen(nav: Navigator) {
    val tests by Catalog.tests.collectAsState()
    val attempts by Medx.attempts.attempts.collectAsState()
    var pick by remember { mutableStateOf<BatchTest?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { Catalog.loadTests() }
    val bySource = remember(attempts) { attempts.filter { it.kind == "test" }.groupBy { it.sourceId } }

    Scaffold(containerColor = Mx.colors.groupedBackground, topBar = { MedxTopBar("Batch papers", onBack = nav::back) }) { pad ->
        Box(Modifier.padding(pad)) {
            when (val s = tests) {
                is Load.Loading -> LoadingState("Loading papers")
                is Load.Failed -> EmptyState("Couldn't load papers", s.message, actionTitle = "Try again") { scope.launch { Catalog.loadTests(true) } }
                is Load.Ready -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    val groups = s.value.groupBy { it.batch?.ifBlank { null } ?: "Papers" }
                    groups.forEach { (batch, list) ->
                        item(key = batch) {
                            GroupedSection(batch) {
                                list.forEachIndexed { i, t ->
                                    val best: SittingAttempt? = bySource[t.testId]?.maxByOrNull { it.score }
                                    MedxRow(
                                        title = t.name,
                                        subtitle = "${t.questionCount} q · ${t.officialTimeMins} min" +
                                            (if (!t.gradable) " · no answer key" else "") +
                                            (best?.let { " · best ${it.score}/${it.total}" } ?: ""),
                                        icon = Icons.Outlined.Description,
                                        iconTint = if (t.gradable) Hues.blue else Hues.gray,
                                        divider = i < list.size - 1,
                                    ) { pick = t }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    pick?.let { t ->
        StartSessionSheet(
            title = t.name, subtitle = t.subject.ifBlank { t.batch ?: "Batch paper" }, questionCount = t.questionCount,
            history = bySource[t.testId].orEmpty(),
            examBlurb = "One ${t.officialTimeMins}-minute timer for the paper. Answers after you submit.",
            onDismiss = { pick = null },
        ) { mode ->
            pick = null
            nav.startSitting(
                RunnerPayload(
                    kind = "test", id = t.testId, name = t.name, subject = t.subject, mode = mode,
                    gradable = t.gradable, examSeconds = t.officialTimeMins * 60,
                ),
            )
        }
    }
}
