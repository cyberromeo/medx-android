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
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import quest.srihari.medxelite.ui.components.IconTile
import quest.srihari.medxelite.ui.components.Pill
import quest.srihari.medxelite.ui.components.PrimaryButton
import quest.srihari.medxelite.ui.components.ProgressBar
import quest.srihari.medxelite.ui.components.ProgressRing
import quest.srihari.medxelite.ui.components.SectionHeader
import quest.srihari.medxelite.ui.components.Space
import quest.srihari.medxelite.ui.components.StatTile
import quest.srihari.medxelite.ui.components.SubjectIcon
import quest.srihari.medxelite.ui.components.softShadow
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
    val c = Mx.colors

    val hour = LocalTime.now().hour
    val greeting = when {
        hour < 5 -> "Still up"
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    val resume = history.firstOrNull { !it.isCompleted && it.resumePosition > 0 }

    Box(modifier.fillMaxSize().background(c.groupedBackground)) {
        PullToRefreshBox(
            isRefreshing = syncing,
            onRefresh = {
                Medx.cache.markAllStale()
                scope.launch { Medx.attempts.refresh() }
                scope.launch { Medx.activity.sync() }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    Row(
                        Modifier.statusBarsPadding().padding(start = Space.margin, end = 12.dp, top = 16.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                                style = MaterialTheme.typography.labelLarge, color = c.secondaryText,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                session?.let { "$greeting, ${it.firstName}" } ?: greeting,
                                style = MaterialTheme.typography.headlineMedium, color = c.ink,
                            )
                        }
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button) { nav.go("settings") },
                            contentAlignment = Alignment.Center,
                        ) { Avatar(session, 42.dp) }
                    }
                }
                item { TodayHero(stats.answeredToday, goal, stats.streakDays) { onTab(Tab.QBANK) } }
                item {
                    Row(
                        Modifier.padding(horizontal = Space.margin, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val acc = if (stats.answeredToday > 0) "${Math.round(stats.correctToday * 100.0 / stats.answeredToday)}%" else "–"
                        StatTile("${stats.correctToday}", "Correct", Icons.Rounded.CheckCircle, Hues.green, Modifier.weight(1f))
                        StatTile(acc, "Accuracy", Icons.Rounded.TrackChanges, Hues.blue, Modifier.weight(1f))
                        StatTile("${stats.weekSittings}", "Week sittings", Icons.Rounded.AutoGraph, Hues.purple, Modifier.weight(1f))
                    }
                }
                item { Countdown(examName, examDate) { editExam = true } }
                if (resume != null) {
                    item {
                        Box(Modifier.padding(horizontal = Space.margin, vertical = 10.dp)) {
                            Card(onClick = { nav.play(resume.video) }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(c.accent),
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(Icons.Rounded.PlayArrow, null, tint = c.onAccent, modifier = Modifier.size(28.dp)) }
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Continue watching", style = MaterialTheme.typography.labelMedium, color = c.accent)
                                        Text(resume.video.displayTitle, style = MaterialTheme.typography.titleMedium, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text("${resume.video.subject} · resume at ${resume.formattedResumeTime}", style = MaterialTheme.typography.bodySmall, color = c.secondaryText)
                                    }
                                }
                                Spacer(Modifier.height(14.dp))
                                ProgressBar(resume.progress.toFloat(), height = 6.dp)
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
                                    leading = { SubjectIcon(d.subject, 40.dp) },
                                    divider = i < minOf(stats.due.size, 5) - 1,
                                    trailingContent = { Pill("Revise", Hues.orange) },
                                    showChevron = false,
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
                    GroupedSection("Shortcuts") {
                        MedxRow("Syllabus checklist", "Tick off videos, QBank and revision per subject", icon = Icons.Rounded.Checklist, iconTint = Hues.green, divider = true) { nav.go("tracker") }
                        MedxRow("Study in QBank", "${stats.sittings} sittings so far", icon = Icons.Rounded.School, iconTint = Hues.blue, divider = false) { onTab(Tab.QBANK) }
                    }
                }
            }
        }
    }

    if (editExam) ExamEditor(examName, examDate) { editExam = false }
}

/** The golden path: today's goal ring, streak, and one clear action. */
@Composable
private fun TodayHero(answered: Int, goal: Int, streak: Int, onPractise: () -> Unit) {
    val c = Mx.colors
    val met = answered >= goal
    val fraction = minOf(answered.toFloat() / maxOf(goal, 1), 1f)
    val bg = if (c.isDark) c.accentContainer else c.accent
    val fg = if (c.isDark) c.onAccentContainer else Color.White
    val fgSoft = fg.copy(alpha = 0.78f)
    val ring = if (c.isDark) c.accent else Color.White
    Box(
        Modifier
            .padding(horizontal = Space.margin, vertical = 8.dp)
            .fillMaxWidth()
            .softShadow(RoundedCornerShape(28.dp), 14.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(bg),
    ) {
        // Quiet depth: two large soft discs, lit from the top-right.
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Color.White.copy(alpha = 0.07f), radius = size.width * 0.42f, center = Offset(size.width * 0.98f, size.height * 0.02f))
            drawCircle(Color.White.copy(alpha = 0.05f), radius = size.width * 0.26f, center = Offset(size.width * 0.86f, size.height * 0.98f))
        }
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(fraction, 104.dp, 10.dp, ring, fg.copy(alpha = 0.22f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$answered", style = MaterialTheme.typography.headlineMedium, color = fg)
                        Text("of $goal", style = MaterialTheme.typography.labelSmall, color = fgSoft)
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (met) "Goal met" else "Today's goal", style = MaterialTheme.typography.labelLarge, color = fgSoft)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        when {
                            met -> "$answered answered. Brilliant work today."
                            answered == 0 -> "$goal questions keeps your streak alive"
                            else -> "${goal - answered} more to reach $goal"
                        },
                        style = MaterialTheme.typography.titleLarge, color = fg,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(fg.copy(alpha = 0.16f)).padding(start = 8.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.LocalFireDepartment, null, tint = if (c.isDark) c.streak else Color(0xFFFFC1A6), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(when (streak) { 0 -> "Start a streak today"; 1 -> "1-day streak"; else -> "$streak-day streak" }, style = MaterialTheme.typography.labelMedium, color = fg)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                if (met) "Keep going" else if (answered == 0) "Start practising" else "Continue practising",
                onClick = onPractise,
                icon = Icons.AutoMirrored.Rounded.ArrowForward,
                container = if (c.isDark) c.accent else Color.White,
                content = if (c.isDark) c.onAccent else c.accent,
                height = 52.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Countdown(name: String, date: LocalDate, onEdit: () -> Unit) {
    val c = Mx.colors
    val days = maxOf(ChronoUnit.DAYS.between(LocalDate.now(), date), 0)
    val runUp = 1f - minOf(days, 365).toFloat() / 365f
    Box(Modifier.padding(horizontal = Space.margin, vertical = 10.dp)) {
        Card(onClick = onEdit) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.Event, Hues.orange, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, color = c.ink)
                    Text(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")), style = MaterialTheme.typography.bodySmall, color = c.secondaryText)
                }
                Text("Edit", style = MaterialTheme.typography.labelLarge, color = c.accent, modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$days", style = MaterialTheme.typography.displayMedium, color = c.ink)
                Spacer(Modifier.width(8.dp))
                Text(if (days == 1L) "day to go" else "days to go", style = MaterialTheme.typography.titleMedium, color = c.secondaryText, modifier = Modifier.padding(bottom = 8.dp))
            }
            Spacer(Modifier.height(12.dp))
            ProgressBar(runUp, color = Hues.orange, track = c.sunken, height = 8.dp)
            Spacer(Modifier.height(8.dp))
            val weeks = days / 7
            Text(if (weeks == 1L) "About 1 week left" else "About $weeks weeks left · keep the daily rhythm", style = MaterialTheme.typography.bodySmall, color = c.secondaryText)
        }
    }
}

@Composable
private fun WeekCard(buckets: List<Int>, sittings: Int, accuracy: Int?) {
    val c = Mx.colors
    val max = maxOf(buckets.maxOrNull() ?: 0, 1)
    val today = LocalDate.now()
    Column(Modifier.padding(horizontal = Space.margin, vertical = 10.dp)) {
        SectionHeader("This week")
        Card {
            if (buckets.all { it == 0 }) {
                Text("Nothing logged yet this week. One module is enough to start the streak.", style = MaterialTheme.typography.bodyMedium, color = c.secondaryText)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${buckets.sum()}", style = MaterialTheme.typography.headlineMedium, color = c.ink)
                    Spacer(Modifier.width(6.dp))
                    Text("questions", style = MaterialTheme.typography.bodyMedium, color = c.secondaryText, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text("$sittings sittings${accuracy?.let { " · $it% accuracy" } ?: ""}", style = MaterialTheme.typography.bodySmall, color = c.secondaryText)
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().height(132.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                buckets.forEachIndexed { i, v ->
                    val day = today.minusDays((6 - i).toLong())
                    val isToday = i == 6
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        if (v > 0) Text("$v", style = MaterialTheme.typography.labelSmall, color = if (isToday) c.accent else c.secondaryText, maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height((84f * v / max).coerceAtLeast(6f).dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isToday) c.accent else if (v > 0) c.accent.copy(alpha = if (c.isDark) 0.35f else 0.28f) else c.sunken),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            day.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall, color = if (isToday) c.ink else c.secondaryText,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccuracyCard(points: List<Int>, overall: Int?) {
    val c = Mx.colors
    val accent = c.accent
    val grid = c.separator
    Column(Modifier.padding(horizontal = Space.margin, vertical = 10.dp)) {
        SectionHeader("Accuracy trend")
        Card {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${points.last()}%", style = MaterialTheme.typography.headlineMedium, color = c.ink)
                Spacer(Modifier.width(8.dp))
                Text("last sitting", style = MaterialTheme.typography.bodyMedium, color = c.secondaryText, modifier = Modifier.padding(bottom = 4.dp))
            }
            Text("${points.size} sittings${overall?.let { " · $it% overall" } ?: ""}", style = MaterialTheme.typography.bodySmall, color = c.secondaryText)
            Spacer(Modifier.height(16.dp))
            Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                val padY = 8.dp.toPx()
                val h = size.height - padY * 2
                listOf(0f, 0.5f, 1f).forEach { g ->
                    val y = padY + h * g
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                val step = size.width / (points.size - 1).coerceAtLeast(1)
                val pts = points.mapIndexed { i, p -> Offset(i * step, padY + h * (1f - p / 100f)) }
                val line = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until pts.size) {
                        val a = pts[i - 1]; val b = pts[i]
                        val mx = (a.x + b.x) / 2
                        cubicTo(mx, a.y, mx, b.y, b.x, b.y)
                    }
                }
                val area = Path().apply {
                    addPath(line)
                    lineTo(pts.last().x, size.height)
                    lineTo(pts.first().x, size.height)
                    close()
                }
                drawPath(area, Brush.verticalGradient(listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0f))))
                drawPath(line, accent, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                drawCircle(c.card, 7.dp.toPx(), pts.last())
                drawCircle(accent, 5.dp.toPx(), pts.last())
            }
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
