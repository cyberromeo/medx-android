package quest.srihari.medxelite.screenshots

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import quest.srihari.medxelite.Medx
import quest.srihari.medxelite.data.MedxAccent
import quest.srihari.medxelite.data.MedxAppearance
import quest.srihari.medxelite.data.RunnerPayload
import quest.srihari.medxelite.data.SittingMode
import quest.srihari.medxelite.ui.AppRoot
import quest.srihari.medxelite.ui.Navigator
import quest.srihari.medxelite.ui.auth.SignInScreen
import quest.srihari.medxelite.ui.flashcards.FlashcardsScreen
import quest.srihari.medxelite.ui.qbank.SubjectScreen
import quest.srihari.medxelite.ui.runner.RunnerScreen
import quest.srihari.medxelite.ui.runner.SittingReview
import quest.srihari.medxelite.ui.settings.SettingsScreen
import quest.srihari.medxelite.ui.theme.MedxTheme
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * Phone screenshots of the real screens, fed with [DemoData]. Run with
 * `./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest*'`; PNGs land in
 * app/build/outputs/roborazzi/.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun out(name: String) = "build/outputs/roborazzi/$name.png"

    @Before
    fun setUp() {
        // Belt and braces: nothing in a screenshot run may reach the network.
        ProxySelector.setDefault(object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> = throw IOException("network disabled in screenshot tests")
            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {}
        })
        Medx.init(ApplicationProvider.getApplicationContext())
    }

    private fun show(signedIn: Boolean = true, content: @Composable (Navigator) -> Unit) {
        DemoData.install(signedIn)
        compose.setContent {
            MedxTheme(MedxAccent.AURORA, MedxAppearance.DARK, reduceMotion = true) {
                content(Navigator(rememberNavController()))
            }
        }
        compose.waitForIdle()
    }

    @Test fun s01_signIn() {
        show(signedIn = false) { SignInScreen() }
        compose.onRoot().captureRoboImage(out("01-signin"))
    }

    @Test fun s02_home() {
        show { AppRoot() }
        compose.onRoot().captureRoboImage(out("02-home"))
    }

    @Test fun s03_qbank() {
        show { AppRoot() }
        compose.onNodeWithText("QBank").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(out("03-qbank-subjects"))
    }

    @Test fun s04_chapters() {
        show { SubjectScreen(it, DemoData.pharmacology.id) }
        compose.onRoot().captureRoboImage(out("04-chapter-modules"))
    }

    @Test fun s05_runner() {
        val payload = RunnerPayload(
            "qbank", "qb_4110", "Antihypertensives", "Pharmacology", SittingMode.REVISION,
            questions = DemoData.questions,
        )
        show { RunnerScreen(it, payload) }
        // Answer the first three, then pick the right answer on question 4 so its explanation shows.
        val answers = listOf("C", "B", "C", "B")
        answers.forEachIndexed { i, letter ->
            compose.onNodeWithText(letter).performClick()
            compose.waitForIdle()
            if (i < answers.size - 1) {
                compose.onNodeWithText("Next").performClick()
                compose.waitForIdle()
            }
        }
        compose.onNodeWithText("Question 4 of 10").assertExists() // the taps on options must land
        compose.onNodeWithText("Explanation").assertExists()
        compose.onRoot().captureRoboImage(out("05-quiz-runner"))
    }

    @Test fun s06_review() {
        show {
            SittingReview(
                name = "Antihypertensives", subject = "Pharmacology", sourceId = "qb_4110",
                questions = DemoData.questions, responses = DemoData.responses, gradable = true,
                elapsedSeconds = 7 * 60 + 42, sections = emptyList(), tags = null, onDone = {},
            )
        }
        // Show the wrong answers and open the first one, so the review's answer key is visible.
        compose.onNodeWithText("Wrong (2)").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Q4").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(out("06-review"))
    }

    @Test fun s07_flashcards() {
        show { FlashcardsScreen(it) }
        compose.onRoot().captureRoboImage(out("07-flashcards"))
    }

    @Test fun s08_settings() {
        show { SettingsScreen(it) }
        compose.onRoot().captureRoboImage(out("08-settings"))
    }
}
