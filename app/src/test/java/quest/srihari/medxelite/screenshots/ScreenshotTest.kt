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
import org.robolectric.RuntimeEnvironment
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
 * app/build/outputs/roborazzi/. Motion is held still (reduceMotion) so progress shows its final state.
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
        appearance: MedxAppearance = MedxAppearance.LIGHT,
        fontScale: Float = 1f,
        content: @Composable (Navigator) -> Unit,
    ) {
        DemoData.install(signedIn)
        Medx.prefs.appearance.value = appearance
        if (fontScale != 1f) RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            MedxTheme(MedxAccent.BLUE, appearance, reduceMotion = true) {
                content(Navigator(rememberNavController()))
            }
        }
        compose.waitForIdle()
    }

    private fun tab(label: String) {
        compose.onAllNodesWithText(label).onFirst().performClick()
        compose.waitForIdle()
    }

    private fun runner(answers: List<String>, appearance: MedxAppearance = MedxAppearance.LIGHT) {
        val payload = RunnerPayload(
            "qbank", "qb_4110", "Antihypertensives", "Pharmacology", SittingMode.REVISION,
            questions = DemoData.questions,
        )
        show(appearance = appearance) { RunnerScreen(it, payload) }
        answers.forEachIndexed { i, letter ->
            compose.onNodeWithText(letter).performClick()
            compose.waitForIdle()
            if (i < answers.size - 1) {
                compose.onNodeWithText("Next").performClick()
                compose.waitForIdle()
            }
        }
    }

    private fun review(appearance: MedxAppearance = MedxAppearance.LIGHT) {
        show(appearance = appearance) {
            SittingReview(
                name = "Antihypertensives", subject = "Pharmacology", sourceId = "qb_4110",
                questions = DemoData.questions, responses = DemoData.responses, gradable = true,
                elapsedSeconds = 7 * 60 + 42, sections = emptyList(), tags = null, onDone = {},
            )
        }
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage(out(name))

    @Test fun s01_signInLight() { show(signedIn = false) { SignInScreen() }; shot("01-signin-light") }
    @Test fun s01b_signInDark() { show(signedIn = false, appearance = MedxAppearance.DARK) { SignInScreen() }; shot("01b-signin-dark") }

    @Test fun s02_homeLight() { show { AppRoot() }; shot("02-home-light") }
    @Test fun s02b_homeDark() { show(appearance = MedxAppearance.DARK) { AppRoot() }; shot("02b-home-dark") }

    @Test fun s02c_homeScrolled() {
        show { AppRoot() }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(5)
        compose.waitForIdle()
        shot("02c-home-scrolled")
    }

    @Test fun s04_qbank() { show { AppRoot() }; tab("QBank"); shot("04-qbank-subjects") }
    @Test fun s04b_qbankDark() { show(appearance = MedxAppearance.DARK) { AppRoot() }; tab("QBank"); shot("04b-qbank-dark") }

    @Test fun s05_chapters() { show { SubjectScreen(it, DemoData.pharmacology.id) }; shot("05-chapter-modules") }

    @Test fun s06_runnerWrong() {
        // Q1-3 right, then the wrong pick (C, losartan) on Q4: red pick + the right answer shown.
        runner(listOf("C", "B", "C", "C"))
        compose.onNodeWithText("Question 4 of ${DemoData.questions.size}").assertExists() // the taps on options must land
        compose.onNodeWithText("Not quite").assertExists()
        shot("06-quiz-runner-wrong")
    }

    @Test fun s07_runnerCorrect() {
        runner(listOf("C", "B", "C", "B"))
        compose.onNodeWithText("Correct").assertExists()
        shot("07-quiz-runner-correct")
    }

    @Test fun s07b_runnerDark() {
        runner(listOf("C", "B", "C", "B"), MedxAppearance.DARK)
        shot("07b-quiz-runner-dark")
    }

    @Test fun s07c_runnerUnanswered() {
        runner(listOf("C"))
        compose.onNodeWithText("Next").performClick()
        compose.waitForIdle()
        shot("07c-quiz-runner-question")
    }

    @Test fun s08_results() { review(); shot("08-results") }
    @Test fun s08b_resultsDark() { review(MedxAppearance.DARK); shot("08b-results-dark") }

    @Test fun s09_review() {
        review()
        compose.onNodeWithText("Wrong (2)").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Q4").performClick()
        compose.waitForIdle()
        shot("09-review")
    }

    @Test fun s10_flashcards() { show { FlashcardsScreen(it) }; shot("10-flashcards") }
    @Test fun s11_tests() { show { AppRoot() }; tab("Tests"); shot("11-tests") }
    @Test fun s12_classes() { show { AppRoot() }; tab("Classes"); shot("12-classes") }
    @Test fun s13_library() { show { AppRoot() }; tab("Library"); shot("13-library") }
    @Test fun s14_settings() { show { SettingsScreen(it) }; shot("14-settings") }
    @Test fun s14b_settingsDark() { show(appearance = MedxAppearance.DARK) { SettingsScreen(it) }; shot("14b-settings-dark") }
    @Test fun s15_faceoff() { show { FaceoffScreen(it) }; shot("15-faceoff") }

    @Test fun s16_homeLargeText() { show(fontScale = 1.3f) { AppRoot() }; shot("16-home-font-1.3x") }
    @Test fun s17_runnerLargeText() {
        val payload = RunnerPayload("qbank", "qb_4110", "Antihypertensives", "Pharmacology", SittingMode.REVISION, questions = DemoData.questions)
        show(fontScale = 1.3f) { RunnerScreen(it, payload) }
        compose.onNodeWithText("C").performClick()
        compose.waitForIdle()
        shot("17-quiz-runner-font-1.3x")
    }
}
