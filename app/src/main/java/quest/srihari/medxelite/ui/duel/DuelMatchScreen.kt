package quest.srihari.medxelite.ui.duel

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import quest.srihari.medxelite.data.Question
import quest.srihari.medxelite.data.ServerClock
import quest.srihari.medxelite.duel.DuelAnswer
import quest.srihari.medxelite.duel.DuelCenter
import quest.srihari.medxelite.duel.DuelError
import quest.srihari.medxelite.duel.DuelMachine
import quest.srihari.medxelite.duel.DuelMatch
import quest.srihari.medxelite.duel.DuelRepo
import quest.srihari.medxelite.duel.DuelScoring
import quest.srihari.medxelite.duel.EndReason
import quest.srihari.medxelite.duel.InviteStatus
import quest.srihari.medxelite.duel.Phase
import quest.srihari.medxelite.duel.PublicProfile
import quest.srihari.medxelite.ui.Navigator
import quest.srihari.medxelite.ui.components.EmptyState
import quest.srihari.medxelite.ui.components.HtmlText
import quest.srihari.medxelite.ui.components.LoadingState
import quest.srihari.medxelite.ui.components.MedxTopBar
import quest.srihari.medxelite.ui.components.Pill
import quest.srihari.medxelite.ui.components.PrimaryButton
import quest.srihari.medxelite.ui.components.ProgressBar
import quest.srihari.medxelite.ui.components.ProgressRing
import quest.srihari.medxelite.ui.components.SectionHeader
import quest.srihari.medxelite.ui.components.TonalButton
import quest.srihari.medxelite.ui.components.softShadow
import quest.srihari.medxelite.ui.runner.Explanation
import quest.srihari.medxelite.ui.runner.OptionButton
import quest.srihari.medxelite.ui.theme.Hues
import quest.srihari.medxelite.ui.theme.Jakarta
import quest.srihari.medxelite.ui.theme.Mx
import quest.srihari.medxelite.ui.theme.onTint
import quest.srihari.medxelite.ui.theme.tint


@Composable
fun DuelMatchScreen(nav: Navigator, code: String) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val match = remember(code) { DuelMatch(code, scope) }
    DisposableEffect(match) {
        match.start()
        onDispose { match.stop() }
    }
    if (match.legacy) {
        LaunchedEffect(Unit) { nav.back(); nav.go("faceoff?code=$code") }
        return
    }
    var confirmLeave by remember { mutableStateOf(false) }
    val inPlay = match.phase in setOf(Phase.LOBBY, Phase.COUNTDOWN, Phase.QUESTION, Phase.ANSWERED, Phase.REVEAL)
    BackHandler { if (inPlay) confirmLeave = true else nav.back() }

    DuelMatchContent(
        match,
        onBack = { if (inPlay) confirmLeave = true else nav.back() },
        onShare = { text -> context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share")) },
        onOpenRoom = { newCode -> nav.back(); nav.go("duel/$newCode") },
        onDone = nav::back,
    )

    if (confirmLeave) {
        val live = match.phase != Phase.LOBBY
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            containerColor = Mx.colors.card,
            title = { Text(if (live) "Forfeit this duel?" else "Leave the lobby?") },
            text = { Text(if (live) "${match.themName} wins and your rating drops. You can also just step away — the duel keeps going and you can rejoin from Duels." else "The room closes for both of you.") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; scope.launch { match.leave(); nav.back() } }) { Text(if (live) "Forfeit" else "Leave", color = Mx.colors.wrong) } },
            dismissButton = {
                Row {
                    if (live) TextButton(onClick = { confirmLeave = false; nav.back() }) { Text("Step away") }
                    TextButton(onClick = { confirmLeave = false }) { Text("Stay") }
                }
            },
        )
    }
}

@Composable
fun DuelMatchContent(m: DuelMatch, onBack: () -> Unit, onShare: (String) -> Unit, onOpenRoom: (String) -> Unit, onDone: () -> Unit) {
    val c = Mx.colors
    val title = when (m.phase) {
        Phase.LOBBY -> "Duel lobby"
        Phase.FINISHED -> "Duel result"
        else -> "Duel"
    }
    Scaffold(
        containerColor = c.groupedBackground,
        topBar = {
            MedxTopBar(title, onBack = onBack, actions = {
                if (m.phase == Phase.LOBBY) {
                    IconButton(onClick = { onShare(DuelCenter.shareText(m.code, m.game?.settings)) }) { Icon(Icons.Rounded.Share, "Share room code") }
                }
            })
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                m.notFound -> EmptyState("Duel not found", "The code may be wrong, or the room was closed.", actionTitle = "Back", onAction = onDone)
                else -> when (m.phase) {
                    Phase.LOADING -> LoadingState("Opening the duel")
                    Phase.LOBBY -> Lobby(m, onShare)
                    Phase.COUNTDOWN -> Countdown(m)
                    Phase.QUESTION, Phase.ANSWERED, Phase.REVEAL -> Live(m)
                    Phase.FINISHED -> Results(m, onShare, onOpenRoom, onDone)
                    Phase.CANCELLED -> {
                        val inv = DuelCenter.outgoing.collectAsState().value.firstOrNull { it.gameId == m.code }
                        val (t, sub) = when (inv?.effectiveStatus(m.now)) {
                            InviteStatus.DECLINED -> "${inv.toName} declined" to "Maybe later — try a quick match or challenge someone else."
                            InviteStatus.EXPIRED -> "Invite expired" to "${inv.toName} didn't answer within 10 minutes."
                            InviteStatus.CANCELLED -> "Invite cancelled" to "This room is closed."
                            else -> "Duel closed" to "This room was closed before the duel started."
                        }
                        EmptyState(t, sub, icon = Icons.Rounded.DoNotDisturbOn, actionTitle = "Back to Duels", onAction = onDone)
                    }
                }
            }
            Column(Modifier.align(Alignment.TopCenter).padding(top = 4.dp)) {
                AnimatedVisibility(m.offline) { Banner("You're offline — reconnecting…", c.wrong) }
                m.notice?.let { Banner(it, c.flagged) }
            }
        }
    }
}

@Composable
private fun Banner(text: String, hue: Color) {
    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 4.dp).softShadow(RoundedCornerShape(50), 6.dp).clip(RoundedCornerShape(50)).background(Mx.colors.card)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.WifiOff, null, tint = hue, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Mx.colors.ink)
    }
}

// ================================================================ lobby + countdown

@Composable
private fun SeatCard(name: String, photo: String?, hue: Color, status: String, ok: Boolean, empty: Boolean, modifier: Modifier, avatarName: String = name) {
    val c = Mx.colors
    Column(
        modifier.softShadow(RoundedCornerShape(24.dp), 8.dp).clip(RoundedCornerShape(24.dp)).background(c.card).padding(vertical = 20.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (empty) {
            Box(Modifier.size(64.dp).clip(CircleShape).border(2.dp, c.separator, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PersonOutline, null, tint = c.tertiaryText, modifier = Modifier.size(30.dp))
            }
        } else {
            PlayerAvatar(avatarName, photo, 64.dp, hue, ring = ok)
        }
        Spacer(Modifier.height(10.dp))
        Text(name, fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = if (empty) c.tertiaryText else c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (ok) Icon(Icons.Rounded.CheckCircle, null, tint = c.correct, modifier = Modifier.size(14.dp).padding(end = 2.dp))
            Text(status, style = MaterialTheme.typography.labelMedium, color = if (ok) c.correct else c.secondaryText)
        }
    }
}

@Composable
private fun VsBadge(size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Mx.colors.ink), contentAlignment = Alignment.Center) {
        Text("VS", color = Mx.colors.groupedBackground, fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.33f).sp)
    }
}

@Composable
private fun Lobby(m: DuelMatch, onShare: (String) -> Unit) {
    val c = Mx.colors
    val g = m.game ?: return
    val scope = rememberCoroutineScope()
    val outgoing by DuelCenter.outgoing.collectAsState()
    val invite = outgoing.firstOrNull { it.gameId == m.code }
    val them = m.them
    val now = m.now
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(48.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SeatCard("You", g.photos[m.uid]?.ifBlank { null }, DuelHues.me(), if (m.me?.ready == true) "Ready" else "Joining…", m.me?.ready == true, false, Modifier.weight(1f), avatarName = m.myName)
            Box(Modifier.padding(horizontal = 10.dp)) { VsBadge() }
            val theirStatus = when {
                them == null -> if (invite != null) "Invited" else "Open seat"
                them.left -> "Left"
                them.ready -> "Ready"
                else -> "Opening…"
            }
            SeatCard(if (them == null && invite == null) "Waiting…" else m.themName.takeIf { them != null } ?: invite?.toName ?: "Waiting…", g.photos[m.themUid ?: ""]?.ifBlank { null }, DuelHues.them, theirStatus, them?.ready == true, them == null && invite == null, Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(g.settings.subjectName, Hues.forName(g.settings.subjectName))
            Pill("${g.total} questions", Hues.blue)
            Pill("${g.settings.perQuestion}s each", Hues.orange)
        }
        Spacer(Modifier.height(24.dp))
        when {
            invite != null && them == null -> when (invite.effectiveStatus(now)) {
                InviteStatus.PENDING -> {
                    StatusLine("Invite sent to ${invite.toName}", "Expires in ${mmss(invite.expiresAt - now)} · starts the moment they accept")
                    Spacer(Modifier.height(16.dp))
                    TonalButton("Cancel invite", { scope.launch { runCatching { DuelRepo.cancelInvite(invite.id) }; runCatching { DuelCenter.refresh() } } }, icon = Icons.Rounded.Close)
                }
                InviteStatus.DECLINED -> StatusLine("${invite.toName} declined", "Try a quick match or challenge someone else.")
                InviteStatus.EXPIRED -> StatusLine("Invite expired", "${invite.toName} didn't answer in time. Send a new one from Duels.")
                InviteStatus.CANCELLED -> StatusLine("Invite cancelled", "This room is closing.")
                InviteStatus.ACCEPTED -> StatusLine("${invite.toName} accepted!", "Joining…")
            }
            them == null -> {
                Text("Room code", style = MaterialTheme.typography.labelLarge, color = c.secondaryText)
                Spacer(Modifier.height(8.dp))
                Text(
                    m.code, fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, letterSpacing = 8.sp, color = c.onAccentContainer,
                    modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(c.accentContainer).padding(horizontal = 24.dp, vertical = 10.dp),
                )
                Spacer(Modifier.height(14.dp))
                PrimaryButton("Share code", { onShare(DuelCenter.shareText(m.code, g.settings)) }, icon = Icons.Rounded.Share)
                Spacer(Modifier.height(12.dp))
                StatusLine("Waiting for someone to join", "Friends can tap Join by code in Duels, or open your link.")
            }
            them.left -> StatusLine("${m.themName} left the lobby", "You can leave and start a new duel.")
            !them.ready -> StatusLine("${m.themName} is in!", "Waiting for them to open the duel…")
            else -> StatusLine("Both ready", "Starting…")
        }
    }
}

@Composable
private fun StatusLine(title: String, sub: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Mx.colors.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(2.dp))
        Text(sub, style = MaterialTheme.typography.bodyMedium, color = Mx.colors.secondaryText, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Countdown(m: DuelMatch) {
    val c = Mx.colors
    val g = m.game ?: return
    val first = m.qIndex == 0
    val total = if (first) DuelMachine.START_COUNTDOWN_MS else DuelMachine.NEXT_GAP_MS
    val leftMs = ((g.askedAt ?: m.now) - m.now).coerceAtLeast(0)
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (first) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerAvatar(m.myName, g.photos[m.uid], 72.dp, DuelHues.me(), ring = true)
                Box(Modifier.padding(horizontal = 16.dp)) { VsBadge(44.dp) }
                PlayerAvatar(m.themName, g.photos[m.themUid ?: ""], 72.dp, DuelHues.them, ring = true)
            }
            Spacer(Modifier.height(10.dp))
            Text("${m.myName} vs ${m.themName}", style = MaterialTheme.typography.titleMedium, color = c.ink)
            Spacer(Modifier.height(32.dp))
        } else {
            ScoreHeader(m)
            Spacer(Modifier.height(32.dp))
        }
        ProgressRing(leftMs / total.toFloat(), 168.dp, 12.dp, c.accent, c.accentContainer) {
            Text("${m.countdown.coerceAtLeast(1)}", fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 72.sp, color = c.ink)
        }
        Spacer(Modifier.height(24.dp))
        Text(if (first) "Get ready" else "Next question", style = MaterialTheme.typography.headlineSmall, color = c.ink)
        Text("Question ${m.qIndex + 1} of ${g.total} · ${g.settings.perQuestion}s on the clock", style = MaterialTheme.typography.bodyMedium, color = c.secondaryText)
    }
}

// ================================================================ live

@Composable
private fun ScoreHeader(m: DuelMatch) {
    val c = Mx.colors
    val g = m.game ?: return
    val reveal = m.phase == Phase.REVEAL || m.phase == Phase.FINISHED
    // Points only count questions already revealed, so the opponent's score never leaks their answer early.
    val upTo = if (reveal) m.qIndex + 1 else m.qIndex
    val mine = m.me?.let { DuelMachine.score(it, g.snapshot, upTo).points } ?: 0
    val theirs = m.them?.let { DuelMachine.score(it, g.snapshot, upTo).points } ?: 0
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).softShadow(RoundedCornerShape(24.dp), 8.dp).clip(RoundedCornerShape(24.dp)).background(c.card).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(m.myName, g.photos[m.uid], 36.dp, DuelHues.me())
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("You", style = MaterialTheme.typography.labelMedium, color = c.secondaryText)
                Text("$mine", fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = DuelHues.me())
            }
            VsBadge(30.dp)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(m.themName, style = MaterialTheme.typography.labelMedium, color = c.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$theirs", fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = onTint(DuelHues.them))
            }
            Spacer(Modifier.width(8.dp))
            PlayerAvatar(m.themName, g.photos[m.themUid ?: ""]?.ifBlank { null }, 36.dp, DuelHues.them)
        }
        Spacer(Modifier.height(10.dp))
        val revealed: (Int) -> Boolean = { i -> i < m.qIndex || (i == m.qIndex && reveal) }
        QuestionDots(g.total, (m.me?.answers ?: emptyMap()) + listOfNotNull(m.pendingPick).associateBy { it.q }, m.qIndex, revealed, DuelHues.me())
        Spacer(Modifier.height(5.dp))
        QuestionDots(g.total, m.them?.answers ?: emptyMap(), m.qIndex, revealed, DuelHues.them)
    }
}

@Composable
private fun Live(m: DuelMatch) {
    val c = Mx.colors
    val g = m.game ?: return
    val q = m.question
    if (q == null) { LoadingState("Loading questions"); return }
    val reveal = m.phase == Phase.REVEAL
    val mine = m.myAnswer
    val theirs = m.theirAnswer
    val left = m.remainingMs
    val urgent = left <= 5_000 && !reveal
    Column(Modifier.fillMaxSize()) {
        ScoreHeader(m)
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Question ${m.qIndex + 1} of ${g.total}", style = MaterialTheme.typography.labelLarge, color = c.secondaryText, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.Timer, null, tint = if (urgent) c.wrong else c.secondaryText, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (reveal) "0:00" else mmss(left), style = MaterialTheme.typography.labelLarge, color = if (urgent) c.wrong else c.ink)
        }
        ProgressBar(if (reveal) 0f else left / (g.settings.perQuestion * 1000f), Modifier.padding(horizontal = 24.dp), color = if (urgent) c.wrong else c.accent, height = 6.dp)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp)) {
            HtmlText(q.displayText, sizeSp = 18f, bold = true, color = c.ink, extraImages = q.images ?: emptyList())
            Spacer(Modifier.height(16.dp))
            q.options.forEachIndexed { i, o ->
                OptionButton(o, i, chosen = mine?.optionId == o.id, correct = q.isCorrect(o.id), revealed = reveal, locked = m.phase != Phase.QUESTION) { m.answer(o.id) }
                if (reveal && theirs?.optionId == o.id) {
                    Row(Modifier.padding(start = 14.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        PlayerAvatar(m.themName, null, 18.dp, DuelHues.them)
                        Spacer(Modifier.width(6.dp))
                        Text("${m.themName}'s pick", style = MaterialTheme.typography.labelSmall, color = onTint(DuelHues.them))
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (reveal) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PointsChip("You", mine, g.settings.perQuestion, DuelHues.me())
                    PointsChip(m.themName, theirs, g.settings.perQuestion, DuelHues.them)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(c.card).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    when (m.phase) {
                        Phase.QUESTION -> "Faster = more points"
                        Phase.ANSWERED -> "Locked in"
                        else -> if (m.qIndex + 1 >= g.total) "Final results coming…" else "Next question coming…"
                    },
                    style = MaterialTheme.typography.labelLarge, color = c.ink,
                )
                if (m.canClaimWin) {
                    Text(
                        "Claim the win", color = c.accent, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { m.claimWin() }.padding(vertical = 4.dp),
                    )
                }
            }
            OpponentStatus(m.themName, theirs != null, m.opponentStale)
        }
    }
}

@Composable
private fun PointsChip(name: String, a: DuelAnswer?, perQuestion: Int, hue: Color) {
    val c = Mx.colors
    val pts = a?.points(perQuestion) ?: 0
    val text = when {
        a == null || a.timedOut -> "$name · time's up"
        a.correct -> "$name +$pts · ${"%.1f".format(a.ms / 1000.0)}s"
        else -> "$name · wrong"
    }
    Text(
        text, style = MaterialTheme.typography.labelMedium, color = if (pts > 0) onTint(hue) else c.secondaryText, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (pts > 0) tint(hue) else c.sunken).padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

// ================================================================ results

@Composable
private fun Results(m: DuelMatch, onShare: (String) -> Unit, onOpenRoom: (String) -> Unit, onDone: () -> Unit) {
    val c = Mx.colors
    val g = m.game ?: return
    val r = g.result
    val scope = rememberCoroutineScope()
    val incoming by DuelCenter.incoming.collectAsState()
    var expanded by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val themUid = m.themUid
    val won = r?.winner == m.uid
    val draw = r?.winner == null
    val hue = when { draw -> Hues.gray; won -> Hues.yellow; else -> DuelHues.them }
    val myScore = m.me?.let { DuelMachine.score(it, g.snapshot) }
    val theirScore = m.them?.let { DuelMachine.score(it, g.snapshot) }
    val delta = r?.eloDelta?.get(m.uid)
    val rematchFromThem = incoming.firstOrNull { it.rematchOf == g.code && it.fromUid == themUid && it.isLive(ServerClock.now()) }
    val reason = when (r?.reason) {
        EndReason.FORFEIT -> if (won) "${m.themName} left the duel." else "You left the duel."
        EndReason.ABANDONED -> if (won) "${m.themName} lost connection." else "You lost connection."
        else -> null
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(96.dp).clip(CircleShape).background(tint(hue)), contentAlignment = Alignment.Center) {
                    Icon(if (draw) Icons.Rounded.Handshake else if (won) Icons.Rounded.EmojiEvents else Icons.Rounded.SportsMartialArts, null, tint = onTint(hue), modifier = Modifier.size(52.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text(if (draw) "It's a draw" else if (won) "You won!" else "${m.themName} won", style = MaterialTheme.typography.headlineLarge, color = c.ink)
                reason?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.secondaryText) }
                if (delta != null) {
                    Spacer(Modifier.height(10.dp))
                    val newRating = m.recorded?.rating ?: ((g.ratings[m.uid] ?: 1200) + delta)
                    Pill("${if (delta >= 0) "+" else ""}$delta rating · now %,d".format(newRating), if (delta >= 0) c.correct else c.wrong)
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ResultCard("You", m.myName, g.photos[m.uid], myScore?.points ?: 0, myScore?.correct ?: 0, g.total, m.me, DuelHues.me(), r?.winner == m.uid, Modifier.weight(1f))
                ResultCard(m.themName, m.themName, g.photos[themUid ?: ""], theirScore?.points ?: 0, theirScore?.correct ?: 0, g.total, m.them, DuelHues.them, r?.winner == themUid, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    if (rematchFromThem != null) "Accept rematch" else "Rematch",
                    {
                        busy = true; err = null
                        scope.launch {
                            try {
                                val code = if (rematchFromThem != null) DuelRepo.acceptInvite(rematchFromThem.id)
                                else DuelRepo.sendInvite(PublicProfile(themUid ?: error("no opponent"), m.themName), g.settings, rematchOf = g.code).gameId
                                runCatching { DuelCenter.refresh() }
                                onOpenRoom(code)
                            } catch (e: DuelError) { err = e.message } catch (e: Exception) { err = "Couldn't start a rematch. Check your connection." }
                            busy = false
                        }
                    },
                    loading = busy, enabled = !busy && themUid != null, icon = Icons.Rounded.Replay, modifier = Modifier.weight(1f),
                )
                TonalButton("Share", {
                    val verdict = if (draw) "drew with" else if (won) "beat" else "lost to"
                    onShare("I $verdict ${m.themName} ${myScore?.points ?: 0}–${theirScore?.points ?: 0} in a MedX Elite duel (${g.settings.label}). Think you can do better? Duel me in the app.")
                }, icon = Icons.Rounded.Share, height = 56.dp, modifier = Modifier.weight(0.7f))
            }
            err?.let { Text(it, color = c.wrong, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 24.dp)) }
        }
        item { SectionHeader("Question by question", Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp)) }
        itemsIndexed(m.deck, key = { i, _ -> "q$i" }) { i, q ->
            BreakdownRow(i, q, m.me?.answers?.get(i), m.them?.answers?.get(i), m.themName, g.settings.perQuestion, expanded == i) { expanded = if (expanded == i) null else i }
        }
        item {
            Box(Modifier.fillMaxWidth().padding(20.dp)) { TonalButton("Back to Duels", onDone, modifier = Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun ResultCard(name: String, avatarName: String, photo: String?, points: Int, correct: Int, total: Int, side: quest.srihari.medxelite.duel.DuelSide?, hue: Color, winner: Boolean, modifier: Modifier) {
    val c = Mx.colors
    val right = side?.answers?.values?.filter { it.correct && !it.timedOut }.orEmpty()
    val avg = if (right.isEmpty()) null else right.map { it.ms }.average() / 1000.0
    Column(
        modifier.softShadow(RoundedCornerShape(24.dp), 8.dp).clip(RoundedCornerShape(24.dp)).background(c.card)
            .then(if (winner) Modifier.border(2.dp, Hues.yellow, RoundedCornerShape(24.dp)) else Modifier).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PlayerAvatar(avatarName, photo?.ifBlank { null }, 44.dp, hue)
        Spacer(Modifier.height(8.dp))
        Text(name, style = MaterialTheme.typography.labelLarge, color = c.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("%,d".format(points), fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, color = c.ink)
        Text("$correct / $total correct", style = MaterialTheme.typography.labelMedium, color = c.secondaryText)
        Text(avg?.let { "avg %.1fs when right".format(it) } ?: "no right answers", style = MaterialTheme.typography.labelSmall, color = c.tertiaryText)
    }
}

@Composable
private fun AnswerTag(a: DuelAnswer?, perQuestion: Int, hue: Color) {
    val c = Mx.colors
    val (icon, color, text) = when {
        a == null || a.timedOut -> Triple(Icons.Rounded.HourglassEmpty, c.tertiaryText, "—")
        a.correct -> Triple(Icons.Rounded.CheckCircle, c.correct, "+${a.points(perQuestion)}")
        else -> Triple(Icons.Rounded.Cancel, c.wrong, "0")
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(64.dp)) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(hue))
        Spacer(Modifier.width(4.dp))
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(3.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = c.ink)
    }
}

@Composable
private fun BreakdownRow(i: Int, q: Question, mine: DuelAnswer?, theirs: DuelAnswer?, themName: String, perQuestion: Int, open: Boolean, onToggle: () -> Unit) {
    val c = Mx.colors
    val plain = (q.plain ?: q.displayText).replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
    Column(Modifier.padding(horizontal = 20.dp, vertical = 5.dp).fillMaxWidth().softShadow(RoundedCornerShape(20.dp), 4.dp).clip(RoundedCornerShape(20.dp)).background(c.card)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Q${i + 1}", fontFamily = Jakarta, fontWeight = FontWeight.Bold, color = c.secondaryText, modifier = Modifier.width(34.dp))
            Text(plain, style = MaterialTheme.typography.bodySmall, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Column {
                AnswerTag(mine, perQuestion, DuelHues.me())
                AnswerTag(theirs, perQuestion, DuelHues.them)
            }
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = c.tertiaryText)
        }
        if (open) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                HtmlText(q.displayText, sizeSp = 16f, bold = true, color = c.ink)
                Spacer(Modifier.height(10.dp))
                q.options.forEachIndexed { idx, o ->
                    OptionButton(o, idx, chosen = mine?.optionId == o.id, correct = q.isCorrect(o.id), revealed = true, locked = true, nested = true) {}
                    if (theirs?.optionId == o.id) {
                        Text("↑ $themName's pick", style = MaterialTheme.typography.labelSmall, color = onTint(DuelHues.them), modifier = Modifier.padding(start = 14.dp, top = 2.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    "You: ${describe(mine, perQuestion)} · $themName: ${describe(theirs, perQuestion)}",
                    style = MaterialTheme.typography.labelMedium, color = c.secondaryText,
                )
                Spacer(Modifier.height(10.dp))
                Explanation(q)
            }
        }
    }
}

private fun describe(a: DuelAnswer?, perQuestion: Int) = when {
    a == null || a.timedOut -> "no answer"
    a.correct -> "right in %.1fs (+%d)".format(a.ms / 1000.0, a.points(perQuestion))
    else -> "wrong in %.1fs".format(a.ms / 1000.0)
}


