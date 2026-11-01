package quest.srihari.medxelite.ui.home

import quest.srihari.medxelite.ui.components.AiCoreOrb
import quest.srihari.medxelite.ui.components.GlowRing
import quest.srihari.medxelite.ui.components.GradientBar
import quest.srihari.medxelite.ui.components.GradientText
import quest.srihari.medxelite.ui.components.IconChip
import quest.srihari.medxelite.ui.components.NeuralField
import quest.srihari.medxelite.ui.components.glass
import quest.srihari.medxelite.ui.components.softGlow
import quest.srihari.medxelite.ui.components.titleBrush
import quest.srihari.medxelite.ui.components.rememberMotionClock
import quest.srihari.medxelite.ui.theme.StatStyle
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.TrackChanges
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
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        PulseDot()
                        Spacer(Modifier.width(8.dp))
                        Text(
                            LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d")).uppercase(),
                            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.2.sp),
                            color = Mx.colors.secondaryText,
                        )
                    }
                }
                item { Countdown(examName, examDate) { editExam = true } }
                item {
                    GoalCard(stats.answeredToday, goal, stats.streakDays, stats.correctToday)
                }
                if (resume != null) {
                    item {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Card(onClick = { nav.play(resume.video) }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(48.dp).softGlow(Mx.colors.glowStart, Mx.colors.glowEnd, 0.5f).clip(CircleShape).background(Mx.colors.fillBrush),
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Rounded.Videocam, null, tint = Mx.colors.accent, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("CONTINUE WATCHING", style = MaterialTheme.typography.labelSmall, color = Mx.colors.secondaryText)
                                        }
                                        Text(resume.video.displayTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${resume.video.subject} · resume at ${resume.formattedResumeTime}", style = MaterialTheme.typography.bodySmall, color = Mx.colors.secondaryText, maxLines = 1)
                                    }
                                }
                                Spacer(Modifier.height(14.dp))
                                GradientBar(resume.progress.toFloat(), Modifier.fillMaxWidth(), height = 5.dp)
                            }
                        }
                    }
                }
                if (stats.due.isNotEmpty()) {
                    item {
                        GroupedSection("Due for revision", "Spaced at 1, 3, 7, 21 and 45 days after each sitting.", icon = Icons.Rounded.Autorenew) {
                            stats.due.take(5).forEachIndexed { i, d ->
                                MedxRow(
                                    d.name, "${d.subject} · ${d.sittings} sitting${if (d.sittings == 1) "" else "s"}",
                                    icon = Icons.Rounded.Refresh, iconTint = Hues.orange,
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
                    GroupedSection("Shortcuts", icon = Icons.Rounded.Bolt) {
                        MedxRow("Syllabus checklist", "Tick off videos, QBank and revision per subject", icon = Icons.Rounded.Checklist, iconTint = Hues.green, divider = true) { nav.go("tracker") }
                        MedxRow("Study in QBank", "${stats.sittings} sittings so far", icon = Icons.Rounded.School, iconTint = Hues.blue, divider = false) { onTab(Tab.QBANK) }
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
    val c = Mx.colors
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(28.dp), fill = c.card, glow = true, onClick = onEdit),
    ) {
        NeuralField(Modifier.matchParentSize(), nodes = 30, seed = 11, intensity = 0.8f)
        // Aurora wash inside the hero.
        Box(
            Modifier.matchParentSize().background(
                Brush.linearGradient(listOf(c.glowStart.copy(alpha = 0.22f), Color.Transparent, c.glowEnd.copy(alpha = 0.16f))),
            ),
        )
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            GlowRing(runUp, Modifier.size(150.dp), stroke = 8.dp, orbit = true, ticks = 52) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GradientText(
                        "$days",
                        StatStyle.copy(fontSize = if (days >= 100) 40.sp else 50.sp, letterSpacing = (-2).sp, fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                    )
                    Text(if (days == 1L) "DAY" else "DAYS", style = MaterialTheme.typography.labelMedium, color = c.secondaryText)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconChip(Icons.Rounded.School, c.glowStart, 28.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(name.uppercase(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp), maxLines = 1)
                }
                Spacer(Modifier.height(10.dp))
                Text("Countdown to\nexam day", style = MaterialTheme.typography.titleLarge.copy(brush = titleBrush(), lineHeight = 26.sp))
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = if (c.isDark) 0.08f else 0.6f))
                        .border(1.dp, c.hairline, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Event, null, tint = c.accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(date.format(DateTimeFormatter.ofPattern("d MMM yyyy")), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.2.sp))
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val weeks = days / 7
                    Text(if (weeks == 1L) "1 week left" else "$weeks weeks left", style = MaterialTheme.typography.labelLarge, color = c.secondaryText, modifier = Modifier.weight(1f))
                    Text("Change", style = MaterialTheme.typography.labelLarge, color = c.accent)
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.accent, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun GoalCard(answered: Int, goal: Int, streak: Int, correct: Int) {
    val met = answered >= goal
    val fraction = minOf(answered.toFloat() / maxOf(goal, 1), 1f)
    val c = Mx.colors
    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AiCoreOrb(fraction, met, Modifier.size(116.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$answered", style = StatStyle.copy(fontSize = 26.sp), color = Color.White)
                        Text("of $goal", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (met) Icons.Rounded.Verified else Icons.Rounded.AutoAwesome, null, tint = if (met) c.correct else c.accent, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(if (met) "GOAL MET" else "TODAY'S GOAL", style = MaterialTheme.typography.labelMedium, color = if (met) c.correct else c.secondaryText)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            met -> "You've answered $answered questions today."
                            answered == 0 -> "$goal questions today keeps the streak alive."
                            else -> "${goal - answered} more to reach today's $goal."
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 23.sp),
                    )
                    Spacer(Modifier.height(8.dp))
                    GradientBar(fraction, Modifier.fillMaxWidth(), height = 5.dp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("$streak", "day streak", Icons.Rounded.LocalFireDepartment, Hues.orange, Modifier.weight(1f))
                StatTile("$correct", "correct today", Icons.Rounded.CheckCircle, c.correct, Modifier.weight(1f))
                StatTile(if (answered > 0) "${Math.round(correct * 100.0 / answered)}%" else "–", "accuracy", Icons.Rounded.TrackChanges, Hues.cyan, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun StatTile(value: String, label: String, icon: ImageVector, hue: Color, modifier: Modifier = Modifier) {
    val c = Mx.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(hue.copy(alpha = if (c.isDark) 0.14f else 0.12f), hue.copy(alpha = 0.03f))))
            .border(1.dp, Brush.linearGradient(listOf(hue.copy(alpha = 0.5f), hue.copy(alpha = 0.08f))), shape)
            .padding(12.dp),
    ) {
        IconChip(icon, hue, 30.dp)
        Spacer(Modifier.height(8.dp))
        Text(value, style = StatStyle.copy(fontSize = 22.sp), maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.secondaryText, maxLines = 1)
    }
}

@Composable
private fun WeekCard(buckets: List<Int>, sittings: Int, accuracy: Int?) {
    val max = maxOf(buckets.maxOrNull() ?: 0, 1)
    val today = LocalDate.now()
    val c = Mx.colors
    GroupedSection("This week", icon = Icons.Rounded.Insights) {
        Column(Modifier.padding(16.dp)) {
            if (buckets.all { it == 0 }) {
                Text("Nothing logged this week yet. One module is enough to start the streak.", color = c.secondaryText)
                Spacer(Modifier.height(12.dp))
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    GradientText("${buckets.sum()}", StatStyle.copy(fontSize = 28.sp), maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    Text("questions · $sittings sittings${accuracy?.let { " · $it% acc." } ?: ""}", style = MaterialTheme.typography.bodyMedium, color = c.secondaryText, modifier = Modifier.padding(bottom = 4.dp))
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                buckets.forEachIndexed { i, v ->
                    val day = today.minusDays((6 - i).toLong())
                    val isToday = i == 6
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        if (v > 0) Text("$v", style = StatStyle.copy(fontSize = 11.sp), color = if (isToday) c.text else c.secondaryText)
                        Spacer(Modifier.height(2.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height((76f * v / max).coerceAtLeast(5f).dp)
                                .then(if (isToday && v > 0) Modifier.softGlow(c.glowStart, c.glowEnd, 0.45f) else Modifier)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (v > 0) Brush.verticalGradient(listOf(c.glowEnd.copy(alpha = if (isToday) 1f else 0.6f), c.glowStart.copy(alpha = if (isToday) 1f else 0.5f)))
                                    else Brush.verticalGradient(listOf(c.separator, c.separator)),
                                ),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(day.dayOfWeek.name.take(1), style = MaterialTheme.typography.labelSmall, color = if (isToday) c.accent else c.secondaryText, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccuracyCard(points: List<Int>, overall: Int?) {
    val c = Mx.colors
    GroupedSection("Accuracy · last ${points.size} sittings", overall?.let { "Overall accuracy: $it%" }, icon = Icons.Rounded.ShowChart) {
        Canvas(Modifier.fillMaxWidth().height(130.dp).padding(horizontal = 18.dp, vertical = 18.dp)) {
            val step = size.width / (points.size - 1).coerceAtLeast(1)
            val pts = points.mapIndexed { i, p -> Offset(i * step, size.height * (1f - p / 100f)) }
            // Guide lines.
            listOf(0.25f, 0.5f, 0.75f).forEach { g ->
                drawLine(c.separator, Offset(0f, size.height * g), Offset(size.width, size.height * g), 1.dp.toPx())
            }
            val line = androidx.compose.ui.graphics.Path().apply { pts.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
            val area = androidx.compose.ui.graphics.Path().apply {
                addPath(line); lineTo(pts.last().x, size.height); lineTo(pts.first().x, size.height); close()
            }
            drawPath(area, Brush.verticalGradient(listOf(c.glowStart.copy(alpha = 0.35f), Color.Transparent)))
            val brush = Brush.horizontalGradient(listOf(c.glowStart, c.glowMid, c.glowEnd))
            drawPath(line, brush, alpha = 0.3f, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            drawPath(line, brush, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            pts.forEach {
                drawCircle(c.base, 4.5.dp.toPx(), it)
                drawCircle(Color.White, 2.5.dp.toPx(), it)
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

/** Small live indicator: a cyan dot with a breathing halo. */
@Composable
private fun PulseDot() {
    val time = rememberMotionClock()
    val c = Mx.colors
    Canvas(Modifier.size(12.dp)) {
        val p = 0.5f + 0.5f * kotlin.math.sin(time.value * 2.4f)
        drawCircle(c.glowEnd.copy(alpha = 0.25f + 0.25f * p), size.minDimension / 2 * (0.7f + 0.3f * p))
        drawCircle(c.glowEnd, size.minDimension / 4.2f)
    }
}
