package quest.srihari.medxelite.ui.library

import androidx.compose.material.icons.rounded.CalendarMonth
import quest.srihari.medxelite.ui.components.subjectIcon
import quest.srihari.medxelite.ui.components.glass
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.ListAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import quest.srihari.medxelite.data.AttemptKind
import quest.srihari.medxelite.data.BookmarkedQuestion
import quest.srihari.medxelite.data.MedxDates
import quest.srihari.medxelite.data.RunnerPayload
import quest.srihari.medxelite.data.SittingAttempt
import quest.srihari.medxelite.data.SittingMode
import quest.srihari.medxelite.ui.Navigator
import quest.srihari.medxelite.ui.components.EmptyState
import quest.srihari.medxelite.ui.components.GroupedSection
import quest.srihari.medxelite.ui.components.HtmlText
import quest.srihari.medxelite.ui.components.IconTile
import quest.srihari.medxelite.ui.components.MedxRow
import quest.srihari.medxelite.ui.components.MedxTopBar
import quest.srihari.medxelite.ui.components.Pill
import quest.srihari.medxelite.ui.runner.Explanation
import quest.srihari.medxelite.ui.runner.OptionButton
import quest.srihari.medxelite.ui.theme.Hues
import quest.srihari.medxelite.ui.theme.Mx
import quest.srihari.medxelite.ui.videos.VideoRow
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class Feature(val title: String, val subtitle: String, val icon: ImageVector, val hue: Color, val route: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(nav: Navigator, modifier: Modifier = Modifier) {
    val bookmarks by Medx.activity.bookmarks.collectAsState()
    val history by Medx.activity.history.collectAsState()
    val attempts by Medx.attempts.attempts.collectAsState()
    val custom by Medx.custom.modules.collectAsState()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val features = listOf(
        Feature("Flashcards", "Image decks by subject", Icons.Rounded.Style, Hues.purple, "flashcards"),
        Feature("Search", "Find any module", Icons.Rounded.Search, Hues.blue, "search"),
        Feature("Custom modules", "${custom.size} built", Icons.Rounded.Tune, Hues.indigo, "custom"),
        Feature("Bookmarks", "${bookmarks.size} saved", Icons.Rounded.Bookmark, Hues.orange, "bookmarks"),
        Feature("Batch papers", "ARISE tests", Icons.Rounded.Description, Hues.teal, "batch"),
        Feature("VOD feed", "Newest recordings", Icons.Rounded.VideoLibrary, Hues.pink, "vod"),
        Feature("Faceoff", "Duel a friend", Icons.Rounded.Bolt, Hues.yellow, "faceoff"),
        Feature("Watch history", "${history.size} classes", Icons.Rounded.History, Hues.green, "history"),
        Feature("Activity log", "${attempts.size} sittings", Icons.Rounded.ListAlt, Hues.gray, "activity"),
    )
    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        containerColor = Mx.colors.groupedBackground,
        topBar = { MedxTopBar("Library", large = true, scrollBehavior = scroll) },
    ) { pad ->
        LazyVerticalGrid(
            GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(pad),
        ) {
            items(features) { f ->
                Column(
                    Modifier.glass(RoundedCornerShape(22.dp), onClick = { nav.go(f.route) }).padding(14.dp),
                ) {
                    IconTile(f.icon, f.hue, 36.dp)
                    Spacer(Modifier.height(18.dp))
                    Text(f.title, fontWeight = FontWeight.SemiBold)
                    Text(f.subtitle, style = MaterialTheme.typography.bodySmall, color = Mx.colors.secondaryText, maxLines = 1)
                }
            }
        }
    }
}

fun relative(stamp: String?): String {
    val t = MedxDates.parse(stamp) ?: return ""
    val d = Duration.between(t, Instant.now())
    return when {
        d.toMinutes() < 1 -> "just now"
        d.toHours() < 1 -> "${d.toMinutes()} min ago"
        d.toDays() < 1 -> "${d.toHours()} hr ago"
        d.toDays() < 7 -> "${d.toDays()} d ago"
        else -> t.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(nav: Navigator) {
    val bookmarks by Medx.activity.bookmarks.collectAsState()
    var open by remember { mutableStateOf<BookmarkedQuestion?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { Medx.activity.sync() }
    Scaffold(
        containerColor = Mx.colors.groupedBackground,
        topBar = {
            MedxTopBar("Bookmarks", onBack = nav::back, actions = {
                if (bookmarks.isNotEmpty()) {
                    TextButton(onClick = {
                        val qs = bookmarks.map { it.question }
                        nav.startSitting(RunnerPayload("custom", "bookmarks", "Bookmarked questions", "Bookmarks", SittingMode.REVISION, questions = qs, questionTags = bookmarks.map { it.sourceName }))
                    }) { Text("Practise") }
                    IconButton(onClick = { confirmClear = true }) { Icon(Icons.Rounded.Delete, "Clear all") }
                }
            })
        },
    ) { pad ->
        if (bookmarks.isEmpty()) {
            Box(Modifier.padding(pad)) { EmptyState("No bookmarks yet", "Tap the bookmark in any sitting to keep a question here.", icon = Icons.Rounded.Bookmark) }
            return@Scaffold
        }
        val bySubject = bookmarks.groupBy { it.subject.ifBlank { "Other" } }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
            bySubject.forEach { (subject, list) ->
                item(key = subject) {
                    GroupedSection(subject, icon = subjectIcon(subject)) {
                        list.forEachIndexed { i, b ->
                            MedxRow(b.previewText, "${b.sourceName} · ${relative(b.bookmarkedAt)}", divider = i < list.size - 1) { open = b }
                        }
                    }
                }
            }
        }
    }
    open?.let { b ->
        ModalBottomSheet(onDismissRequest = { open = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(b.sourceName)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { Medx.activity.remove(b); open = null }) { Text("Remove", color = Mx.colors.wrong) }
                }
                HtmlText(b.question.displayText, sizeSp = 17f, bold = true, extraImages = b.question.images.orEmpty())
                Spacer(Modifier.height(12.dp))
                b.question.options.forEachIndexed { i, o ->
                    OptionButton(o, i, chosen = false, correct = b.question.isCorrect(o.id), revealed = true, locked = true) {}
                    Spacer(Modifier.height(8.dp))
                }
                Explanation(b.question)
                Spacer(Modifier.height(32.dp))
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all bookmarks?") },
            text = { Text("This removes them from every device signed in to your account.") },
            confirmButton = { TextButton(onClick = { Medx.activity.clearBookmarks(); confirmClear = false }) { Text("Clear", color = Mx.colors.wrong) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun WatchHistoryScreen(nav: Navigator) {
    val history by Medx.activity.history.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { Medx.activity.sync() }
    Scaffold(
        containerColor = Mx.colors.groupedBackground,
        topBar = {
            MedxTopBar("Watch history", onBack = nav::back, actions = {
                if (history.isNotEmpty()) IconButton(onClick = { confirmClear = true }) { Icon(Icons.Rounded.Delete, "Clear history") }
            })
        },
    ) { pad ->
        if (history.isEmpty()) {
            Box(Modifier.padding(pad)) { EmptyState("Nothing watched yet", "Classes you play show up here with where you left off.", icon = Icons.Rounded.History) }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                GroupedSection(footer = "Tap a class to resume where you left off.") {
                    history.forEachIndexed { i, h ->
                        VideoRow(h.video.copy(faculty = "${h.video.subject} · ${relative(h.watchedAt)}"), h.progress, h.isCompleted, i < history.size - 1) { nav.play(h.video) }
                    }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear watch history?") },
            confirmButton = { TextButton(onClick = { Medx.activity.clearHistory(); confirmClear = false }) { Text("Clear", color = Mx.colors.wrong) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

/** Every sitting this account has filed, newest first (ActivityLogView.swift). */
@Composable
fun ActivityLogScreen(nav: Navigator) {
    val attempts by Medx.attempts.attempts.collectAsState()
    val stats by Medx.attempts.stats.collectAsState()
    var deleting by remember { mutableStateOf<SittingAttempt?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { Medx.attempts.refresh() }
    Scaffold(containerColor = Mx.colors.groupedBackground, topBar = { MedxTopBar("Activity log", onBack = nav::back) }) { pad ->
        if (attempts.isEmpty()) {
            Box(Modifier.padding(pad)) { EmptyState("No sittings yet", "Finished QBank modules, tests and duels are listed here.", icon = Icons.Rounded.ListAlt) }
            return@Scaffold
        }
        val byDay = attempts.groupBy { a ->
            a.finishedInstant?.atZone(ZoneId.systemDefault())?.toLocalDate()?.format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy")) ?: "Earlier"
        }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text(
                    "${stats.sittings} sittings · ${stats.overallAccuracy?.let { "$it% accuracy" } ?: "no answers yet"} · ${stats.streakDays}-day streak",
                    color = Mx.colors.secondaryText, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            byDay.forEach { (day, list) ->
                item(key = day) {
                    GroupedSection(day, icon = Icons.Rounded.CalendarMonth) {
                        list.forEachIndexed { i, a ->
                            val time = a.finishedInstant?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("h:mm a")) ?: ""
                            MedxRow(
                                title = a.name,
                                subtitle = "${AttemptKind.label(a.kind)} · ${if (a.mode == "exam") "Exam" else "Revision"} · $time" +
                                    if (Medx.attempts.isPending(a)) " · waiting to sync" else "",
                                icon = when (a.kind) {
                                    "qbank" -> Icons.Rounded.LibraryBooks
                                    "series" -> Icons.Rounded.EmojiEvents
                                    "duel" -> Icons.Rounded.Bolt
                                    "custom" -> Icons.Rounded.Tune
                                    else -> Icons.Rounded.Description
                                },
                                iconTint = Hues.forName(a.kind),
                                trailing = if (a.gradable == false) "${a.attempted}/${a.total}" else "${a.score}/${a.total}",
                                showChevron = false,
                                divider = i < list.size - 1,
                                trailingContent = {
                                    IconButton(onClick = { deleting = a }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Rounded.Delete, "Delete", tint = Mx.colors.tertiaryText, modifier = Modifier.size(18.dp))
                                    }
                                },
                            ) {
                                if (a.kind == "qbank" || a.kind == "test" || a.kind == "series") {
                                    nav.startSitting(RunnerPayload(a.kind, a.sourceId, a.name, a.subject ?: "", SittingMode.REVISION))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    deleting?.let { a ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this sitting?") },
            text = { Text("${a.name} — ${a.score}/${a.total}. It stops counting towards your stats.") },
            confirmButton = { TextButton(onClick = { Medx.attempts.delete(a); deleting = null }) { Text("Delete", color = Mx.colors.wrong) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}
