package quest.srihari.medxelite.ui.runner

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
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TrackChanges
import quest.srihari.medxelite.ui.components.Card
import quest.srihari.medxelite.ui.components.ChoiceChip
import quest.srihari.medxelite.ui.components.MedxRow
import quest.srihari.medxelite.ui.components.Pill
import quest.srihari.medxelite.ui.components.PrimaryButton
import quest.srihari.medxelite.ui.components.ProgressRing
import quest.srihari.medxelite.ui.components.RowShape
import quest.srihari.medxelite.ui.components.SectionHeader
import quest.srihari.medxelite.ui.components.Space
import quest.srihari.medxelite.ui.components.StatTile
import quest.srihari.medxelite.ui.components.softShadow
import quest.srihari.medxelite.ui.theme.Hues
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

    val c = Mx.colors
    val scoreColor = when {
        !gradable -> c.accent
        pct >= 70 -> c.correct
        pct >= 50 -> c.accent
        else -> c.flagged
    }
    Scaffold(
        containerColor = c.groupedBackground,
        topBar = {
            MedxTopBar("Results", actions = {
                TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp)) { Text("Done", style = MaterialTheme.typography.labelLarge, color = c.accent) }
            })
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Box(Modifier.padding(horizontal = Space.margin, vertical = 8.dp)) {
                    Card(padding = PaddingValues(horizontal = 20.dp, vertical = 28.dp)) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            val fraction = if (gradable) score.toFloat() / maxOf(total, 1) else attempted.toFloat() / maxOf(total, 1)
                            ProgressRing(fraction, 168.dp, 14.dp, scoreColor, c.sunken) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        if (gradable) "$pct%" else "$attempted",
                                        style = MaterialTheme.typography.displayMedium, color = c.ink,
                                    )
                                    Text(if (gradable) "$score of $total" else "of $total answered", style = MaterialTheme.typography.labelLarge, color = c.secondaryText)
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                            Text(if (gradable) verdict else "Paper submitted", style = MaterialTheme.typography.headlineSmall, color = c.ink, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (gradable) name else "This paper has no answer key yet · $name",
                                style = MaterialTheme.typography.bodyMedium, color = c.secondaryText, textAlign = TextAlign.Center, maxLines = 2,
                            )
                        }
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = Space.margin, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("$attempted", "Attempted", Icons.Rounded.EditNote, Hues.blue, Modifier.weight(1f))
                    if (gradable) StatTile(if (attempted > 0) "${Math.round(score * 100.0 / attempted)}%" else "–", "Accuracy", Icons.Rounded.TrackChanges, Hues.green, Modifier.weight(1f))
                    StatTile(formatDuration(elapsedSeconds), "Time taken", Icons.Rounded.Timer, Hues.purple, Modifier.weight(1f))
                }
            }
            if (sections.size > 1) {
                item {
                    GroupedSection("Blocks", "Each one was timed on its own and submitted for good.") {
                        val sorted = sections.sortedBy { it.index }
                        sorted.forEachIndexed { i, b ->
                            MedxRow(
                                b.label, "${b.attempted} attempted · ${formatDuration(b.seconds)}",
                                trailingContent = { Pill("${b.score}/${b.total}") }, divider = i < sorted.size - 1,
                            )
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(start = Space.margin, end = Space.margin, top = 18.dp, bottom = 4.dp)) {
                    SectionHeader("Review answers")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip("All ($total)", filter == ReviewFilter.ALL, { filter = ReviewFilter.ALL })
                        if (gradable) ChoiceChip("Wrong ($wrongCount)", filter == ReviewFilter.WRONG, { filter = ReviewFilter.WRONG })
                        ChoiceChip("Skipped ($skippedCount)", filter == ReviewFilter.SKIPPED, { filter = ReviewFilter.SKIPPED })
                    }
                }
            }
            itemsIndexed(shown, key = { _, v -> v.value.id }) { _, (index, q) ->
                val r = responses[q.id]
                val open = expanded == q.id
                val marked = bookmarks.any { it.question.id == q.id && it.sourceId == sourceId }
                Column(
                    Modifier
                        .padding(horizontal = Space.margin, vertical = 6.dp)
                        .fillMaxWidth()
                        .softShadow(RowShape, 6.dp)
                        .clip(RowShape)
                        .background(c.card)
                        .clickable { expanded = if (open) null else q.id }
                        .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (icon, hue) = when {
                            r?.chosenId == null -> Icons.Rounded.Remove to c.tertiaryText
                            !gradable -> Icons.Rounded.Check to c.accent
                            r.correct -> Icons.Rounded.Check to c.correct
                            else -> Icons.Rounded.Close to c.wrong
                        }
                        Box(Modifier.size(28.dp).clip(CircleShape).background(hue.copy(alpha = if (c.isDark) 0.22f else 0.13f)), contentAlignment = Alignment.Center) {
                            Icon(icon, null, tint = hue, modifier = Modifier.size(17.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("Q${index + 1}", style = MaterialTheme.typography.titleMedium, color = c.ink)
                        val tag = tags?.getOrNull(index)
                        if (!tag.isNullOrEmpty()) Text("  ·  $tag", style = MaterialTheme.typography.labelMedium, color = c.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { Medx.activity.toggleBookmark(q, sourceId, name, subject) }) {
                            Icon(if (marked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, if (marked) "Remove bookmark" else "Bookmark", tint = if (marked) c.flagged else c.secondaryText)
                        }
                    }
                    Column(Modifier.padding(end = 12.dp)) {
                        if (!open) {
                            Text(RichHtml.plain(q.displayText), maxLines = 2, overflow = TextOverflow.Ellipsis, color = c.secondaryText, style = MaterialTheme.typography.bodyMedium)
                        }
                        AnimatedVisibility(open) {
                            Column {
                                Spacer(Modifier.height(4.dp))
                                HtmlText(q.displayText, sizeSp = 17f, bold = true, extraImages = q.images.orEmpty(), lineSpacing = 1.4f)
                                Spacer(Modifier.height(14.dp))
                                q.options.forEachIndexed { i, o ->
                                    OptionButton(o, i, chosen = r?.chosenId == o.id, correct = q.isCorrect(o.id), revealed = gradable, locked = true, nested = true) {}
                                    Spacer(Modifier.height(10.dp))
                                }
                                Explanation(q)
                            }
                        }
                    }
                }
            }
            item {
                PrimaryButton("Done", onDone, modifier = Modifier.fillMaxWidth().padding(horizontal = Space.margin, vertical = 20.dp))
            }
        }
    }
}
