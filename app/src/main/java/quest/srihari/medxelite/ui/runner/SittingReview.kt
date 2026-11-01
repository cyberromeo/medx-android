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
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(name, style = MaterialTheme.typography.titleMedium, color = Mx.colors.secondaryText, maxLines = 2)
                    Spacer(Modifier.height(8.dp))
                    if (gradable) {
                        Text("$score / $total", fontSize = 48.sp, fontWeight = FontWeight.Black)
                        Text("$verdict · $pct%", style = MaterialTheme.typography.titleMedium, color = Mx.colors.accent)
                    } else {
                        Text("$attempted / $total", fontSize = 48.sp, fontWeight = FontWeight.Black)
                        Text("answered · this paper has no answer key yet", color = Mx.colors.secondaryText)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Stat("$attempted", "attempted")
                        if (gradable) Stat(if (attempted > 0) "${Math.round(score * 100.0 / attempted)}%" else "–", "accuracy")
                        Stat(formatDuration(elapsedSeconds), "time")
                    }
                }
            }
            if (sections.size > 1) {
                item {
                    GroupedSection("Blocks", "Each one was timed on its own and submitted for good.") {
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
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(filter == ReviewFilter.ALL, { filter = ReviewFilter.ALL }, { Text("All ($total)") })
                    if (gradable) FilterChip(filter == ReviewFilter.WRONG, { filter = ReviewFilter.WRONG }, { Text("Wrong ($wrongCount)") })
                    FilterChip(filter == ReviewFilter.SKIPPED, { filter = ReviewFilter.SKIPPED }, { Text("Skipped ($skippedCount)") })
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
                        .clip(RoundedCornerShape(14.dp))
                        .background(Mx.colors.card)
                        .clickable { expanded = if (open) null else q.id }
                        .padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (icon, tint) = when {
                            r?.chosenId == null -> Icons.Filled.Remove to Mx.colors.tertiaryText
                            !gradable -> Icons.Filled.Check to Mx.colors.accent
                            r.correct -> Icons.Filled.Check to Mx.colors.correct
                            else -> Icons.Filled.Close to Mx.colors.wrong
                        }
                        Box(Modifier.size(26.dp).clip(CircleShape).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("Q${index + 1}", fontWeight = FontWeight.Bold)
                        val tag = tags?.getOrNull(index)
                        if (!tag.isNullOrEmpty()) Text("  $tag", style = MaterialTheme.typography.labelSmall, color = Mx.colors.secondaryText)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { Medx.activity.toggleBookmark(q, sourceId, name, subject) }, modifier = Modifier.size(32.dp)) {
                            Icon(if (marked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, "Bookmark", tint = if (marked) Mx.colors.flagged else Mx.colors.secondaryText)
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
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("Done") }
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
