package quest.srihari.medxelite.screenshots

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import quest.srihari.medxelite.duel.DuelAnswer
import quest.srihari.medxelite.duel.DuelCenter
import quest.srihari.medxelite.duel.DuelGameDoc
import quest.srihari.medxelite.duel.DuelInvite
import quest.srihari.medxelite.duel.DuelMachine
import quest.srihari.medxelite.duel.DuelMatch
import quest.srihari.medxelite.duel.DuelRecord
import quest.srihari.medxelite.duel.DuelResultDoc
import quest.srihari.medxelite.duel.DuelSettings
import quest.srihari.medxelite.duel.DuelSide
import quest.srihari.medxelite.duel.EndReason
import quest.srihari.medxelite.duel.InviteStatus
import quest.srihari.medxelite.duel.MatchStatus
import quest.srihari.medxelite.duel.PublicProfile
import quest.srihari.medxelite.ui.AppRoot
import quest.srihari.medxelite.ui.Navigator
import quest.srihari.medxelite.ui.components.MedxTopBar
import quest.srihari.medxelite.ui.duel.DuelHubScreen
import quest.srihari.medxelite.ui.duel.DuelMatchContent
import quest.srihari.medxelite.ui.duel.DuelSetupContent
import quest.srihari.medxelite.ui.duel.DuelUiFlags
import quest.srihari.medxelite.ui.duel.InvitePrompt
import quest.srihari.medxelite.ui.duel.Searching
import quest.srihari.medxelite.ui.duel.SheetMode
import quest.srihari.medxelite.ui.theme.MedxTheme
import quest.srihari.medxelite.ui.theme.Mx
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/** Made-up duel state for the screenshots (test sources only, never shipped, no network). */
object DuelDemo {
    const val ME = DemoData.UID
    const val THEM = "asha-uid"
    val now = System.currentTimeMillis()
    val settings = DuelSettings("4", "Pharmacology", 10, 30)
    val deck = DemoData.questions.take(10)

    /** Option id helper: DemoData option ids are questionId*10 + index. */
    private fun pick(i: Int, idx: Int, ms: Long, askedAt: Long): DuelAnswer {
        val q = deck[i]
        val o = q.options[idx].id
        return DuelAnswer(i, o, q.isCorrect(o), ms, askedAt + ms)
    }

    private fun correctIdx(i: Int) = deck[i].options.indexOfFirst { deck[i].isCorrect(it.id) }
    private fun wrongIdx(i: Int) = (correctIdx(i) + 1) % deck[i].options.size

    fun game(status: MatchStatus, q: Int, askedAt: Long?, guest: String? = THEM, result: DuelResultDoc? = null, origin: String = "invite") = DuelGameDoc(
        code = "K7Q2PX", hostUid = ME, guestUid = guest, status = status, settings = settings, total = deck.size, deckParts = 1,
        qIndex = q, askedAt = askedAt, names = mapOf(ME to "Sri", THEM to "Asha"), photos = emptyMap(),
        ratings = mapOf(ME to 1268, THEM to 1310), played = mapOf(ME to 17, THEM to 22), origin = origin, inviteId = "inv_demo",
        createdAtMs = now - 120_000, updatedAtMs = now, finishedAtMs = if (status == MatchStatus.DONE) now else null, result = result,
    )

    /** My and Asha's answers for questions before [upTo]. */
    fun past(upTo: Int): Pair<Map<Int, DuelAnswer>, Map<Int, DuelAnswer>> {
        val mine = mutableMapOf<Int, DuelAnswer>()
        val theirs = mutableMapOf<Int, DuelAnswer>()
        val myWrong = setOf(4, 8)
        val theirWrong = setOf(1, 3, 6)
        for (i in 0 until upTo) {
            val asked = now - 400_000 + i * 35_000L
            mine[i] = pick(i, if (i in myWrong) wrongIdx(i) else correctIdx(i), 4_200L + (i * 1_300L) % 9_000, asked)
            if (i != 7) theirs[i] = pick(i, if (i in theirWrong) wrongIdx(i) else correctIdx(i), 6_800L + (i * 1_900L) % 11_000, asked)
        }
        return mine to theirs
    }

    val record = DuelRecord(rating = 1284, played = 18, wins = 11, losses = 6, draws = 1, streak = 3, bestStreak = 5, peak = 1301)

    fun invite(from: String, fromUid: String, to: String, toUid: String, ago: Long, s: DuelSettings = settings) =
        DuelInvite("inv_${fromUid}_$toUid", fromUid, from, null, toUid, to, InviteStatus.PENDING, "R4ND0M", s, now - ago, now - ago + DuelInvite.TTL_MS)

    fun history(): List<DuelGameDoc> {
        fun finished(code: String, opp: String, oppName: String, mine: Int, theirs: Int, delta: Int, daysAgo: Int, subj: String) = DuelGameDoc(
            code = code, hostUid = ME, guestUid = opp, status = MatchStatus.DONE, settings = DuelSettings(null, subj, 10, 30), total = 10, deckParts = 1,
            qIndex = 9, askedAt = null, names = mapOf(ME to "Sri", opp to oppName), photos = emptyMap(), ratings = emptyMap(), played = emptyMap(),
            origin = "invite", inviteId = null, createdAtMs = now - daysAgo * 86_400_000L, updatedAtMs = now - daysAgo * 86_400_000L,
            finishedAtMs = now - daysAgo * 86_400_000L,
            result = DuelResultDoc(
                if (mine == theirs) null else if (mine > theirs) ME else opp, EndReason.COMPLETE,
                mapOf(ME to quest.srihari.medxelite.duel.SideScore(ME, mine, 7, 9, 40_000), opp to quest.srihari.medxelite.duel.SideScore(opp, theirs, 5, 8, 52_000)),
                mapOf(ME to delta, opp to -delta),
            ),
        )
        return listOf(
            finished("H1", THEM, "Asha", 1342, 1105, 14, 0, "Pharmacology"),
            finished("H2", "rahul", "Rahul", 980, 1210, -11, 1, "Mixed"),
            finished("H3", "meera", "Meera", 1460, 1188, 12, 2, "Microbiology"),
            finished("H4", "karthik", "Karthik", 1120, 1120, 0, 4, "Anatomy"),
        )
    }

    fun installHub() {
        DuelCenter.record.value = record
        DuelCenter.history.value = history()
        DuelCenter.incoming.value = listOf(invite("Rahul", "rahul", "Sri", ME, 75_000, DuelSettings(null, "Mixed", 20, 30)))
        DuelCenter.outgoing.value = listOf(invite("Sri", ME, "Meera", "meera", 200_000, DuelSettings("7", "Microbiology", 10, 45)))
        DuelCenter.active.value = emptyList()
        DuelCenter.loaded.value = true
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class DuelScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun out(name: String) = "build/outputs/roborazzi/$name.png"
    private fun shot(name: String) = compose.onRoot().captureRoboImage(out(name))

    @Before
    fun setUp() {
        ProxySelector.setDefault(object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> = throw IOException("network disabled in screenshot tests")
            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {}
        })
        Medx.init(ApplicationProvider.getApplicationContext())
        DuelUiFlags.askNotificationPermission = false
        DuelCenter.reset()
    }

    private fun show(appearance: MedxAppearance = MedxAppearance.LIGHT, content: @Composable (Navigator) -> Unit) {
        DemoData.install(true)
        Medx.prefs.appearance.value = appearance
        compose.setContent {
            MedxTheme(MedxAccent.BLUE, appearance, reduceMotion = true) { content(Navigator(rememberNavController())) }
        }
        compose.waitForIdle()
    }

    @Composable
    private fun match(game: DuelGameDoc, mine: Map<Int, DuelAnswer>, theirs: Map<Int, DuelAnswer>, now: Long, ready: Boolean = true): DuelMatch {
        val m = remember { DuelMatch(game.code, CoroutineScope(Dispatchers.Unconfined)) }
        remember(game, now) {
            m.install(
                game,
                DuelSide(DuelDemo.ME, "Sri", mine, ready = true, heartbeatAt = now),
                game.guestUid?.let { DuelSide(it, "Asha", theirs, ready = ready, heartbeatAt = now) },
                DuelDemo.deck, now,
            )
        }
        return m
    }

    @Composable
    private fun MatchShot(game: DuelGameDoc, mine: Map<Int, DuelAnswer>, theirs: Map<Int, DuelAnswer>, now: Long, ready: Boolean = true) {
        DuelMatchContent(match(game, mine, theirs, now, ready), onBack = {}, onShare = {}, onOpenRoom = {}, onDone = {})
    }

    // ------------------------------------------------------------ hub

    @Test fun d01_hub() { DuelDemo.installHub(); show { DuelHubScreen(it, live = false) }; shot("d01-duel-hub") }
    @Test fun d01b_hubDark() { DuelDemo.installHub(); show(MedxAppearance.DARK) { DuelHubScreen(it, live = false) }; shot("d01b-duel-hub-dark") }

    @Test fun d01c_hubScrolled() {
        DuelDemo.installHub()
        show { DuelHubScreen(it, live = false) }
        compose.onNodeWithText("History").performScrollTo()
        compose.waitForIdle()
        shot("d01c-duel-hub-history")
    }

    @Test fun d01d_hubNewUser() {
        DuelCenter.record.value = DuelRecord(); DuelCenter.loaded.value = true
        show { DuelHubScreen(it, live = false) }
        shot("d01d-duel-hub-new-user")
    }

    // ------------------------------------------------------------ invite sheet + prompt

    @Test fun d02_inviteSheet() {
        DuelDemo.installHub()
        val asha = PublicProfile(DuelDemo.THEM, "Asha Raman", null, DuelDemo.now - 30_000, null, 1310)
        val results = listOf(
            asha,
            PublicProfile("ashwin", "Ashwin Kumar", null, DuelDemo.now - 60_000, "Q2B9ZZ", 1192),
            PublicProfile("asif", "Asif Rahman", null, DuelDemo.now - 3_600_000, null, 1240),
        )
        show {
            SheetFrame { DuelSetupContent(SheetMode.INVITE, asha, onCreated = {}, live = false, initialQuery = "as", previewResults = results) }
        }
        shot("d02-invite-sheet-search")
    }

    @Test fun d02b_roomSheetDark() {
        show(MedxAppearance.DARK) { SheetFrame { DuelSetupContent(SheetMode.ROOM, null, onCreated = {}, live = false) } }
        shot("d02b-room-sheet-dark")
    }

    @Test fun d03_incomingInvite() {
        DuelDemo.installHub()
        val inv = DuelDemo.invite("Asha", DuelDemo.THEM, "Sri", DuelDemo.ME, 40_000)
        show {
            Box(Modifier.fillMaxSize()) {
                DuelHubScreen(it, live = false)
                InvitePrompt(inv, onOpenRoom = {}, onDismiss = {})
            }
        }
        shot("d03-incoming-invite")
    }

    // ------------------------------------------------------------ lobby + countdown

    @Test fun d04_lobbyInvitePending() {
        DuelCenter.outgoing.value = listOf(DuelDemo.invite("Sri", DuelDemo.ME, "Asha", DuelDemo.THEM, 52_000).copy(gameId = "K7Q2PX"))
        val g = DuelDemo.game(MatchStatus.LOBBY, 0, null, guest = null)
        show { MatchShot(g, emptyMap(), emptyMap(), DuelDemo.now) }
        shot("d04-lobby-invite-pending")
    }

    @Test fun d04b_lobbyRoomCode() {
        val g = DuelDemo.game(MatchStatus.LOBBY, 0, null, guest = null, origin = "code")
        show { MatchShot(g, emptyMap(), emptyMap(), DuelDemo.now) }
        shot("d04b-lobby-room-code")
    }

    @Test fun d04c_countdown() {
        val g = DuelDemo.game(MatchStatus.LIVE, 0, DuelDemo.now + 2_400)
        show { MatchShot(g, emptyMap(), emptyMap(), DuelDemo.now) }
        shot("d04c-start-countdown")
    }

    // ------------------------------------------------------------ live

    @Test fun d05_liveOpponentAnswered() {
        val asked = DuelDemo.now - 11_000
        val (mine, theirs) = DuelDemo.past(3)
        val q = DuelDemo.deck[3]
        val theirNow = theirs + (3 to DuelAnswer(3, q.options[2].id, false, 7_400, asked + 7_400))
        val g = DuelDemo.game(MatchStatus.LIVE, 3, asked)
        show { MatchShot(g, mine, theirNow, DuelDemo.now) }
        shot("d05-live-opponent-answered")
    }

    @Test fun d05b_liveReveal() {
        val asked = DuelDemo.now - 12_000
        val (mine, theirs) = DuelDemo.past(3)
        val q = DuelDemo.deck[3]
        val right = q.options.first { q.isCorrect(it.id) }
        val m2 = mine + (3 to DuelAnswer(3, right.id, true, 6_200, asked + 6_200))
        val t2 = theirs + (3 to DuelAnswer(3, q.options[2].id, false, 7_400, asked + 7_400))
        val g = DuelDemo.game(MatchStatus.LIVE, 3, asked)
        show { MatchShot(g, m2, t2, DuelDemo.now) }
        shot("d05b-live-reveal")
    }

    @Test fun d05c_liveDarkLocked() {
        val asked = DuelDemo.now - 4_000
        val (mine, theirs) = DuelDemo.past(6)
        val q = DuelDemo.deck[6]
        val m2 = mine + (6 to DuelAnswer(6, q.options[0].id, q.isCorrect(q.options[0].id), 3_100, asked + 3_100))
        val g = DuelDemo.game(MatchStatus.LIVE, 6, asked)
        show(MedxAppearance.DARK) { MatchShot(g, m2, theirs, DuelDemo.now) }
        shot("d05c-live-locked-in-dark")
    }

    // ------------------------------------------------------------ results

    private fun finishedGame(): Triple<DuelGameDoc, Map<Int, DuelAnswer>, Map<Int, DuelAnswer>> {
        val (mine, theirs) = DuelDemo.past(10)
        val base = DuelDemo.game(MatchStatus.LIVE, 9, DuelDemo.now - 60_000)
        val outcome = DuelMachine.outcome(base.snapshot, DuelSide(DuelDemo.ME, "Sri", mine), DuelSide(DuelDemo.THEM, "Asha", theirs))
        val result = DuelResultDoc.build(outcome, base)
        return Triple(DuelDemo.game(MatchStatus.DONE, 9, DuelDemo.now - 60_000, result = result), mine, theirs)
    }

    @Test fun d06_results() {
        val (g, mine, theirs) = finishedGame()
        show { MatchShot(g, mine, theirs, DuelDemo.now) }
        shot("d06-results")
    }

    @Test fun d06b_resultsReview() {
        val (g, mine, theirs) = finishedGame()
        show { MatchShot(g, mine, theirs, DuelDemo.now) }
        compose.onNodeWithText("Q4").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Q4").performScrollTo()
        compose.waitForIdle()
        shot("d06b-results-review")
    }

    // ------------------------------------------------------------ quick match + rejoin

    @Test fun d07_quickMatch() {
        show {
            Scaffold(containerColor = Mx.colors.groupedBackground, topBar = { MedxTopBar("Quick match", onBack = {}) }) { pad ->
                Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { Searching(false, 12_000, {}) }
            }
        }
        shot("d07-quick-match-searching")
    }

    @Test fun d08_rejoinPillOnHome() {
        DuelCenter.active.value = listOf(DuelDemo.game(MatchStatus.LIVE, 4, DuelDemo.now))
        show { AppRoot() }
        shot("d08-rejoin-pill-home")
    }

    @Composable
    private fun SheetFrame(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f))) {
            Column(
                Modifier.align(Alignment.BottomCenter).padding(top = 64.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Mx.colors.groupedBackground),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(50)).background(Mx.colors.tertiaryText.copy(alpha = 0.4f)))
                Spacer(Modifier.height(18.dp))
                content()
            }
        }
    }

    @Suppress("unused") private val keepSize = Modifier.size(1.dp)
}
