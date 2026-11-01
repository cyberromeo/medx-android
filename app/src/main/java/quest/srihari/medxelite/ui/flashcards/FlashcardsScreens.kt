package quest.srihari.medxelite.ui.flashcards

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import quest.srihari.medxelite.data.Catalog
import quest.srihari.medxelite.data.FlashcardCard
import quest.srihari.medxelite.data.Load
import quest.srihari.medxelite.ui.Navigator
import quest.srihari.medxelite.ui.components.EmptyState
import quest.srihari.medxelite.ui.components.GroupedSection
import quest.srihari.medxelite.ui.components.LoadingState
import quest.srihari.medxelite.ui.components.MedxRow
import quest.srihari.medxelite.ui.components.MedxTopBar
import quest.srihari.medxelite.ui.components.ZoomableImage
import quest.srihari.medxelite.ui.qbank.SubjectBadge
import quest.srihari.medxelite.ui.theme.Mx

@Composable
fun FlashcardsScreen(nav: Navigator) {
    val subjects by Catalog.flashcards.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { Catalog.loadFlashcards() }
    Scaffold(containerColor = Mx.colors.groupedBackground, topBar = { MedxTopBar("Flashcards", onBack = nav::back) }) { pad ->
        Box(Modifier.padding(pad)) {
            when (val s = subjects) {
                is Load.Loading -> LoadingState("Loading flashcards")
                is Load.Failed -> EmptyState("Couldn't load flashcards", s.message, actionTitle = "Try again") { scope.launch { Catalog.loadFlashcards(true) } }
                is Load.Ready -> androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        GroupedSection("${s.value.size} subjects · ${s.value.sumOf { it.cardCount }} cards") {
                            s.value.forEachIndexed { i, sub ->
                                MedxRow(
                                    sub.name, "${sub.cardCount} cards", leading = { SubjectBadge(sub.name) },
                                    divider = i < s.value.size - 1,
                                ) { nav.go("flashcards/${sub.subjectId}") }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A subject's deck: thumbnail grid, then a full-screen pager (FlashcardStudyView + FlashcardDeckView). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FlashcardDeckScreen(nav: Navigator, subjectId: Int) {
    val subjects by Catalog.flashcards.collectAsState()
    LaunchedEffect(Unit) { Catalog.loadFlashcards() }
    val subject = (subjects as? Load.Ready)?.value?.firstOrNull { it.subjectId == subjectId }
    var openAt by remember { mutableStateOf<Int?>(null) }
    val config = LocalConfiguration.current
    val landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val tablet = config.smallestScreenWidthDp >= 600

    Scaffold(containerColor = Mx.colors.groupedBackground, topBar = { MedxTopBar(subject?.name ?: "Flashcards", onBack = nav::back) }) { pad ->
        if (subject == null) {
            Box(Modifier.padding(pad)) { LoadingState() }
            return@Scaffold
        }
        if (subject.cards.isEmpty()) {
            Box(Modifier.padding(pad)) { EmptyState("No cards", "There are no flashcards to display.", icon = Icons.Outlined.Style) }
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(pad),
        ) {
            itemsIndexed(subject.cards, key = { i, c -> "${c.id}-$i" }) { i, card ->
                Column(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(Mx.colors.card).border(1.dp, Mx.colors.hairline, RoundedCornerShape(20.dp)).clickable { openAt = i },
                ) {
                    AsyncImage(
                        card.imageUrl(landscape = false, tablet = tablet), card.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f).background(Mx.colors.raised),
                    )
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
                        Text("%02d".format(i + 1), style = MaterialTheme.typography.labelMedium, color = Mx.colors.accent)
                        Spacer(Modifier.width(8.dp))
                        Text(card.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    val start = openAt
    if (subject != null && start != null) {
        Dialog(onDismissRequest = { openAt = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            DeckPager(subject.cards, start, landscape, tablet) { openAt = null }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeckPager(cards: List<FlashcardCard>, start: Int, landscape: Boolean, tablet: Boolean, onClose: () -> Unit) {
    val pager = rememberPagerState(initialPage = start) { cards.size }
    var chrome by remember { mutableStateOf(true) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            Box(Modifier.fillMaxSize().clickable { chrome = !chrome }, contentAlignment = Alignment.Center) {
                val url = cards[page].imageUrl(landscape, tablet)
                if (url != null) ZoomableImage(url) else Text("No artwork", color = Color.White)
            }
        }
        if (chrome) {
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.6f)).statusBarsPadding().padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF111111)).border(1.dp, Color(0xFF2A2A2A), CircleShape).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Close, "Close", tint = Color.White, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(cards[pager.currentPage].name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    cards[pager.currentPage].chapter?.let { Text(it.uppercase(), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                }
                Text(
                    "${pager.currentPage + 1}/${cards.size}", color = Color.White,
                    style = androidx.compose.ui.text.TextStyle(fontFamily = quest.srihari.medxelite.ui.theme.NType.dot, fontWeight = FontWeight.Black, fontSize = 22.sp),
                )
            }
        }
        Spacer(Modifier.height(0.dp))
    }
}
