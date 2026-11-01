package quest.srihari.medxelite.screenshots

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
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
import quest.srihari.medxelite.ui.faceoff.FaceoffScreen
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
 * app/build/outputs/roborazzi/. Motion is held still (reduceMotion) so every dot is lit.
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

    private fun show(
        signedIn: Boolean = true,
        appearance: MedxAppearance = MedxAppearance.DARK,
        content: @Composable (Navigator) -> Unit,
    ) {
        DemoData.install(signedIn)
        Medx.prefs.appearance.value = appearance
        compose.setContent {
            MedxTheme(MedxAccent.RED, appearance, reduceMotion = true) {
                content(Navigator(rememberNavController()))
            }
        }
        compose.waitForIdle()
    }

    private fun tab(label: String) {
        compose.onAllNodesWithText(label).onFirst().performClick()
        compose.waitForIdle()
    }

    private fun runner(answers: List<String>) {
        val payload = RunnerPayload(
            "qbank", "qb_4110", "Antihypertensives", "Pharmacology", SittingMode.REVISION,
            questions = DemoData.questions,
        )
        show { RunnerScreen(it, payload) }
        answers.forEachIndexed { i, letter ->
            compose.onNodeWithText(letter).performClick()
            compose.waitForIdle()
            if (i < answers.size - 1) {
                compose.onNodeWithText("Next").performClick()
                compose.waitForIdle()
            }
        }
    }

    private fun review() {
        show {
            SittingReview(
                name = "Antihypertensives", subject = "Pharmacology", sourceId = "qb_4110",
                questions = DemoData.questions, responses = DemoData.responses, gradable = true,
                elapsedSeconds = 7 * 60 + 42, sections = emptyList(), tags = null, onDone = {},
            )
        }
    }

    @Test fun s01_signIn() {
        show(signedIn = false) { SignInScreen() }
        compose.onRoot().captureRoboImage(out("01-signin"))
    }

    @Test fun s02_home() {
        show { AppRoot() }
        compose.onRoot().captureRoboImage(out("02-home"))
    }

    @Test fun s02b_homeScrolled() {
        show { AppRoot() }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(6)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(out("02b-home-scrolled"))
    }

    @Test fun s03_homeLight() {
        show(appearance = MedxAppearance.LIGHT) { AppRoot() }
        compose.onRoot().captureRoboImage(out("03-home-light"))
    }

    @Test fun s04_qbank() {
        show { AppRoot() }
        tab("QBank")
        compose.onRoot().captureRoboImage(out("04-qbank-subjects"))
    }

    @Test fun s05_chapters() {
        show { SubjectScreen(it, DemoData.pharmacology.id) }
        compose.onRoot().captureRoboImage(out("05-chapter-modules"))
    }

    @Test fun s06_runnerWrong() {
        // Q1-3 right, then the wrong pick (C, losartan) on Q4: red pick + inverted correct answer.
        runner(listOf("C", "B", "C", "C"))
        compose.onNodeWithText("04").assertExists() // the taps on options must land
        compose.onNodeWithText("EXPLANATION").assertExists()
        compose.onRoot().captureRoboImage(out("06-quiz-runner-wrong"))
    }

    @Test fun s07_runnerCorrect() {
        runner(listOf("C", "B", "C", "B"))
        compose.onNodeWithText("EXPLANATION").assertExists()
        compose.onRoot().captureRoboImage(out("07-quiz-runner-correct"))
    }

    @Test fun s08_results() {
        review()
        compose.onRoot().captureRoboImage(out("08-results"))
    }

    @Test fun s09_review() {
        review()
        // Show the wrong answers and open the first one, so the review's answer key is visible.
        compose.onNodeWithText("Wrong (2)").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Q4").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(out("09-review"))
    }

    @Test fun s10_flashcards() {
        show { FlashcardsScreen(it) }
        compose.onRoot().captureRoboImage(out("10-flashcards"))
    }

    @Test fun s11_tests() {
        show { AppRoot() }
        tab("Tests")
        compose.onRoot().captureRoboImage(out("11-tests"))
    }

    @Test fun s12_classes() {
        show { AppRoot() }
        tab("Classes")
        compose.onRoot().captureRoboImage(out("12-classes"))
    }

    @Test fun s13_library() {
        show { AppRoot() }
        tab("Library")
        compose.onRoot().captureRoboImage(out("13-library"))
    }

    @Test fun s14_settings() {
        show { SettingsScreen(it) }
        compose.onRoot().captureRoboImage(out("14-settings"))
    }

    @Test fun s15_faceoff() {
        show { FaceoffScreen(it) }
        compose.onRoot().captureRoboImage(out("15-faceoff"))
    }

    @Test fun s16_qbankLight() {
        show(appearance = MedxAppearance.LIGHT) { AppRoot() }
        tab("QBank")
        compose.onRoot().captureRoboImage(out("16-qbank-light"))
    }

    @Test fun s17_runnerLight() {
        val payload = RunnerPayload(
            "qbank", "qb_4110", "Antihypertensives", "Pharmacology", SittingMode.REVISION,
            questions = DemoData.questions,
        )
        show(appearance = MedxAppearance.LIGHT) { RunnerScreen(it, payload) }
        compose.onNodeWithText("C").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(out("17-quiz-runner-light"))
    }
}
