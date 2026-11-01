package quest.srihari.medxelite.ui.runner

import androidx.compose.material.icons.rounded.ViewAgenda
import quest.srihari.medxelite.ui.components.GradientButton
import quest.srihari.medxelite.ui.components.GlowChip
import quest.srihari.medxelite.ui.components.GlowRing
import quest.srihari.medxelite.ui.components.GradientText
import quest.srihari.medxelite.ui.components.IconChip
import quest.srihari.medxelite.ui.components.NeuralField
import quest.srihari.medxelite.ui.components.SectionHeader
import quest.srihari.medxelite.ui.components.subjectIcon
import quest.srihari.medxelite.ui.components.glass
import quest.srihari.medxelite.ui.components.softGlow
import quest.srihari.medxelite.ui.theme.StatStyle
import quest.srihari.medxelite.ui.theme.Aurora
import quest.srihari.medxelite.ui.theme.Hues
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.border
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.LaunchedEffect
import kotlin.math.roundToInt
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Timer
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import quest.srihari.medxelite.Medx
import quest.srihari.medxelite.data.AttemptSection
import quest.srihari.medxelite.data.Question
import quest.srihari.medxelite.data.QuestionResponse
import quest.srihari.medxelite.ui.components.GroupedSection
import quest.srihari.medxelite.ui.components.HtmlText
import quest.srihari.medxelite.ui.components.MedxTopBar
import quest.srihari.medxelite.ui.components.RichHtml
import quest.srihari.medxelite.ui.components.formatDuration
import quest.srihari.medxelite.ui.theme.Mx

private enum class ReviewFilter { ALL, WRONG, SKIPPED }

/** Results + per-question review after a sitting (SittingReviewView.swift). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SittingReview(
    name: String,
    subject: String,
    sourceId: String,
    questions: List<Question>,
    responses: Map<Int, QuestionResponse>,
    gradable: Boolean,
    elapsedSeconds: Int,
    sections: List<AttemptSection>,
    tags: List<String>?,
    onDone: () -> Unit,
) {
    BackHandler { onDone() }
    val bookmarks by Medx.activity.bookmarks.collectAsState()
    var filter by remember { mutableStateOf(ReviewFilter.ALL) }
    var expanded by remember { mutableStateOf<Int?>(null) }
    val score = responses.values.count { it.correct }
    val attempted = responses.values.count { it.chosenId != null }
    val total = questions.size
    val pct = if (total > 0) Math.round(score * 100.0 / total).toInt() else 0
    val wrongCount = questions.count { q -> responses[q.id]?.let { it.chosenId != null && !it.correct } == true }
    val skippedCount = questions.count { q -> responses[q.id]?.chosenId == null }
    val verdict = when {
        pct >= 85 -> "Excellent"
        pct >= 70 -> "Solid effort"
        pct >= 50 -> "Getting there"
        else -> "Keep practising"
    }
    val shown = questions.withIndex().filter { (_, q) ->
        val r = responses[q.id]
        when (filter) {
            ReviewFilter.ALL -> true
            ReviewFilter.WRONG -> r != null && r.chosenId != null && !r.correct
            ReviewFilter.SKIPPED -> r?.chosenId == null
        }
    }

    Scaffold(
        containerColor = Mx.colors.groupedBackground,
        topBar = {
            MedxTopBar("Results", actions = { TextButton(onClick = onDone) { Text("Done", fontWeight = FontWeight.SemiBold) } })
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                ResultHero(
                    name = name, subject = subject, gradable = gradable, score = score, attempted = attempted, total = total,
                    pct = pct, verdict = verdict, elapsedSeconds = elapsedSeconds,
                    wrong = wrongCount, skipped = skippedCount,
                )
            }
            if (sections.size > 1) {
                item {
                    GroupedSection("Blocks", "Each one was timed on its own and submitted for good.", icon = Icons.Rounded.ViewAgenda) {
                        sections.sortedBy { it.index }.forEach { b ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(b.label, fontWeight = FontWeight.SemiBold)
                                    Text("${b.attempted} attempted · ${formatDuration(b.seconds)}", style = MaterialTheme.typography.bodySmall, color = Mx.colors.secondaryText)
                                }
                                Text("${b.score}/${b.total}", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    SectionHeader("Question review", Icons.Rounded.FactCheck, Modifier.padding(start = 6.dp, bottom = 10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlowChip(filter == ReviewFilter.ALL, { filter = ReviewFilter.ALL }, "All ($total)", Icons.AutoMirrored.Rounded.ViewList)
                        if (gradable) GlowChip(filter == ReviewFilter.WRONG, { filter = ReviewFilter.WRONG }, "Wrong ($wrongCount)", Icons.Rounded.Close)
                        GlowChip(filter == ReviewFilter.SKIPPED, { filter = ReviewFilter.SKIPPED }, "Skipped ($skippedCount)", Icons.Rounded.Remove)
                    }
                }
            }
            itemsIndexed(shown, key = { _, v -> v.value.id }) { _, (index, q) ->
                val r = responses[q.id]
                val open = expanded == q.id
                val marked = bookmarks.any { it.question.id == q.id && it.sourceId == sourceId }
                Column(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(22.dp), pressScale = false, onClick = { expanded = if (open) null else q.id })
                        .padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (icon, tint) = when {
                            r?.chosenId == null -> Icons.Rounded.Remove to Mx.colors.tertiaryText
                            !gradable -> Icons.Rounded.Check to Mx.colors.accent
                            r.correct -> Icons.Rounded.Check to Mx.colors.correct
                            else -> Icons.Rounded.Close to Mx.colors.wrong
                        }
                        Box(
                            Modifier.size(28.dp).softGlow(tint, tint, 0.3f).clip(CircleShape).background(tint.copy(alpha = 0.16f)).border(1.dp, tint.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("Q${index + 1}", style = StatStyle.copy(fontSize = 15.sp))
                        val tag = tags?.getOrNull(index)
                        if (!tag.isNullOrEmpty()) Text("  $tag", style = MaterialTheme.typography.labelSmall, color = Mx.colors.secondaryText)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { Medx.activity.toggleBookmark(q, sourceId, name, subject) }, modifier = Modifier.size(32.dp)) {
                            Icon(if (marked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, "Bookmark", tint = if (marked) Mx.colors.flagged else Mx.colors.secondaryText)
                        }
                    }
                    if (!open) {
                        Text(RichHtml.plain(q.displayText), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = Mx.colors.secondaryText, style = MaterialTheme.typography.bodyMedium)
                    }
                    AnimatedVisibility(open) {
                        Column {
                            Spacer(Modifier.height(6.dp))
                            HtmlText(q.displayText, sizeSp = 16f, bold = true, extraImages = q.images.orEmpty())
                            Spacer(Modifier.height(10.dp))
                            q.options.forEachIndexed { i, o ->
                                OptionButton(o, i, chosen = r?.chosenId == o.id, correct = q.isCorrect(o.id), revealed = gradable, locked = true) {}
                                Spacer(Modifier.height(8.dp))
                            }
                            Explanation(q)
                        }
                    }
                }
            }
            item {
                GradientButton(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("Done") }
            }
        }
    }
}

@Composable
private fun ResultHero(
    name: String, subject: String, gradable: Boolean, score: Int, attempted: Int, total: Int,
    pct: Int, verdict: String, elapsedSeconds: Int, wrong: Int, skipped: Int,
) {
    val c = Mx.colors
    val target = if (gradable) pct / 100f else attempted.toFloat() / maxOf(total, 1)
    val ring = remember { Animatable(0f) }
    val still = Mx.reduceMotion
    LaunchedEffect(target) { if (still) ring.snapTo(target) else ring.animateTo(target, tween(1400, easing = FastOutSlowInEasing)) }
    val shown = (ring.value * 100).roundToInt()
    val colors = when {
        !gradable -> listOf(c.glowStart, c.glowMid, c.glowEnd, c.glowStart)
        pct >= 70 -> listOf(c.correct, Aurora.cyan, c.glowStart, c.correct)
        pct >= 50 -> listOf(c.glowStart, c.glowMid, c.glowEnd, c.glowStart)
        else -> listOf(c.wrong, c.glowMid, c.glowStart, c.wrong)
    }
    Box(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
            .glass(RoundedCornerShape(30.dp), glow = true),
    ) {
        NeuralField(Modifier.matchParentSize(), nodes = 24, seed = 21, intensity = 0.55f)
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconChip(subjectIcon(subject), Hues.forName(subject), 26.dp)
                Spacer(Modifier.width(8.dp))
                Text(subject.uppercase(), style = MaterialTheme.typography.labelMedium, color = c.secondaryText, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
            Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 2, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            GlowRing(ring.value, Modifier.size(196.dp), stroke = 12.dp, colors = colors, ticks = 60, orbit = true) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GradientText("$shown%", StatStyle.copy(fontSize = 46.sp, letterSpacing = (-2).sp), brush = Brush.linearGradient(colors.take(3)), maxLines = 1)
                    Text(
                        if (gradable) "$score / $total correct" else "$attempted / $total answered",
                        style = StatStyle.copy(fontSize = 13.sp), color = c.secondaryText,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(colors[0].copy(alpha = 0.2f), colors[2].copy(alpha = 0.14f))))
                    .border(1.dp, Brush.horizontalGradient(listOf(colors[0].copy(alpha = 0.7f), colors[2].copy(alpha = 0.4f))), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (gradable) Icons.Rounded.AutoAwesome else Icons.Rounded.Info, null, tint = c.text, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (gradable) verdict else "No answer key yet", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ResultStat("$attempted", "attempted", Icons.Rounded.EditNote, Hues.blue, Modifier.weight(1f))
                if (gradable) ResultStat(if (attempted > 0) "${Math.round(score * 100.0 / attempted)}%" else "–", "accuracy", Icons.Rounded.TrackChanges, Hues.cyan, Modifier.weight(1f))
                else ResultStat("$skipped", "skipped", Icons.Rounded.Remove, Hues.gray, Modifier.weight(1f))
                ResultStat(formatDuration(elapsedSeconds), "time", Icons.Rounded.Timer, Hues.purple, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ResultStat(value: String, label: String, icon: ImageVector, hue: Color, modifier: Modifier = Modifier) {
    val c = Mx.colors
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(c.glassStrong.copy(alpha = if (c.isDark) 0.08f else 0.7f))
            .border(1.dp, Brush.linearGradient(listOf(hue.copy(alpha = 0.5f), hue.copy(alpha = 0.06f))), RoundedCornerShape(18.dp))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = hue, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, style = StatStyle.copy(fontSize = 18.sp), maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.secondaryText)
    }
}
