package quest.srihari.medxelite.ui.library

import quest.srihari.medxelite.ui.components.softShadow
import quest.srihari.medxelite.ui.components.tappable
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
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
        Feature("Flashcards", "Image decks by subject", Icons.Filled.Style, Hues.purple, "flashcards"),
        Feature("Search", "Find any module", Icons.Filled.Search, Hues.blue, "search"),
        Feature("Custom modules", if (custom.isEmpty()) "Build your own paper" else "${custom.size} built", Icons.Filled.Tune, Hues.indigo, "custom"),
        Feature("Bookmarks", if (bookmarks.isEmpty()) "None saved yet" else "${bookmarks.size} saved", Icons.Filled.Bookmark, Hues.orange, "bookmarks"),
        Feature("Batch papers", "ARISE tests", Icons.Filled.Description, Hues.teal, "batch"),
        Feature("VOD feed", "Newest recordings", Icons.Filled.VideoLibrary, Hues.pink, "vod"),
        Feature("Faceoff", "Duel a friend", Icons.Filled.Bolt, Hues.yellow, "faceoff"),
        Feature("Watch history", "${history.size} class${if (history.size == 1) "" else "es"}", Icons.Filled.History, Hues.green, "history"),
        Feature("Activity log", "${attempts.size} sitting${if (attempts.size == 1) "" else "s"}", Icons.Filled.ListAlt, Hues.gray, "activity"),
    )
    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        containerColor = Mx.colors.groupedBackground,
        topBar = { MedxTopBar("Library", large = true, scrollBehavior = scroll) },
    ) { pad ->
        LazyVerticalGrid(
            GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(pad),
        ) {
            items(features) { f ->
                Column(
                    Modifier
                        .softShadow(RoundedCornerShape(24.dp), 8.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Mx.colors.card)
                        .tappable { nav.go(f.route) }
                        .padding(18.dp),
                ) {
                    IconTile(f.icon, f.hue, 48.dp)
                    Spacer(Modifier.height(20.dp))
                    Text(f.title, style = MaterialTheme.typography.titleMedium, color = Mx.colors.ink, maxLines = 2)
                    Spacer(Modifier.height(2.dp))
                    Text(f.subtitle, style = MaterialTheme.typography.bodySmall, color = Mx.colors.secondaryText, maxLines = 2)
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
                    IconButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.Delete, "Clear all") }
                }
            })
        },
    ) { pad ->
        if (bookmarks.isEmpty()) {
            Box(Modifier.padding(pad)) { EmptyState("No bookmarks yet", "Tap the bookmark in any sitting to keep a question here.", icon = Icons.Filled.Bookmark) }
            return@Scaffold
        }
        val bySubject = bookmarks.groupBy { it.subject.ifBlank { "Other" } }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
            bySubject.forEach { (subject, list) ->
                item(key = subject) {
                    GroupedSection(subject) {
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
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(b.sourceName)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { Medx.activity.remove(b); open = null }) { Text("Remove", color = Mx.colors.wrong) }
                }
                Spacer(Modifier.height(8.dp))
                HtmlText(b.question.displayText, sizeSp = 17f, bold = true, extraImages = b.question.images.orEmpty(), lineSpacing = 1.4f)
                Spacer(Modifier.height(16.dp))
                b.question.options.forEachIndexed { i, o ->
                    OptionButton(o, i, chosen = false, correct = b.question.isCorrect(o.id), revealed = true, locked = true, nested = true) {}
                    Spacer(Modifier.height(10.dp))
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
                if (history.isNotEmpty()) IconButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.Delete, "Clear history") }
            })
        },
    ) { pad ->
        if (history.isEmpty()) {
            Box(Modifier.padding(pad)) { EmptyState("Nothing watched yet", "Classes you play show up here with where you left off.", icon = Icons.Filled.History) }
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
            Box(Modifier.padding(pad)) { EmptyState("No sittings yet", "Finished QBank modules, tests and duels are listed here.", icon = Icons.Filled.ListAlt) }
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
                    GroupedSection(day) {
                        list.forEachIndexed { i, a ->
                            val time = a.finishedInstant?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("h:mm a")) ?: ""
                            MedxRow(
                                title = a.name,
                                subtitle = "${AttemptKind.label(a.kind)} · ${if (a.mode == "exam") "Exam" else "Revision"} · $time" +
                                    if (Medx.attempts.isPending(a)) " · waiting to sync" else "",
                                icon = when (a.kind) {
                                    "qbank" -> Icons.Filled.LibraryBooks
                                    "series" -> Icons.Filled.EmojiEvents
                                    "duel" -> Icons.Filled.Bolt
                                    "custom" -> Icons.Filled.Tune
                                    else -> Icons.Filled.Description
                                },
                                iconTint = Hues.forName(a.kind),
                                trailing = if (a.gradable == false) "${a.attempted}/${a.total}" else "${a.score}/${a.total}",
                                showChevron = false,
                                divider = i < list.size - 1,
                                trailingContent = {
                                    IconButton(onClick = { deleting = a }) {
                                        Icon(Icons.Filled.Delete, "Delete", tint = Mx.colors.tertiaryText, modifier = Modifier.size(18.dp))
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
