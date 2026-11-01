package quest.srihari.medxelite.ui

import quest.srihari.medxelite.ui.components.GlassBottomBar
import quest.srihari.medxelite.ui.components.NavItem
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import quest.srihari.medxelite.data.RecordedVideo
import quest.srihari.medxelite.data.RunnerPayload
import quest.srihari.medxelite.ui.custom.CustomModuleEditor
import quest.srihari.medxelite.ui.custom.CustomModulesScreen
import quest.srihari.medxelite.ui.faceoff.FaceoffScreen
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

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    QBANK("QBank", Icons.AutoMirrored.Rounded.LibraryBooks),
    TESTS("Tests", Icons.Rounded.Assignment),
    CLASSES("Classes", Icons.Rounded.PlayCircle),
    LIBRARY("Library", Icons.Rounded.GridView),
}

private val navItems = Tab.entries.map { NavItem(it.label, it.icon) }

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

    NavHost(
        navController = nav,
        startDestination = "tabs",
        enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
    ) {
        composable("tabs") {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    GlassBottomBar(navItems, tab.ordinal) { tab = Tab.entries[it] }
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
        composable("faceoff") { FaceoffScreen(navigator) }
    }
}
