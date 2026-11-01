package quest.srihari.medxelite.ui.home

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TrackChanges
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
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val hour = LocalTime.now().hour
    val greeting = when {
        hour < 5 -> "Still up"
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    val title = session?.let { "$greeting, ${it.firstName}" } ?: greeting
    val resume = history.firstOrNull { !it.isCompleted && it.resumePosition > 0 }

    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        containerColor = Mx.colors.groupedBackground,
        topBar = {
            MedxTopBar(title, large = true, scrollBehavior = scroll, actions = {
                IconButton(onClick = { nav.go("settings") }) { Avatar(session) }
            })
        },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = syncing,
            onRefresh = {
                Medx.cache.markAllStale()
                scope.launch { Medx.attempts.refresh() }
                scope.launch { Medx.activity.sync() }
            },
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Text(
                        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d")).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = Mx.colors.secondaryText,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
                item { Countdown(examName, examDate) { editExam = true } }
                item {
                    GoalCard(stats.answeredToday, goal, stats.streakDays, stats.correctToday)
                }
                if (resume != null) {
                    item {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Card(Modifier.clickable { nav.play(resume.video) }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Mx.colors.accent),
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White) }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("CONTINUE WATCHING", style = MaterialTheme.typography.labelSmall, color = Mx.colors.secondaryText)
                                        Text(resume.video.displayTitle, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                        Text("${resume.video.subject} · resume at ${resume.formattedResumeTime}", style = MaterialTheme.typography.bodySmall, color = Mx.colors.secondaryText)
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                LinearProgressIndicator(progress = { resume.progress.toFloat() }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
                if (stats.due.isNotEmpty()) {
                    item {
                        GroupedSection("Due for revision", "Spaced at 1, 3, 7, 21 and 45 days after each sitting.") {
                            stats.due.take(5).forEachIndexed { i, d ->
                                MedxRow(
                                    d.name, "${d.subject} · ${d.sittings} sitting${if (d.sittings == 1) "" else "s"}",
                                    icon = Icons.Filled.Refresh, iconTint = Hues.orange,
                                    divider = i < minOf(stats.due.size, 5) - 1,
                                ) {
                                    nav.startSitting(RunnerPayload("qbank", d.moduleId, d.name, d.subject, SittingMode.REVISION))
                                }
                            }
                        }
                    }
                }
                item { WeekCard(stats.weeklyAnswered, stats.weekSittings, stats.weekAccuracy) }
                if (stats.recentAccuracy.size >= 2) {
                    item { AccuracyCard(stats.recentAccuracy, stats.overallAccuracy) }
                }
                item {
                    GroupedSection {
                        MedxRow("Syllabus checklist", "Tick off videos, QBank and revision per subject", icon = Icons.Filled.Checklist, iconTint = Hues.green, divider = true) { nav.go("tracker") }
                        MedxRow("Study in QBank", "${stats.sittings} sittings so far", icon = Icons.Filled.School, iconTint = Hues.blue, divider = false) { onTab(Tab.QBANK) }
                    }
                }
            }
        }
    }

    if (editExam) ExamEditor(examName, examDate) { editExam = false }
}

@Composable
private fun Countdown(name: String, date: LocalDate, onEdit: () -> Unit) {
    val days = maxOf(ChronoUnit.DAYS.between(LocalDate.now(), date), 0)
    val runUp = 1f - minOf(days, 365).toFloat() / 365f
    val accent = Mx.colors.accent
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.75f))))
            .clickable(onClick = onEdit)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.School, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(name.uppercase(), color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                date.format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                color = Color.White, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.22f)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$days", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(8.dp))
            Text(if (days == 1L) "day to go" else "days to go", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, modifier = Modifier.padding(bottom = 14.dp))
        }
        LinearProgressIndicator(progress = { runUp }, color = Color.White, trackColor = Color.White.copy(alpha = 0.3f), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row {
            val weeks = days / 7
            Text(if (weeks == 1L) "1 week left" else "$weeks weeks left", color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("Change", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun GoalCard(answered: Int, goal: Int, streak: Int, correct: Int) {
    val met = answered >= goal
    val fraction = minOf(answered.toFloat() / maxOf(goal, 1), 1f)
    val ringColor = if (met) Mx.colors.correct else Mx.colors.accent
    val track = Mx.colors.separator
    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                        drawArc(track, 0f, 360f, false, style = stroke)
                        drawArc(ringColor, -90f, 360f * fraction, false, style = stroke)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$answered", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text("of $goal", style = MaterialTheme.typography.labelSmall, color = Mx.colors.secondaryText)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (met) "GOAL MET" else "TODAY'S GOAL", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (met) Mx.colors.correct else Mx.colors.secondaryText)
                    Text(
                        when {
                            met -> "You've answered $answered questions today."
                            answered == 0 -> "$goal questions today keeps the streak alive."
                            else -> "${goal - answered} more to reach today's $goal."
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("$streak", "day streak", Icons.Filled.LocalFireDepartment, Hues.orange, Modifier.weight(1f))
                StatTile("$correct", "correct today", Icons.Filled.CheckCircle, Mx.colors.correct, Modifier.weight(1f))
                StatTile(if (answered > 0) "${Math.round(correct * 100.0 / answered)}%" else "–", "accuracy today", Icons.Filled.TrackChanges, Mx.colors.accent, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun StatTile(value: String, label: String, icon: ImageVector, hue: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(hue.copy(alpha = 0.12f)).padding(10.dp),
    ) {
        Icon(icon, null, tint = hue, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Mx.colors.secondaryText, maxLines = 1)
    }
}

@Composable
private fun WeekCard(buckets: List<Int>, sittings: Int, accuracy: Int?) {
    val max = maxOf(buckets.maxOrNull() ?: 0, 1)
    val today = LocalDate.now()
    GroupedSection("This week") {
        Column(Modifier.padding(16.dp)) {
            if (buckets.all { it == 0 }) {
                Text("Nothing logged this week yet. One module is enough to start the streak.", color = Mx.colors.secondaryText)
                Spacer(Modifier.height(12.dp))
            } else {
                Text("${buckets.sum()} questions · $sittings sittings${accuracy?.let { " · $it% accuracy" } ?: ""}", style = MaterialTheme.typography.bodyMedium, color = Mx.colors.secondaryText)
                Spacer(Modifier.height(12.dp))
            }
            Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                buckets.forEachIndexed { i, v ->
                    val day = today.minusDays((6 - i).toLong())
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        if (v > 0) Text("$v", style = MaterialTheme.typography.labelSmall, color = Mx.colors.secondaryText)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height((70f * v / max).coerceAtLeast(4f).dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (i == 6) Mx.colors.accent else Mx.colors.accent.copy(alpha = if (v > 0) 0.45f else 0.15f)),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(day.dayOfWeek.name.take(1), style = MaterialTheme.typography.labelSmall, color = Mx.colors.secondaryText)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccuracyCard(points: List<Int>, overall: Int?) {
    val accent = Mx.colors.accent
    GroupedSection("Accuracy over the last ${points.size} sittings", overall?.let { "Overall accuracy: $it%" }) {
        Canvas(Modifier.fillMaxWidth().height(120.dp).padding(16.dp)) {
            val step = size.width / (points.size - 1).coerceAtLeast(1)
            val pts = points.mapIndexed { i, p -> Offset(i * step, size.height * (1f - p / 100f)) }
            for (i in 0 until pts.size - 1) drawLine(accent, pts[i], pts[i + 1], strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            pts.forEach { drawCircle(accent, 4.dp.toPx(), it) }
        }
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
