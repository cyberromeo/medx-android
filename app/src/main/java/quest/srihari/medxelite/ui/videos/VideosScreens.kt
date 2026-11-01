package quest.srihari.medxelite.ui.videos

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.sp
import quest.srihari.medxelite.ui.components.DotStat
import quest.srihari.medxelite.ui.components.NWidget
import quest.srihari.medxelite.ui.components.WidgetHeader
import quest.srihari.medxelite.ui.components.DotProgress
import quest.srihari.medxelite.ui.components.NSearchField
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.VideoLibrary
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import quest.srihari.medxelite.Medx
import quest.srihari.medxelite.data.Catalog
import quest.srihari.medxelite.data.Load
import quest.srihari.medxelite.data.RecordedVideo
import quest.srihari.medxelite.data.VodItem
import quest.srihari.medxelite.ui.Navigator
import quest.srihari.medxelite.ui.components.EmptyState
import quest.srihari.medxelite.ui.components.GroupedSection
import quest.srihari.medxelite.ui.components.LoadingState
import quest.srihari.medxelite.ui.components.MedxRow
import quest.srihari.medxelite.ui.components.MedxTopBar
import quest.srihari.medxelite.ui.qbank.SubjectBadge
import quest.srihari.medxelite.ui.theme.Hues
import quest.srihari.medxelite.ui.theme.Mx
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun hours(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

/** The Classes tab: every recorded lecture by batch, then subject (VideosBatchListView.swift). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideosScreen(nav: Navigator, modifier: Modifier = Modifier) {
    val videos by Catalog.videos.collectAsState()
    val history by Medx.activity.history.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    LaunchedEffect(Unit) { Catalog.loadVideos() }

    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        containerColor = Mx.colors.groupedBackground,
        topBar = {
            MedxTopBar("Classes", large = true, scrollBehavior = scroll, actions = {
                IconButton(onClick = { nav.go("history") }) { Icon(Icons.Outlined.History, "Watch history") }
                IconButton(onClick = { nav.go("vod") }) { Icon(Icons.Outlined.VideoLibrary, "VOD feed") }
            })
        },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; Catalog.loadVideos(true); refreshing = false } },
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) {
            when (val s = videos) {
                is Load.Loading -> LoadingState("Loading classes")
                is Load.Failed -> EmptyState("Couldn't load classes", s.message, actionTitle = "Try again") { scope.launch { Catalog.loadVideos(true) } }
                is Load.Ready -> {
                    val filtered = s.value.filter {
                        query.isBlank() || it.title.contains(query, true) || it.subject.contains(query, true) ||
                            (it.faculty ?: "").contains(query, true) || (it.batch ?: "").contains(query, true)
                    }
                    // Batch order and subject order as they first appear, like iOS.
                    val batches = LinkedHashMap<String, Pair<String, LinkedHashMap<String, MutableList<RecordedVideo>>>>()
                    filtered.forEach { v ->
                        val bId = v.batchId ?: "default"
                        val entry = batches.getOrPut(bId) { (v.batch ?: "Batch") to LinkedHashMap() }
                        entry.second.getOrPut(v.subjectId ?: v.subject) { mutableListOf() }.add(v)
                    }
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        item {
                            val all = s.value
                            val watched = all.count { v -> history.any { it.video.id == v.id && it.isCompleted } }
                            NWidget(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
                                WidgetHeader("Recorded classes")
                                Spacer(Modifier.height(14.dp))
                                Row(Modifier.fillMaxWidth()) {
                                    DotStat("${all.size}", "classes", Modifier.weight(1f), size = 30.sp)
                                    DotStat("${all.sumOf { it.durationSeconds ?: 0 } / 3600}h", "of video", Modifier.weight(1f), size = 30.sp)
                                    DotStat("$watched", "watched", Modifier.weight(1f), size = 30.sp)
                                }
                            }
                        }
                        item {
                            NSearchField(query, { query = it }, "Search classes, subjects, faculty", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                        if (batches.isEmpty()) item { EmptyState("No classes", if (query.isBlank()) "Nothing has been published yet." else "No class matches “$query”.") }
                        batches.forEach { (bId, pair) ->
                            val (name, subjects) = pair
                            val all = subjects.values.flatten()
                            item(key = bId) {
                                GroupedSection(name, "${all.size} classes · ${hours(all.sumOf { it.durationSeconds ?: 0 })}") {
                                    val list = subjects.entries.toList()
                                    list.forEachIndexed { i, (_, vids) ->
                                        val subject = vids.first().subject
                                        val watched = vids.count { v -> history.any { it.video.id == v.id && it.isCompleted } }
                                        MedxRow(
                                            title = subject,
                                            subtitle = "${vids.size} classes · ${hours(vids.sumOf { it.durationSeconds ?: 0 })}" + if (watched > 0) " · $watched watched" else "",
                                            leading = { SubjectBadge(subject) },
                                            divider = i < list.size - 1,
                                        ) { nav.openVideoGroup(subject, vids) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoSubjectScreen(nav: Navigator, title: String, videos: List<RecordedVideo>) {
    val history by Medx.activity.history.collectAsState()
    Scaffold(containerColor = Mx.colors.groupedBackground, topBar = { MedxTopBar(title, onBack = nav::back) }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                GroupedSection("${videos.size} classes") {
                    videos.forEachIndexed { i, v ->
                        val h = history.firstOrNull { it.video.id == v.id }
                        VideoRow(v, h?.progress, h?.isCompleted == true, i < videos.size - 1) { nav.play(v) }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoRow(v: RecordedVideo, progress: Double?, completed: Boolean, divider: Boolean, onClick: () -> Unit) {
    MedxRow(
        title = v.displayTitle,
        subtitle = listOfNotNull(v.faculty, v.formattedDuration.ifBlank { null }).joinToString(" · "),
        icon = if (completed) Icons.Outlined.CheckCircle else Icons.Outlined.PlayArrow,
        iconTint = if (completed) Hues.green else Mx.colors.accent,
        divider = divider,
        showChevron = false,
        trailingContent = {
            if (progress != null && progress > 0 && !completed) {
                DotProgress(progress.toFloat(), Modifier.width(56.dp), dots = 7, dotSize = 5.dp, animate = false)
            }
        },
        onClick = onClick,
    )
}

/** The VOD bucket, newest first, 48 a page (VodFeedView.swift). */
@Composable
fun VodFeedScreen(nav: Navigator) {
    val items = remember { mutableStateListOf<VodItem>() }
    var cursor by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()

    fun loadMore() {
        if (loading || done) return
        loading = true
        scope.launch {
            try {
                val (page, next) = Medx.fs.fetchVodPage(cursor = cursor)
                items.addAll(page.filter { p -> items.none { it.id == p.id } })
                cursor = next
                done = next == null
                error = null
            } catch (e: Exception) {
                error = "The VOD feed could not be loaded."
            } finally {
                loading = false
            }
        }
    }
    LaunchedEffect(Unit) { loadMore() }
    val nearEnd by remember { derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= items.size - 6 } }
    LaunchedEffect(nearEnd, items.size) { if (nearEnd && items.isNotEmpty()) loadMore() }

    Scaffold(containerColor = Mx.colors.groupedBackground, topBar = { MedxTopBar("VOD feed", onBack = nav::back) }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (items.isEmpty() && loading) LoadingState("Loading recordings")
            else if (items.isEmpty() && error != null) EmptyState("Couldn't load", error, actionTitle = "Try again") { loadMore() }
            else LazyColumn(state = list, contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(items, key = { _, v -> v.id }) { _, v ->
                    MedxRow(
                        title = v.title,
                        subtitle = listOfNotNull(
                            v.subject.ifBlank { null }, v.faculty.ifBlank { null },
                            v.uploadedAt?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                            if (v.durationSeconds > 0) hours(v.durationSeconds) else null,
                        ).joinToString(" · "),
                        leading = {
                            Box(Modifier.size(width = 76.dp, height = 46.dp).clip(RoundedCornerShape(12.dp)).background(Mx.colors.raised), contentAlignment = Alignment.Center) {
                                if (v.thumbnailUrl.isNotBlank()) {
                                    AsyncImage(v.thumbnailUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                } else Icon(Icons.Outlined.PlayArrow, null, tint = Mx.colors.secondaryText)
                            }
                        },
                        showChevron = false,
                    ) { nav.play(v.asRecordedVideo) }
                }
                if (loading) item { Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) { androidx.compose.material3.CircularProgressIndicator() } }
                if (error != null && items.isNotEmpty()) item { TextButton(onClick = { loadMore() }, modifier = Modifier.padding(16.dp)) { Text("Load more") } }
            }
        }
    }
}
