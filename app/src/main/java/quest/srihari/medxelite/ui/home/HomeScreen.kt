package quest.srihari.medxelite.ui.home

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import quest.srihari.medxelite.ui.components.DotBars
import quest.srihari.medxelite.ui.components.DotLine
import quest.srihari.medxelite.ui.components.DotProgress
import quest.srihari.medxelite.ui.components.DotRing
import quest.srihari.medxelite.ui.components.DotText
import quest.srihari.medxelite.ui.components.DotValue
import quest.srihari.medxelite.ui.components.IconTile
import quest.srihari.medxelite.ui.components.MedxLogoMark
import quest.srihari.medxelite.ui.components.NChip
import quest.srihari.medxelite.ui.components.NCircleButton
import quest.srihari.medxelite.ui.components.NLabel
import quest.srihari.medxelite.ui.components.NWidget
import quest.srihari.medxelite.ui.components.WidgetHeader
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import quest.srihari.medxelite.Medx
import quest.srihari.medxelite.data.RunnerPayload
import quest.srihari.medxelite.data.SittingMode
import quest.srihari.medxelite.ui.Navigator
import quest.srihari.medxelite.ui.Tab
import quest.srihari.medxelite.ui.components.Avatar
import quest.srihari.medxelite.ui.components.Card
import quest.srihari.medxelite.ui.components.GroupedSection
import quest.srihari.medxelite.ui.components.MedxRow
import quest.srihari.medxelite.ui.components.MedxTopBar
import quest.srihari.medxelite.ui.theme.Hues
import quest.srihari.medxelite.ui.theme.Mx
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: Navigator, modifier: Modifier = Modifier, onTab: (Tab) -> Unit) {
    val session by Medx.auth.session.collectAsState()
    val stats by Medx.attempts.stats.collectAsState()
    val syncing by Medx.attempts.syncing.collectAsState()
    val goal by Medx.prefs.dailyGoal.collectAsState()
    val examName by Medx.prefs.examName.collectAsState()
    val examDate by Medx.prefs.examDate.collectAsState()
    val history by Medx.activity.history.collectAsState()
    val scope = rememberCoroutineScope()
    var editExam by remember { mutableStateOf(false) }

    val hour = LocalTime.now().hour
    val greeting = when {
        hour < 5 -> "Still up"
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    val resume = history.firstOrNull { !it.isCompleted && it.resumePosition > 0 }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        containerColor = Mx.colors.groupedBackground,
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = syncing,
            onRefresh = {
                Medx.cache.markAllStale()
                scope.launch { Medx.attempts.refresh() }
                scope.launch { Medx.activity.sync() }
            },
            modifier = Modifier.padding(pad).fillMaxSize().statusBarsPadding(),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    HomeHeader(greeting, session?.firstName, session) { nav.go("settings") }
                }
                item { Countdown(examName, examDate) { editExam = true } }
                item {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GoalWidget(stats.answeredToday, goal, Modifier.weight(1f).fillMaxHeight())
                        StreakWidget(stats.streakDays, stats.weeklyAnswered, Modifier.weight(1f).fillMaxHeight())
                    }
                }
                item {
                    val answered = stats.answeredToday
                    val correct = stats.correctToday
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NWidget(Modifier.weight(1f).fillMaxHeight()) {
                            NLabel("Correct today")
                            Spacer(Modifier.height(14.dp))
                            Spacer(Modifier.weight(1f))
                            DotText("$correct", size = 64.sp)
                        }
                        NWidget(Modifier.weight(1f).fillMaxHeight()) {
                            NLabel("Accuracy today")
                            Spacer(Modifier.height(14.dp))
                            Spacer(Modifier.weight(1f))
                            DotValue(if (answered > 0) "${Math.round(correct * 100.0 / answered)}" else "--", if (answered > 0) "%" else null, size = 64.sp, suffixColor = Mx.colors.accent)
                        }
                    }
                }
                if (resume != null) {
                    item {
                        NWidget(Modifier.fillMaxWidth(), onClick = { nav.play(resume.video) }) {
                            WidgetHeader("Continue watching")
                            Spacer(Modifier.height(14.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                NCircleButton(Icons.Outlined.PlayArrow, "Resume", { nav.play(resume.video) }, filled = true, size = 52.dp)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(resume.video.displayTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(2.dp))
                                    Text("${resume.video.subject} · resume at ${resume.formattedResumeTime}", style = MaterialTheme.typography.bodySmall, color = Mx.colors.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            DotProgress(resume.progress.toFloat(), dots = 32, dotSize = 5.dp)
                        }
                    }
                }
                if (stats.due.isNotEmpty()) {
                    item {
                        NWidget(Modifier.fillMaxWidth(), padding = PaddingValues(top = 18.dp, bottom = 6.dp)) {
                            WidgetHeader("Due for revision", Modifier.padding(horizontal = 18.dp), trailing = {
                                NLabel("${stats.due.size}", color = Mx.colors.ink)
                            })
                            Spacer(Modifier.height(6.dp))
                            stats.due.take(5).forEachIndexed { i, d ->
                                MedxRow(
                                    d.name, "${d.subject} · ${d.sittings} sitting${if (d.sittings == 1) "" else "s"}",
                                    icon = Icons.Outlined.Refresh, iconTint = Hues.orange,
                                    divider = i < minOf(stats.due.size, 5) - 1,
                                ) {
                                    nav.startSitting(RunnerPayload("qbank", d.moduleId, d.name, d.subject, SittingMode.REVISION))
                                }
                            }
                        }
                        Text(
                            "Spaced at 1, 3, 7, 21 and 45 days after each sitting.",
                            style = MaterialTheme.typography.bodySmall, color = Mx.colors.secondaryText,
                            modifier = Modifier.padding(start = 6.dp, top = 8.dp),
                        )
                    }
                }
                item { WeekCard(stats.weeklyAnswered, stats.weekSittings, stats.weekAccuracy) }
                if (stats.recentAccuracy.size >= 2) {
                    item { AccuracyCard(stats.recentAccuracy, stats.overallAccuracy) }
                }
                item {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        QuickAction("Syllabus checklist", "Tick off videos, QBank and revision", Icons.Outlined.Checklist, Modifier.weight(1f).fillMaxHeight()) { nav.go("tracker") }
                        QuickAction("Study in QBank", "${stats.sittings} sittings so far", Icons.Outlined.School, Modifier.weight(1f).fillMaxHeight()) { onTab(Tab.QBANK) }
                    }
                }
            }
        }
    }

    if (editExam) ExamEditor(examName, examDate) { editExam = false }
}

@Composable
private fun HomeHeader(greeting: String, firstName: String?, session: quest.srihari.medxelite.auth.AuthSession?, onAvatar: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MedxLogoMark(size = 30.dp, framed = false)
            Spacer(Modifier.width(8.dp))
            DotText("MEDX", size = 26.sp)
            Spacer(Modifier.width(6.dp))
            NLabel("Elite", modifier = Modifier.padding(top = 3.dp))
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onAvatar).semantics { contentDescription = "Settings" },
                contentAlignment = Alignment.Center,
            ) { Avatar(session, 40.dp) }
        }
        Spacer(Modifier.height(18.dp))
        NLabel(LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, d MMM")))
        Spacer(Modifier.height(4.dp))
        Text(
            if (firstName.isNullOrBlank()) greeting else "$greeting,\n$firstName",
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium),
            color = Mx.colors.ink,
        )
    }
}

@Composable
private fun Countdown(name: String, date: LocalDate, onEdit: () -> Unit) {
    val days = maxOf(ChronoUnit.DAYS.between(LocalDate.now(), date), 0)
    val runUp = 1f - minOf(days, 365).toFloat() / 365f
    NWidget(Modifier.fillMaxWidth(), onClick = onEdit) {
        Box(Modifier.fillMaxWidth()) {
            Column {
                WidgetHeader("$name countdown", trailing = {
                    NChip(date.format(DateTimeFormatter.ofPattern("d MMM yyyy")))
                })
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    DotText("$days", size = 104.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.padding(bottom = 0.dp)) {
                        NLabel(if (days == 1L) "day" else "days", color = Mx.colors.ink, size = 13.sp)
                        NLabel("to go", size = 13.sp)
                    }
                }
                Spacer(Modifier.height(22.dp))
                DotProgress(runUp, dots = 30, dotSize = 6.dp)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val weeks = days / 7
                    NLabel(if (weeks == 1L) "1 week left" else "$weeks weeks left", Modifier.weight(1f), color = Mx.colors.ink)
                    NLabel("Change")
                }
            }
        }
    }
}

@Composable
private fun GoalWidget(answered: Int, goal: Int, modifier: Modifier) {
    val met = answered >= goal
    val fraction = minOf(answered.toFloat() / maxOf(goal, 1), 1f)
    NWidget(modifier) {
        NLabel(if (met) "Goal met" else "Today's goal", color = if (met) Mx.colors.ink else Mx.colors.secondaryText)
        Spacer(Modifier.height(12.dp))
        DotRing(fraction, Modifier.size(124.dp).align(Alignment.CenterHorizontally), dots = 32, dotSize = 5.5.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DotText("$answered", size = 42.sp)
                Spacer(Modifier.height(4.dp))
                NLabel("of $goal", size = 10.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            when {
                met -> "$answered answered today."
                answered == 0 -> "$goal today keeps the streak alive."
                else -> "${goal - answered} more to reach $goal."
            },
            style = MaterialTheme.typography.bodySmall, color = Mx.colors.secondaryText,
        )
    }
}

@Composable
private fun StreakWidget(streak: Int, week: List<Int>, modifier: Modifier) {
    val c = Mx.colors
    NWidget(modifier) {
        NLabel("Streak")
        Spacer(Modifier.weight(1f))
        DotText("$streak", size = 72.sp)
        NLabel(if (streak == 1) "day in a row" else "days in a row", size = 10.sp)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(14.dp))
        val today = LocalDate.now()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            week.takeLast(7).forEachIndexed { i, v ->
                val day = today.minusDays((6 - i).toLong())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(9.dp).clip(CircleShape).background(
                            when {
                                v > 0 && i == 6 -> c.accent
                                v > 0 -> c.ink
                                else -> c.dotOff
                            },
                        ),
                    )
                    Spacer(Modifier.height(5.dp))
                    NLabel(day.dayOfWeek.name.take(1), size = 9.sp, color = c.tertiaryText)
                }
            }
        }
    }
}

@Composable
private fun QuickAction(title: String, subtitle: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    NWidget(modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, Mx.colors.ink, 40.dp)
            Spacer(Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = Mx.colors.tertiaryText, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Mx.colors.secondaryText, maxLines = 2)
    }
}

@Composable
private fun WeekCard(buckets: List<Int>, sittings: Int, accuracy: Int?) {
    val today = LocalDate.now()
    NWidget(Modifier.fillMaxWidth()) {
        WidgetHeader("This week", trailing = {
            if (buckets.any { it > 0 }) NLabel("${buckets.sum()} q", color = Mx.colors.ink)
        })
        Spacer(Modifier.height(8.dp))
        Text(
            if (buckets.all { it == 0 }) "Nothing logged this week yet. One module is enough to start the streak."
            else "$sittings sittings${accuracy?.let { " · $it% accuracy" } ?: ""}",
            style = MaterialTheme.typography.bodyMedium, color = Mx.colors.secondaryText,
        )
        Spacer(Modifier.height(16.dp))
        DotBars(buckets, Modifier.fillMaxWidth().height(120.dp), rows = 9)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            buckets.forEachIndexed { i, v ->
                val day = today.minusDays((6 - i).toLong())
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    NLabel(day.dayOfWeek.name.take(1), size = 10.sp, color = if (i == 6) Mx.colors.ink else Mx.colors.tertiaryText)
                    NLabel(if (v > 0) "$v" else "·", size = 9.sp, color = Mx.colors.tertiaryText)
                }
            }
        }
    }
}

@Composable
private fun AccuracyCard(points: List<Int>, overall: Int?) {
    NWidget(Modifier.fillMaxWidth()) {
        WidgetHeader("Accuracy · last ${points.size} sittings")
        Spacer(Modifier.height(10.dp))
        if (overall != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                DotValue("$overall", "%", size = 56.sp, suffixColor = Mx.colors.accent)
                Spacer(Modifier.width(10.dp))
                NLabel("overall", Modifier.padding(bottom = 6.dp))
            }
            Spacer(Modifier.height(12.dp))
        }
        DotLine(points, Modifier.fillMaxWidth().height(96.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamEditor(name: String, date: LocalDate, onDismiss: () -> Unit) {
    var newName by remember { mutableStateOf(name) }
    var picking by remember { mutableStateOf(false) }
    val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    if (picking) {
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        Medx.prefs.setExam(newName.ifBlank { "FMGE" }, Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    picking = false
                    onDismiss()
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your exam") },
        text = {
            Column {
                OutlinedTextField(newName, { newName = it }, label = { Text("Exam name") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Text("Date: ${date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}", color = Mx.colors.secondaryText)
            }
        },
        confirmButton = { TextButton(onClick = { picking = true }) { Text("Pick date") } },
        dismissButton = {
            TextButton(onClick = {
                Medx.prefs.setExam(newName.ifBlank { "FMGE" }, date)
                onDismiss()
            }) { Text("Save name") }
        },
    )
}
