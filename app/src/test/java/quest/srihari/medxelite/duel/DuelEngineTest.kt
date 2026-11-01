package quest.srihari.medxelite.duel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import quest.srihari.medxelite.data.Question
import quest.srihari.medxelite.data.QuestionOption

/** Plain JVM tests for the duel rules both phones run: scoring, Elo, the state machine, dealing, links. */
class DuelEngineTest {

    private val H = "host"
    private val G = "guest"

    private fun snap(status: MatchStatus = MatchStatus.LIVE, q: Int = 0, askedAt: Long? = 10_000, total: Int = 5, per: Int = 30) =
        MatchSnapshot(status, total, per, q, askedAt, H, G)

    private fun ans(q: Int, correct: Boolean, ms: Long, at: Long = 10_000 + ms, opt: Int? = 1) = DuelAnswer(q, opt, correct, ms, at)

    // ------------------------------------------------------------ scoring

    @Test fun wrongOrTimeoutScoresZero() {
        assertEquals(0, DuelScoring.points(false, 1_000, 30))
        assertEquals(0, ans(0, true, 5_000).copy(timedOut = true).points(30))
    }

    @Test fun instantRightAnswerScoresMax_lastSecondScoresBase() {
        assertEquals(200, DuelScoring.points(true, 0, 30))
        assertEquals(100, DuelScoring.points(true, 30_000, 30))
        assertEquals(150, DuelScoring.points(true, 15_000, 30))
    }

    @Test fun scoringClampsOutOfRangeTimes() {
        assertEquals(200, DuelScoring.points(true, -50, 30))
        assertEquals(100, DuelScoring.points(true, 99_000, 30))
    }

    @Test fun fasterRightAnswerAlwaysBeatsSlowerOne() {
        for (ms in 0L until 29_000L step 1_000) {
            assertTrue(DuelScoring.points(true, ms, 30) >= DuelScoring.points(true, ms + 1_000, 30))
        }
    }

    // ------------------------------------------------------------ Elo

    @Test fun eloIsZeroSumForEqualKAndSymmetricForEqualRatings() {
        val win = Elo.delta(1200, 1200, 1.0)
        val loss = Elo.delta(1200, 1200, 0.0)
        assertEquals(12, win)
        assertEquals(-12, loss)
        assertEquals(0, Elo.delta(1200, 1200, 0.5))
    }

    @Test fun upsetGainsMoreThanExpectedWin() {
        val underdog = Elo.delta(1000, 1400, 1.0)
        val favourite = Elo.delta(1400, 1000, 1.0)
        assertTrue(underdog > favourite)
        assertTrue(favourite in 1..5)
    }

    @Test fun provisionalPlayersMoveFaster() {
        assertEquals(20, Elo.delta(1200, 1200, 1.0, played = 0))
        assertEquals(12, Elo.delta(1200, 1200, 1.0, played = 50))
    }

    @Test fun recordTracksStreaksAndPeak() {
        var r = DuelRecord()
        r = r.apply(1.0, 1200).first
        r = r.apply(1.0, 1200).first
        assertEquals(2, r.streak); assertEquals(2, r.wins); assertEquals(2, r.bestStreak)
        r = r.apply(0.0, 1200).first
        assertEquals(-1, r.streak); assertEquals(1, r.losses); assertEquals(2, r.bestStreak)
        r = r.apply(0.5, 1200).first
        assertEquals(0, r.streak); assertEquals(1, r.draws); assertEquals(4, r.played)
        assertTrue(r.peak >= r.rating)
        assertEquals(0.5f, r.winRate)
    }

    @Test fun recordUsesTheResultsDeltaWhenGiven() {
        val (r, d) = DuelRecord(rating = 1300, played = 20).apply(1.0, 1100, fixedDelta = 7)
        assertEquals(7, d); assertEquals(1307, r.rating)
    }

    // ------------------------------------------------------------ phases

    @Test fun phaseWalksLobbyCountdownQuestionAnsweredReveal() {
        assertEquals(Phase.LOADING, DuelMachine.phase(null, null, null, 0))
        assertEquals(Phase.LOBBY, DuelMachine.phase(snap(MatchStatus.LOBBY, askedAt = null), null, null, 0))
        assertEquals(Phase.COUNTDOWN, DuelMachine.phase(snap(), null, null, 9_000))
        val me = DuelSide(H, "A")
        val them = DuelSide(G, "B")
        assertEquals(Phase.QUESTION, DuelMachine.phase(snap(), me, them, 12_000))
        val answered = me.copy(answers = mapOf(0 to ans(0, true, 2_000)))
        assertEquals(Phase.ANSWERED, DuelMachine.phase(snap(), answered, them, 12_500))
        val both = them.copy(answers = mapOf(0 to ans(0, false, 2_500)))
        assertEquals(Phase.REVEAL, DuelMachine.phase(snap(), answered, both, 12_600))
        // Nobody answered, but the clock ran out.
        assertEquals(Phase.REVEAL, DuelMachine.phase(snap(), me, them, 40_000))
        assertEquals(Phase.FINISHED, DuelMachine.phase(snap(MatchStatus.DONE), me, them, 0))
        assertEquals(Phase.CANCELLED, DuelMachine.phase(snap(MatchStatus.ABANDONED), me, them, 0))
    }

    @Test fun lobbyStartsOnlyWhenBothSeatsAreReady() {
        val lobby = snap(MatchStatus.LOBBY, askedAt = null)
        val host = DuelSide(H, "A", ready = true)
        assertEquals(DuelAction.None, DuelMachine.nextAction(lobby.copy(guestUid = null), host, null, 0))
        assertEquals(DuelAction.None, DuelMachine.nextAction(lobby, host, DuelSide(G, "B", ready = false), 0))
        val a = DuelMachine.nextAction(lobby, host, DuelSide(G, "B", ready = true), 1_000)
        assertEquals(DuelAction.Start(1_000 + DuelMachine.START_COUNTDOWN_MS), a)
        assertEquals(DuelAction.None, DuelMachine.nextAction(lobby, host, DuelSide(G, "B", ready = true, left = true), 1_000))
    }

    @Test fun advanceWaitsForRevealThenMovesOn() {
        val s = snap()
        val h = DuelSide(H, "A", answers = mapOf(0 to ans(0, true, 2_000)))
        val g = DuelSide(G, "B", answers = mapOf(0 to ans(0, true, 3_000)))
        val closed = 13_000L // last answer landed at askedAt + 3 s
        assertEquals(DuelAction.None, DuelMachine.nextAction(s, h, g, closed + DuelMachine.REVEAL_MS - 1))
        val a = DuelMachine.nextAction(s, h, g, closed + DuelMachine.REVEAL_MS)
        assertEquals(DuelAction.Advance(1, closed + DuelMachine.REVEAL_MS + DuelMachine.NEXT_GAP_MS), a)
    }

    @Test fun oneSilentPlayerDoesNotStallTheMatch() {
        val s = snap()
        val h = DuelSide(H, "A", answers = mapOf(0 to ans(0, true, 2_000)))
        val g = DuelSide(G, "B") // never answers (disconnected)
        assertEquals(DuelAction.None, DuelMachine.nextAction(s, h, g, 39_000))
        val deadline = 10_000L + 30_000
        assertTrue(DuelMachine.nextAction(s, h, g, deadline + DuelMachine.REVEAL_MS) is DuelAction.Advance)
    }

    @Test fun lastQuestionFinishesWithTheRightWinner() {
        val s = snap(q = 1, total = 2)
        val h = DuelSide(H, "A", answers = mapOf(0 to ans(0, true, 1_000), 1 to ans(1, true, 1_000)))
        val g = DuelSide(G, "B", answers = mapOf(0 to ans(0, true, 9_000), 1 to ans(1, false, 500)))
        val a = DuelMachine.nextAction(s, h, g, 100_000)
        assertTrue(a is DuelAction.Finish)
        val o = (a as DuelAction.Finish).outcome
        assertEquals(H, o.winner)
        assertEquals(EndReason.COMPLETE, o.reason)
        assertEquals(2, o.scores[H]!!.correct)
    }

    @Test fun leavingMidMatchIsAForfeit() {
        val s = snap()
        val h = DuelSide(H, "A", answers = mapOf(0 to ans(0, true, 1_000)))
        val g = DuelSide(G, "B", left = true)
        val a = DuelMachine.nextAction(s, h, g, 11_000) as DuelAction.Finish
        assertEquals(H, a.outcome.winner)
        assertEquals(EndReason.FORFEIT, a.outcome.reason)
        // Even when the leaver was ahead.
        val g2 = DuelSide(G, "B", answers = mapOf(0 to ans(0, true, 100)), left = true)
        assertEquals(H, (DuelMachine.nextAction(s, DuelSide(H, "A"), g2, 11_000) as DuelAction.Finish).outcome.winner)
    }

    @Test fun tiesBreakOnCorrectThenSpeedElseDraw() {
        val s = snap(total = 2, per = 30)
        // Same points from different mixes is practically impossible, so: equal points, equal correct, different time.
        val a = DuelSide(H, "A", answers = mapOf(0 to ans(0, true, 3_000)))
        val b = DuelSide(G, "B", answers = mapOf(0 to ans(0, true, 3_000)))
        assertNull(DuelMachine.outcome(s, a, b).winner)
        val c = DuelSide(G, "B", answers = mapOf(0 to ans(0, true, 3_010)))
        // 3000 vs 3010 ms round to the same points; the faster one still wins the tie-break.
        assertEquals(DuelScoring.points(true, 3_000, 30), DuelScoring.points(true, 3_010, 30))
        assertEquals(H, DuelMachine.outcome(s, a, c).winner)
        assertTrue(DuelMachine.outcome(s, DuelSide(H, "A"), DuelSide(G, "B")).isDraw)
    }

    @Test fun opponentScoreOnlyCountsRevealedQuestions() {
        val s = snap(q = 1)
        val g = DuelSide(G, "B", answers = mapOf(0 to ans(0, true, 0), 1 to ans(1, true, 0)))
        assertEquals(200, DuelMachine.score(g, s, upTo = 1).points)
        assertEquals(400, DuelMachine.score(g, s).points)
    }

    @Test fun staleHeartbeat() {
        val side = DuelSide(G, "B", heartbeatAt = 1_000)
        assertFalse(DuelMachine.isStale(side, 1_000 + DuelMachine.STALE_MS))
        assertTrue(DuelMachine.isStale(side, 1_001 + DuelMachine.STALE_MS))
        assertFalse(DuelMachine.isStale(DuelSide(G, "B"), 99_999_999))
    }

    // ------------------------------------------------------------ deck, codes, links

    private fun q(id: Int, key: Boolean = true, opts: Int = 4) = Question(
        id = id, html = "Q$id", options = (1..opts).map { QuestionOption(it, ('A' + it - 1).toString(), "opt$it", null) },
        correctIds = if (key) listOf(1) else emptyList(),
    )

    @Test fun dealIsDeterministicDeduplicatedAndSkipsUnplayable() {
        val pool = (1..30).map { q(it) } + q(5) + q(99, key = false) + q(100, opts = 1)
        val a = DuelDeck.deal(pool, 10, seed = 42)
        val b = DuelDeck.deal(pool, 10, seed = 42)
        assertEquals(a.map { it.id }, b.map { it.id })
        assertEquals(10, a.map { it.id }.toSet().size)
        assertFalse(a.any { it.id == 99 || it.id == 100 })
        assertEquals(3, DuelDeck.deal(pool.take(3), 10, 1).size)
        assertTrue(DuelDeck.deal(listOf(q(1, key = false)), 5, 1).isEmpty())
    }

    @Test fun codesAndLinks() {
        val code = DuelCodes.newCode()
        assertEquals(6, code.length)
        assertFalse(code.any { it in "01ILO" })
        assertEquals("ABC123", DuelCodes.normalize(" abc-123 "))
        assertEquals(DuelLink.Code("K7Q2PX"), DuelCodes.parseLink("medx://duel/K7Q2PX"))
        assertEquals(DuelLink.Code("K7Q2PX"), DuelCodes.parseLink("https://medx.srihari.quest/duel/k7q2px?x=1"))
        assertEquals(DuelLink.Invite("inv_abc"), DuelCodes.parseLink("medx://duel/invite/inv_abc"))
        assertNull(DuelCodes.parseLink("medx://duel/"))
        assertNull(DuelCodes.parseLink("https://example.com/duel/ABCDEF"))
    }

    @Test fun profileSearchKeysCoverEveryWordPrefix() {
        val keys = PublicProfile.searchKeys("Sri Prabakaran")
        assertTrue("sr" in keys); assertTrue("pra" in keys); assertTrue("prabakaran" in keys); assertTrue("sri prab" in keys)
        assertFalse("s" in keys)
        assertEquals(PublicProfile.emailHash("A@B.com"), PublicProfile.emailHash(" a@b.COM "))
        assertEquals(64, PublicProfile.emailHash("a@b.com").length)
    }

    @Test fun invitesExpire() {
        val inv = DuelInvite("i", "a", "A", null, "b", "B", InviteStatus.PENDING, "CODE", DuelSettings(), 0, DuelInvite.TTL_MS)
        assertTrue(inv.isLive(DuelInvite.TTL_MS - 1))
        assertFalse(inv.isLive(DuelInvite.TTL_MS))
        assertEquals(InviteStatus.EXPIRED, inv.effectiveStatus(DuelInvite.TTL_MS))
        assertEquals(InviteStatus.DECLINED, inv.copy(status = InviteStatus.DECLINED).effectiveStatus(0))
    }

    @Test fun resultDocCarriesBothEloDeltas() {
        val g = DuelGameDoc(
            "CODE", H, G, MatchStatus.LIVE, DuelSettings(), 5, 1, 4, 0, emptyMap(), emptyMap(),
            mapOf(H to 1200, G to 1400), mapOf(H to 30, G to 30), "invite", null, 0, 0, null, null,
        )
        val r = DuelResultDoc.build(DuelOutcome(H, EndReason.COMPLETE, emptyMap()), g)
        assertTrue(r.eloDelta[H]!! > 12)
        assertEquals(-r.eloDelta[H]!!, r.eloDelta[G]!!)
    }

    @Test fun pathFieldsNestWithoutClobberingSiblings() {
        val (fields, mask) = pathFields(mapOf(listOf("names", "u1") to "Sri", listOf("ratings", "u1") to 1200, listOf("status") to "live"))
        assertEquals(listOf(listOf("names", "u1"), listOf("ratings", "u1"), listOf("status")), mask)
        assertEquals("{\"names\":{\"u1\":\"Sri\"},\"ratings\":{\"u1\":1200},\"status\":\"live\"}", fields.toString())
    }
}
