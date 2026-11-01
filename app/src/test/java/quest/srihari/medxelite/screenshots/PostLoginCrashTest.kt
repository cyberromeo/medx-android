package quest.srihari.medxelite.screenshots

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import quest.srihari.medxelite.Medx
import quest.srihari.medxelite.auth.AuthSession
import quest.srihari.medxelite.data.Catalog
import quest.srihari.medxelite.data.Load
import quest.srihari.medxelite.data.MedxAccent
import quest.srihari.medxelite.data.MedxAppearance
import quest.srihari.medxelite.data.StatsMath
import quest.srihari.medxelite.data.StudyStats
import quest.srihari.medxelite.ui.AppRoot
import quest.srihari.medxelite.ui.Navigator
import quest.srihari.medxelite.ui.settings.SettingsScreen
import quest.srihari.medxelite.ui.theme.MedxTheme
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * Renders what a real user sees right after signing in, without demo data: a brand-new account
 * (no attempts, no stats, no history), catalogues still loading or failed, and motion ON (the
 * screenshot tests hold motion still, which hid draw-time crashes). Runs in LEGACY graphics so it
 * also works on machines the native Robolectric runtime does not support. Any exception thrown
 * while composing, measuring or drawing fails the test with its stack trace.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class PostLoginCrashTest {
    @get:Rule val compose = createComposeRule()

    private val newUser = AuthSession(
        idToken = "t", refreshToken = "r", uid = "new-user", email = "",
        displayName = null, photoUrl = null, provider = "password", expiresAtMs = Long.MAX_VALUE,
    )

    @Before
    fun setUp() {
        ProxySelector.setDefault(object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> = throw IOException("network disabled in tests")
            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {}
        })
        Medx.init(ApplicationProvider.getApplicationContext())
    }

    @Suppress("UNCHECKED_CAST")
    private fun installEmpty(catalog: Load<Nothing>) {
        (Medx.auth.session as MutableStateFlow<AuthSession?>).value = newUser
        Medx.prefs.accent.value = MedxAccent.RED
        Medx.prefs.appearance.value = MedxAppearance.DARK
        Medx.attempts.attempts.value = emptyList()
        Medx.attempts.stats.value = StudyStats() // before the first compute: loaded = false
        Medx.attempts.syncing.value = true
        Medx.activity.history.value = emptyList()
        Medx.activity.bookmarks.value = emptyList()
        Medx.custom.modules.value = emptyList()
        Catalog.banks.value = catalog
        Catalog.series.value = catalog
        Catalog.tests.value = catalog
        Catalog.videos.value = catalog
        Catalog.flashcards.value = catalog
    }

    private fun renderApp(appearance: MedxAppearance = MedxAppearance.DARK) {
        compose.setContent {
            // Exactly what MainActivity does: no reduceMotion override, so animations run.
            MedxTheme(MedxAccent.RED, appearance) { AppRoot() }
        }
        settle()
    }

    private fun settle() {
        // Run real frames so the motion clock and animations advance and every draw lambda runs.
        repeat(6) { compose.mainClock.advanceTimeBy(250) }
        compose.waitForIdle()
    }

    private fun visitTabs() {
        for (tab in listOf("QBank", "Tests", "Classes", "Library", "Home")) {
            compose.onAllNodesWithText(tab).onFirst().performClick()
            settle()
        }
    }

    @Test fun emptyUser_loading_allTabs() {
        installEmpty(Load.Loading)
        renderApp()
        visitTabs()
    }

    @Test fun emptyUser_failed_allTabs() {
        installEmpty(Load.Failed("offline"))
        renderApp()
        visitTabs()
    }

    @Test fun emptyUser_readyEmpty_allTabs() {
        installEmpty(Load.Loading)
        Catalog.banks.value = Load.Ready(emptyList())
        Catalog.series.value = Load.Ready(emptyList())
        Catalog.tests.value = Load.Ready(emptyList())
        Catalog.videos.value = Load.Ready(emptyList())
        Catalog.flashcards.value = Load.Ready(emptyList())
        Medx.attempts.stats.value = StatsMath.compute(emptyList())
        Medx.attempts.syncing.value = false
        renderApp()
        visitTabs()
    }

    @Test fun emptyUser_light_allTabs() {
        installEmpty(Load.Loading)
        renderApp(MedxAppearance.LIGHT)
        visitTabs()
    }

    @Test fun emptyUser_settings() {
        installEmpty(Load.Loading)
        compose.setContent {
            MedxTheme(MedxAccent.RED, MedxAppearance.DARK) { SettingsScreen(Navigator(rememberNavController())) }
        }
        settle()
    }

    @Test fun demoUser_motionOn_allTabs() {
        DemoData.install(true)
        renderApp()
        visitTabs()
    }
}
