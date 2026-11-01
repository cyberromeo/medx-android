package quest.srihari.medxelite.ui.runner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import quest.srihari.medxelite.ui.components.DotDivider
import quest.srihari.medxelite.ui.components.DotStat
import quest.srihari.medxelite.ui.components.DotText
import quest.srihari.medxelite.ui.components.DotValue
import quest.srihari.medxelite.ui.components.NChip
import quest.srihari.medxelite.ui.components.NLabel
import quest.srihari.medxelite.ui.components.NPill
import quest.srihari.medxelite.ui.components.NWidget
import quest.srihari.medxelite.ui.components.WidgetHeader
import quest.srihari.medxelite.ui.components.WidgetShape
import quest.srihari.medxelite.ui.components.rememberReveal
import quest.srihari.medxelite.ui.theme.NType
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Remove
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
            MedxTopBar("Results", actions = {
                TextButton(onClick = onDone, modifier = Modifier.padding(end = 4.dp)) { Text("Done", fontWeight = FontWeight.SemiBold) }
            })
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                NWidget(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
                    WidgetHeader(if (gradable) "Score" else "Answered", trailing = { NChip(subject.ifBlank { "Sitting" }) })
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        if (gradable) {
                            DotText("$pct", size = 120.sp)
                            DotText("%", size = 120.sp, color = Mx.colors.accent)
                        } else {
                            DotValue("$attempted", "/ $total", size = 120.sp)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (gradable) "$verdict · $score of $total correct" else "This paper has no answer key yet",
                        style = MaterialTheme.typography.bodyMedium, color = Mx.colors.secondaryText,
                    )
                    Spacer(Modifier.height(16.dp))
                    ResultDots(questions, responses, gradable)
                    Spacer(Modifier.height(16.dp))
                    DotDivider()
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth()) {
                        DotStat("$attempted", "attempted", Modifier.weight(1f), size = 34.sp)
                        if (gradable) DotStat(if (attempted > 0) "${Math.round(score * 100.0 / attempted)}%" else "--", "accuracy", Modifier.weight(1f), size = 34.sp)
                        DotStat(formatDuration(elapsedSeconds), "time", Modifier.weight(1f), size = 34.sp)
                    }
                }
            }
            if (sections.size > 1) {
                item {
                    GroupedSection("Blocks", "Each one was timed on its own and submitted for good.") {
                        sections.sortedBy { it.index }.forEach { b ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(b.label, fontWeight = FontWeight.SemiBold)
                                    Text("${b.attempted} attempted · ${formatDuration(b.seconds)}", style = MaterialTheme.typography.bodySmall, color = Mx.colors.secondaryText)
                                }
                                DotValue("${b.score}", "/${b.total}", size = 28.sp)
                            }
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterPill("All ($total)", filter == ReviewFilter.ALL) { filter = ReviewFilter.ALL }
                    if (gradable) FilterPill("Wrong ($wrongCount)", filter == ReviewFilter.WRONG) { filter = ReviewFilter.WRONG }
                    FilterPill("Skipped ($skippedCount)", filter == ReviewFilter.SKIPPED) { filter = ReviewFilter.SKIPPED }
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
                        .clip(WidgetShape)
                        .background(Mx.colors.card)
                        .border(1.dp, Mx.colors.hairline, WidgetShape)
                        .clickable { expanded = if (open) null else q.id }
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val c = Mx.colors
                        val (icon, bg, fg) = when {
                            r?.chosenId == null -> Triple(Icons.Outlined.Remove, Color.Transparent, c.secondaryText)
                            !gradable -> Triple(Icons.Outlined.Check, Color.Transparent, c.ink)
                            r.correct -> Triple(Icons.Outlined.Check, c.ink, c.onInk)
                            else -> Triple(Icons.Outlined.Close, c.wrong, Color.White)
                        }
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(bg).border(1.dp, if (bg == Color.Transparent) c.separator else bg, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("Q${index + 1}", style = TextStyle(fontFamily = NType.mono, fontWeight = FontWeight.Bold, fontSize = 15.sp))
                        val tag = tags?.getOrNull(index)
                        if (!tag.isNullOrEmpty()) NLabel("  $tag", Modifier.weight(1f, fill = false), size = 10.sp)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { Medx.activity.toggleBookmark(q, sourceId, name, subject) }) {
                            Icon(if (marked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, "Bookmark", tint = if (marked) Mx.colors.accent else Mx.colors.secondaryText)
                        }
                    }
                    if (!open) {
                        Text(
                            RichHtml.plain(q.displayText), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            color = Mx.colors.secondaryText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    AnimatedVisibility(open) {
                        Column(Modifier.padding(end = 8.dp)) {
                            Spacer(Modifier.height(4.dp))
                            HtmlText(q.displayText, sizeSp = 16f, bold = true, extraImages = q.images.orEmpty())
                            Spacer(Modifier.height(14.dp))
                            q.options.forEachIndexed { i, o ->
                                OptionButton(o, i, chosen = r?.chosenId == o.id, correct = q.isCorrect(o.id), revealed = gradable, locked = true) {}
                                Spacer(Modifier.height(8.dp))
                            }
                            Spacer(Modifier.height(4.dp))
                            Explanation(q)
                        }
                    }
                }
            }
            item {
                NPill("Done", onDone, Modifier.fillMaxWidth().padding(16.dp))
            }
        }
    }
}

@Composable
private fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = Mx.colors
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .background(if (selected) c.ink else Color.Transparent)
            .border(1.dp, if (selected) c.ink else c.separator, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) c.onInk else c.ink, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** Every question as a dot: ink = right, red = wrong, ring = skipped. */
@Composable
private fun ResultDots(questions: List<Question>, responses: Map<Int, QuestionResponse>, gradable: Boolean) {
    val c = Mx.colors
    val reveal = rememberReveal(questions.size)
    val perRow = 20
    val rows = (questions.size + perRow - 1) / perRow
    Canvas(Modifier.fillMaxWidth().height((rows * 14).dp)) {
        val step = size.width / perRow
        val r = minOf(4.5.dp.toPx(), step * 0.36f)
        val shown = (questions.size * reveal).toInt()
        questions.forEachIndexed { i, q ->
            val cx = step * (i % perRow) + step / 2
            val cy = 14.dp.toPx() * (i / perRow) + 7.dp.toPx()
            val resp = responses[q.id]
            val o = androidx.compose.ui.geometry.Offset(cx, cy)
            when {
                i >= shown -> drawCircle(c.dotOff, r, o)
                resp?.chosenId == null -> drawCircle(c.secondaryText, r - 0.75.dp.toPx(), o, style = Stroke(1.5.dp.toPx()))
                gradable && !resp.correct -> drawCircle(c.wrong, r, o)
                else -> drawCircle(c.ink, r, o)
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Mx.colors.secondaryText)
    }
}
