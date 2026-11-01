package quest.srihari.medxelite.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import quest.srihari.medxelite.ui.theme.Mx
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import quest.srihari.medxelite.Medx
import quest.srihari.medxelite.data.RecordedVideo
import quest.srihari.medxelite.data.RunnerPayload
import quest.srihari.medxelite.ui.custom.CustomModuleEditor
import quest.srihari.medxelite.ui.custom.CustomModulesScreen
import quest.srihari.medxelite.ui.faceoff.FaceoffScreen
import quest.srihari.medxelite.ui.duel.DuelHubScreen
import quest.srihari.medxelite.ui.duel.DuelMatchScreen
import quest.srihari.medxelite.ui.duel.InvitePrompt
import quest.srihari.medxelite.ui.duel.QuickMatchScreen
import quest.srihari.medxelite.ui.duel.RejoinPill
import quest.srihari.medxelite.duel.DuelCenter
import quest.srihari.medxelite.duel.DuelError
import quest.srihari.medxelite.duel.DuelRepo
import quest.srihari.medxelite.duel.MatchStatus
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.launch
import quest.srihari.medxelite.ui.flashcards.FlashcardDeckScreen
import quest.srihari.medxelite.ui.flashcards.FlashcardsScreen
import quest.srihari.medxelite.ui.home.HomeScreen
import quest.srihari.medxelite.ui.library.ActivityLogScreen
import quest.srihari.medxelite.ui.library.BookmarksScreen
import quest.srihari.medxelite.ui.library.LibraryScreen
import quest.srihari.medxelite.ui.library.WatchHistoryScreen
import quest.srihari.medxelite.ui.qbank.QBankScreen
import quest.srihari.medxelite.ui.qbank.SearchScreen
import quest.srihari.medxelite.ui.qbank.SubjectScreen
import quest.srihari.medxelite.ui.runner.RunnerScreen
import quest.srihari.medxelite.ui.settings.SettingsScreen
import quest.srihari.medxelite.ui.settings.SyllabusTrackerScreen
import quest.srihari.medxelite.ui.tests.BatchPapersScreen
import quest.srihari.medxelite.ui.tests.TestsScreen
import quest.srihari.medxelite.ui.videos.PlayerScreen
import quest.srihari.medxelite.ui.videos.VideoSubjectScreen
import quest.srihari.medxelite.ui.videos.VideosScreen
import quest.srihari.medxelite.ui.videos.VodFeedScreen

enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("Home", Icons.Outlined.Home, Icons.Rounded.Home),
    QBANK("QBank", Icons.AutoMirrored.Outlined.LibraryBooks, Icons.AutoMirrored.Rounded.LibraryBooks),
    TESTS("Tests", Icons.Outlined.Assignment, Icons.Rounded.Assignment),
    CLASSES("Classes", Icons.Outlined.PlayCircle, Icons.Rounded.PlayCircle),
    LIBRARY("Library", Icons.Outlined.GridView, Icons.Rounded.GridView),
}

/** Hand-off for arguments too rich for a route string (AppState.pendingRunnerPayload on iOS). */
object Handoff {
    var payload: RunnerPayload? = null
    var video: RecordedVideo? = null
    var videoGroup: List<RecordedVideo> = emptyList()
    var videoGroupTitle: String = ""
}

class Navigator(val nav: NavHostController) {
    fun back() { nav.popBackStack() }
    fun go(route: String) { nav.navigate(route) { launchSingleTop = true } }

    fun startSitting(payload: RunnerPayload) {
        Handoff.payload = payload
        nav.navigate("runner")
    }

    fun play(video: RecordedVideo) {
        Handoff.video = video
        nav.navigate("player")
    }

    fun openVideoGroup(title: String, videos: List<RecordedVideo>) {
        Handoff.videoGroup = videos
        Handoff.videoGroupTitle = title
        nav.navigate("videos/group")
    }
}

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val navigator = Navigator(nav)
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val reduce = Mx.reduceMotion

    val prompt by DuelCenter.prompt.collectAsState()
    val openRoom by DuelCenter.openRoom.collectAsState()
    val pendingCode by DuelCenter.pendingCode.collectAsState()
    val inMatch by DuelCenter.inMatch.collectAsState()
    val active by DuelCenter.active.collectAsState()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    // An accepted invite / notification tap / deep link opens the room from anywhere.
    LaunchedEffect(openRoom) { openRoom?.let { DuelCenter.openRoom.value = null; if (inMatch != it) nav.navigate("duel/$it") { launchSingleTop = true } } }
    LaunchedEffect(pendingCode) {
        val code = pendingCode ?: return@LaunchedEffect
        DuelCenter.pendingCode.value = null
        try {
            when (val r = DuelRepo.joinByCode(code)) {
                is DuelRepo.JoinResult.Duel -> nav.navigate("duel/${r.code}") { launchSingleTop = true }
                is DuelRepo.JoinResult.Legacy -> nav.navigate("faceoff?code=${r.code}")
            }
        } catch (e: Exception) {
            nav.navigate("duels") { launchSingleTop = true }
        }
    }

    Box(Modifier.fillMaxSize()) {
    NavHost(
        navController = nav,
        startDestination = "tabs",
        enterTransition = { if (reduce) fadeIn(snap()) else slideInHorizontally(tween(320)) { it / 4 } + fadeIn(tween(220)) },
        exitTransition = { if (reduce) fadeOut(snap()) else fadeOut(tween(180)) },
        popEnterTransition = { if (reduce) fadeIn(snap()) else fadeIn(tween(220)) },
        popExitTransition = { if (reduce) fadeOut(snap()) else slideOutHorizontally(tween(280)) { it / 4 } + fadeOut(tween(200)) },
    ) {
        composable("tabs") {
            Scaffold(
                containerColor = Mx.colors.groupedBackground,
                bottomBar = {
                    val c = Mx.colors
                    Column {
                        HorizontalDivider(color = c.separator, thickness = 1.dp)
                        NavigationBar(containerColor = c.card, tonalElevation = 0.dp) {
                            Tab.entries.forEach { t ->
                                val on = tab == t
                                NavigationBarItem(
                                    selected = on,
                                    onClick = { tab = t },
                                    icon = { Icon(if (on) t.selectedIcon else t.icon, null) },
                                    label = { Text(t.label, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = c.onAccentContainer,
                                        selectedTextColor = c.ink,
                                        indicatorColor = c.accentContainer,
                                        unselectedIconColor = c.secondaryText,
                                        unselectedTextColor = c.secondaryText,
                                    ),
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                val m = Modifier.padding(bottom = padding.calculateBottomPadding())
                when (tab) {
                    Tab.HOME -> HomeScreen(navigator, m, onTab = { tab = it })
                    Tab.QBANK -> QBankScreen(navigator, m)
                    Tab.TESTS -> TestsScreen(navigator, m)
                    Tab.CLASSES -> VideosScreen(navigator, m)
                    Tab.LIBRARY -> LibraryScreen(navigator, m)
                }
            }
        }
        composable("qbank/subject/{id}") { e -> SubjectScreen(navigator, e.arguments?.getString("id") ?: "") }
        composable("runner") { RunnerScreen(navigator, Handoff.payload) }
        composable("player") { PlayerScreen(navigator, Handoff.video) }
        composable("videos/group") { VideoSubjectScreen(navigator, Handoff.videoGroupTitle, Handoff.videoGroup) }
        composable("vod") { VodFeedScreen(navigator) }
        composable("flashcards") { FlashcardsScreen(navigator) }
        composable("flashcards/{id}") { e -> FlashcardDeckScreen(navigator, e.arguments?.getString("id")?.toIntOrNull() ?: 0) }
        composable("batch") { BatchPapersScreen(navigator) }
        composable("custom") { CustomModulesScreen(navigator) }
        composable("custom/edit?id={id}") { e -> CustomModuleEditor(navigator, e.arguments?.getString("id")) }
        composable("bookmarks") { BookmarksScreen(navigator) }
        composable("history") { WatchHistoryScreen(navigator) }
        composable("activity") { ActivityLogScreen(navigator) }
        composable("search") { SearchScreen(navigator) }
        composable("settings") { SettingsScreen(navigator) }
        composable("tracker") { SyllabusTrackerScreen(navigator) }
        composable("faceoff?code={code}", arguments = listOf(androidx.navigation.navArgument("code") { type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null })) { e -> FaceoffScreen(navigator, e.arguments?.getString("code")) }
        composable("duels") { DuelHubScreen(navigator) }
        composable("duels/quick") { QuickMatchScreen(navigator) }
        composable("duel/{code}") { e -> DuelMatchScreen(navigator, e.arguments?.getString("code") ?: "") }
    }

    // A duel still running (e.g. after leaving the room or restarting the app): one tap to rejoin.
    val running = active.firstOrNull { it.status == MatchStatus.LIVE && it.uids.size == 2 }
    if (route == "tabs" && running != null && inMatch == null) {
        val me = Medx.auth.uid ?: ""
        RejoinPill(
            running.nameOf(running.opponentOf(me)), true, { nav.navigate("duel/${running.code}") { launchSingleTop = true } },
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 92.dp),
        )
    }
    // Incoming invite: shown over any screen except a live match.
    prompt?.let { inv ->
        if (inMatch == null) InvitePrompt(inv, onOpenRoom = { nav.navigate("duel/$it") { launchSingleTop = true } }, onDismiss = DuelCenter::consumePrompt)
    }
    }
}
