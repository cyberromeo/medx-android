package quest.srihari.medxelite.ui.duel

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import quest.srihari.medxelite.Medx
import quest.srihari.medxelite.data.ServerClock
import quest.srihari.medxelite.duel.DuelCenter
import quest.srihari.medxelite.duel.DuelError
import quest.srihari.medxelite.duel.DuelRepo
import quest.srihari.medxelite.duel.DuelSettings
import quest.srihari.medxelite.duel.MatchStatus
import quest.srihari.medxelite.ui.Navigator
import quest.srihari.medxelite.ui.components.EmptyState
import quest.srihari.medxelite.ui.components.MedxTopBar
import quest.srihari.medxelite.ui.components.Pill
import quest.srihari.medxelite.ui.components.TonalButton
import quest.srihari.medxelite.ui.theme.Hues
import quest.srihari.medxelite.ui.theme.Mx

private enum class QStage { DEALING, SEARCHING, TIMEOUT, ERROR }

/**
 * Quick match: deal my own room, then each 1.5 s (a) check whether someone claimed me, (b) try to
 * claim the closest-rated waiting player (a transaction, see DuelRepo.claim), (c) otherwise sit in
 * the queue with a heartbeat. Gives up after 60 s; Cancel leaves the queue atomically (and if I was
 * matched in that instant, goes to the match instead).
 */
@Composable
fun QuickMatchScreen(nav: Navigator) {
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf(QStage.DEALING) }
    var error by remember { mutableStateOf<String?>(null) }
    var started by remember { mutableLongStateOf(ServerClock.now()) }
    var now by remember { mutableLongStateOf(ServerClock.now()) }
    var myCode by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }

    fun found(code: String) {
        if (done) return
        done = true
        Medx.scope.launch {
            DuelRepo.clearQueueEntry()
            myCode?.let { if (it != code) DuelRepo.abandonLobby(it) }
        }
        nav.back(); nav.go("duel/$code")
    }

    fun cancel() {
        if (done) { nav.back(); return }
        done = true
        Medx.scope.launch {
            val late = runCatching { DuelRepo.leaveQueue() }.getOrNull()
            if (late != null) {
                DuelRepo.clearQueueEntry()
                DuelCenter.openRoom.value = late // matched at the last moment: still play it
            } else myCode?.let { DuelRepo.abandonLobby(it) }
        }
        nav.back()
    }

    BackHandler { cancel() }
    DisposableEffect(Unit) { onDispose { if (!done) { done = true; Medx.scope.launch { runCatching { DuelRepo.leaveQueue() }; myCode?.let { DuelRepo.abandonLobby(it) } } } } }
    LaunchedEffect(Unit) { while (true) { now = ServerClock.now(); delay(500) } }

    LaunchedEffect(attempt) {
        error = null
        done = false
        try {
            stage = QStage.DEALING
            if (DuelCenter.active.value.any { it.status == MatchStatus.LIVE } || DuelCenter.inMatch.value != null) {
                throw DuelError("You're already in a duel. Finish or forfeit it first.")
            }
            val code = DuelRepo.createLobby(DuelSettings.QUICK, "quick", DuelRepo.dealQuestions(DuelSettings.QUICK))
            myCode = code
            val rating = DuelRepo.stats().rating
            stage = QStage.SEARCHING
            started = ServerClock.now()
            var enqueued = false
            var lastBeat = 0L
            while (!done && ServerClock.now() - started < 60_000) {
                DuelRepo.myQueueEntry()?.takeIf { it.status == "matched" }?.gameId?.let { found(it); return@LaunchedEffect }
                for (cand in DuelRepo.queueCandidates(rating).take(3)) {
                    val claimed = runCatching { DuelRepo.claim(cand) }.getOrNull()
                    if (claimed != null) { found(claimed); return@LaunchedEffect }
                }
                val t = ServerClock.now()
                if (!enqueued) { DuelRepo.enqueue(code); enqueued = true; lastBeat = t }
                else if (t - lastBeat > 5_000) { runCatching { DuelRepo.queueHeartbeat() }; lastBeat = t }
                delay(1_500)
            }
            if (done) return@LaunchedEffect
            val late = DuelRepo.leaveQueue()
            if (late != null) { found(late); return@LaunchedEffect }
            DuelRepo.abandonLobby(code)
            stage = QStage.TIMEOUT
        } catch (e: DuelError) {
            error = e.message; stage = QStage.ERROR
        } catch (e: Exception) {
            if (!done) { error = "Matchmaking failed. Check your connection."; stage = QStage.ERROR }
        }
    }

    Scaffold(containerColor = Mx.colors.groupedBackground, topBar = { MedxTopBar("Quick match", onBack = ::cancel) }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
            when (stage) {
                QStage.DEALING, QStage.SEARCHING -> Searching(stage == QStage.DEALING, if (stage == QStage.SEARCHING) now - started else 0L, ::cancel)
                QStage.TIMEOUT -> EmptyState(
                    "No one's around right now", "Nobody else was searching in the last minute. Try again, or challenge a friend directly.",
                    icon = Icons.Rounded.PersonSearch, actionTitle = "Search again", onAction = { attempt++ },
                )
                QStage.ERROR -> EmptyState("Couldn't start a quick match", error, actionTitle = "Try again", onAction = { attempt++ })
            }
        }
    }
}

@Composable
fun Searching(dealing: Boolean, elapsedMs: Long, onCancel: () -> Unit) {
    val c = Mx.colors
    val name = Medx.auth.current?.name ?: "You"
    val t = rememberInfiniteTransition(label = "radar")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "p")
    val phase = if (Mx.reduceMotion) 0.5f else p
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
            val accent = c.accent
            Canvas(Modifier.fillMaxSize()) {
                for (k in 0 until 3) {
                    val f = (phase + k / 3f) % 1f
                    drawCircle(accent.copy(alpha = (1f - f) * 0.35f), radius = size.minDimension / 2 * (0.35f + 0.65f * f), style = Stroke(width = 3.dp.toPx()))
                }
                drawCircle(accent.copy(alpha = 0.08f), radius = size.minDimension / 2 * 0.42f)
            }
            PlayerAvatar(name, Medx.auth.current?.photoUrl, 84.dp, c.accent, ring = true)
        }
        Spacer(Modifier.height(16.dp))
        Text(if (dealing) "Dealing your questions…" else "Finding an opponent…", style = MaterialTheme.typography.headlineSmall, color = c.ink)
        Spacer(Modifier.height(4.dp))
        Text(
            if (dealing) "Mixed subjects from the QBank" else "Searching · ${elapsedMs / 1000}s of 60s",
            style = MaterialTheme.typography.bodyMedium, color = c.secondaryText, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("Mixed", Hues.purple)
            Pill("${DuelSettings.QUICK.count} Qs", Hues.blue)
            Pill("${DuelSettings.QUICK.perQuestion}s each", Hues.orange)
        }
        Spacer(Modifier.height(28.dp))
        TonalButton("Cancel", onCancel, icon = Icons.Rounded.Close)
    }
}
