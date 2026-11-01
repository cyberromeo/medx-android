package quest.srihari.medxelite.ui.faceoff

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import quest.srihari.medxelite.ui.components.DotLoader
import quest.srihari.medxelite.ui.components.DotText
import quest.srihari.medxelite.ui.components.NLabel
import quest.srihari.medxelite.ui.components.NPill
import quest.srihari.medxelite.ui.components.NSegmented
import quest.srihari.medxelite.ui.components.NWidget
import quest.srihari.medxelite.ui.components.RecDot
import quest.srihari.medxelite.ui.components.WidgetHeader
import quest.srihari.medxelite.ui.components.WidgetShape
import quest.srihari.medxelite.ui.theme.NType
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import quest.srihari.medxelite.Medx
import quest.srihari.medxelite.data.Catalog
import quest.srihari.medxelite.data.Load
import quest.srihari.medxelite.data.SeriesPaper
import quest.srihari.medxelite.ui.Navigator
import quest.srihari.medxelite.ui.components.Card
import quest.srihari.medxelite.ui.components.EmptyState
import quest.srihari.medxelite.ui.components.GroupedSection
import quest.srihari.medxelite.ui.components.HtmlText
import quest.srihari.medxelite.ui.components.LoadingState
import quest.srihari.medxelite.ui.components.MedxRow
import quest.srihari.medxelite.ui.components.MedxTopBar
import quest.srihari.medxelite.ui.runner.Explanation
import quest.srihari.medxelite.ui.runner.OptionButton
import quest.srihari.medxelite.ui.theme.Hues
import quest.srihari.medxelite.ui.theme.Mx

/** Faceoff lobby: create a room (pick a paper and a length) or join one by code. */
@Composable
fun FaceoffScreen(nav: Navigator) {
    var roomId by rememberSaveable { mutableStateOf<String?>(null) }
    val id = roomId
    if (id != null) {
        DuelRoomScreen(id) { roomId = null }
        return
    }
    val series by Catalog.series.collectAsState()
    var code by rememberSaveable { mutableStateOf("") }
    var length by rememberSaveable { mutableStateOf(10) }
    var pick by remember { mutableStateOf<SeriesPaper?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { Catalog.loadSeries() }

    fun join() {
        val c = code.trim().uppercase()
        if (c.length < 4) { error = "Enter the room code your friend shared."; return }
        busy = "join"; error = null
        scope.launch {
            try {
                val doc = Medx.fs.fetchDocument(DuelPath.GAMES, c, useCache = false)
                val game = doc?.let { DuelGame.from(c, it) }
                when {
                    game == null -> error = "No room with code $c."
                    game.status == DuelStatus.DONE || game.status == DuelStatus.ABANDONED -> error = "That room has closed."
                    game.status == DuelStatus.LIVE && game.guestUid != Medx.auth.uid && game.hostUid != Medx.auth.uid -> error = "That game has already started."
                    else -> {
                        if (game.hostUid != Medx.auth.uid) {
                            val s = Medx.auth.current!!
                            Medx.fs.writeDocument(DuelPath.PLAYERS, DuelPath.playerDoc(c, s.uid), DuelPlayer.blank(c, s.uid, s.firstName))
                        }
                        roomId = c
                    }
                }
            } catch (e: Exception) {
                error = "Couldn't join: check the code and your connection."
            } finally {
                busy = null
            }
        }
    }

    Scaffold(containerColor = Mx.colors.groupedBackground, topBar = { MedxTopBar("Faceoff", onBack = nav::back) }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                NWidget(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
                    WidgetHeader("1 v 1 · live")
                    Spacer(Modifier.height(10.dp))
                    Spacer(Modifier.height(8.dp))
                    quest.srihari.medxelite.ui.components.DotValue("60", "sec / question", size = 72.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Duel any MedX friend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Same questions, same 60-second clock. A right answer scores 40 plus the seconds left. Create a room and share its code, or join one.",
                        color = Mx.colors.secondaryText, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    NLabel("Join a room", Modifier.padding(start = 6.dp, bottom = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            code, { code = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(8) },
                            placeholder = { Text("CODE", style = MaterialTheme.typography.titleMedium.copy(fontFamily = NType.mono, letterSpacing = 4.sp)) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = NType.mono, fontWeight = FontWeight.Bold, letterSpacing = 4.sp),
                            shape = CircleShape,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Mx.colors.ink, unfocusedBorderColor = Mx.colors.separator, cursorColor = Mx.colors.accent),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        NPill(if (busy == "join") "Joining…" else "Join", ::join, enabled = busy == null, modifier = Modifier.height(56.dp))
                    }
                    error?.let {
                        Text(it, color = Mx.colors.wrong, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 6.dp, top = 8.dp))
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    NLabel("Create a room · length", Modifier.padding(start = 6.dp, bottom = 8.dp))
                    NSegmented(DuelRules.LENGTHS, length, { l -> if (l == 0) "All" else "$l q" }, { length = it })
                }
            }
            when (val s = series) {
                is Load.Loading -> item { LoadingState("Loading papers") }
                is Load.Failed -> item { EmptyState("Couldn't load papers", s.message) }
                is Load.Ready -> item {
                    GroupedSection("Pick a paper to deal from") {
                        val papers = s.value.sortedByDescending { it.startAt }.take(60)
                        papers.forEachIndexed { i, p ->
                            MedxRow(p.title, "${p.group.label} · ${p.questions} questions", icon = Icons.Outlined.EmojiEvents, iconTint = Mx.colors.ink, divider = i < papers.size - 1) { pick = p }
                        }
                    }
                }
            }
        }
    }

    pick?.let { p ->
        AlertDialog(
            onDismissRequest = { if (busy == null) pick = null },
            title = { Text("Deal from ${p.title}?") },
            text = { Text(if (busy == "create") "Dealing the deck…" else "${if (length == 0) p.questions else minOf(length, p.questions)} questions, shuffled. You'll get a code to share.") },
            confirmButton = {
                TextButton(enabled = busy == null, onClick = {
                    busy = "create"
                    scope.launch {
                        try {
                            val all = Medx.fs.fetchTestQuestions(p.id).filter { it.options.isNotEmpty() && it.hasAnswerKey }.shuffled()
                            val dealt = if (length == 0) all else all.take(length)
                            if (dealt.isEmpty()) error("empty")
                            roomId = createDuelRoom(dealt, "series", p.id, p.title, p.title)
                            pick = null
                        } catch (e: Exception) {
                            error = "Couldn't create the room. Check your connection."
                            pick = null
                        } finally {
                            busy = null
                        }
                    }
                }) { Text("Create room") }
            },
            dismissButton = { TextButton(enabled = busy == null, onClick = { pick = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DuelRoomScreen(gameId: String, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val room = remember(gameId) { DuelRoom(gameId, scope) }
    var confirmLeave by remember { mutableStateOf(false) }
    DisposableEffect(room) {
        room.start()
        onDispose { room.stop() }
    }
    val inPlay = room.phase in setOf(DuelPhase.LOBBY, DuelPhase.ARMING, DuelPhase.ASKED, DuelPhase.WAITING, DuelPhase.REVEAL)
    BackHandler { if (inPlay) confirmLeave = true else onClose() }

    Scaffold(
        containerColor = Mx.colors.groupedBackground,
        topBar = {
            MedxTopBar("Room $gameId", onBack = { if (inPlay) confirmLeave = true else onClose() }, actions = {
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Faceoff me on MedX Elite — room code $gameId")
                    }
                    context.startActivity(Intent.createChooser(send, "Share room code"))
                }) { Icon(Icons.Outlined.Share, null); Spacer(Modifier.width(4.dp)); Text("Share") }
            })
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (room.notFound) {
                EmptyState("Room not found", "The code may be wrong, or the room was closed.", actionTitle = "Back", onAction = onClose)
                return@Box
            }
            when (room.phase) {
                DuelPhase.LOADING -> LoadingState("Opening the room")
                DuelPhase.LOBBY -> Lobby(room)
                DuelPhase.ARMING -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    NLabel("Question ${room.qIndex + 1}")
                    DotText("${room.armingIn}", size = 140.sp, color = Mx.colors.accent)
                    Scoreboard(room)
                }
                DuelPhase.ASKED, DuelPhase.WAITING, DuelPhase.REVEAL -> Round(room)
                DuelPhase.DONE -> Result(room, onClose)
                DuelPhase.ABANDONED -> EmptyState("Game abandoned", "The host left before the end.", actionTitle = "Back", onAction = onClose)
            }
            room.failure?.let {
                Text(it, color = Mx.colors.wrong, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave the room?") },
            text = { Text(if (room.isHost) "Leaving closes the room for both of you." else "Your opponent will see that you left.") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; scope.launch { room.leave(); onClose() } }) { Text("Leave", color = Mx.colors.wrong) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Stay") } },
        )
    }
}

@Composable
private fun Lobby(room: DuelRoom) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) { RecDot(); Spacer(Modifier.width(8.dp)); NLabel("Room code") }
        Spacer(Modifier.height(8.dp))
        DotText(room.gameId, size = 56.sp)
        Text(room.sourceLabel(), color = Mx.colors.secondaryText, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        val other = room.theirs
        if (room.isHost) {
            if (other == null || other.left) {
                DotLoader()
                Spacer(Modifier.height(14.dp))
                Text("Waiting for a friend to join with this code…", textAlign = TextAlign.Center)
            } else {
                Text("${other.profile.ifBlank { "Your friend" }} joined", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                NPill("Start the duel", { room.startGame() }, Modifier.fillMaxWidth(), accent = true)
            }
        } else {
            if (room.mine == null) {
                NPill("Join ${room.hostName()}'s room", { room.join() })
            } else {
                DotLoader()
                Spacer(Modifier.height(14.dp))
                Text("You're in. Waiting for ${room.hostName()} to start…", textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun Scoreboard(room: DuelRoom) {
    val s = room.scores
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) { RecDot(); Spacer(Modifier.width(6.dp)); NLabel("You") }
            DotText("${s[room.uid]?.points ?: 0}", size = 30.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            NLabel(room.theirName)
            val theirUid = room.uids.firstOrNull { it != room.uid }
            DotText("${theirUid?.let { s[it]?.points } ?: 0}", size = 30.sp, color = Mx.colors.secondaryText)
        }
    }
}

@Composable
private fun Round(room: DuelRoom) {
    val q = room.question
    if (q == null) { LoadingState("Loading the deck"); return }
    val reveal = room.phase == DuelPhase.REVEAL
    val mine = if (reveal) room.shownMine else room.myAnswer
    val theirs = room.shownTheirs
    Column(Modifier.fillMaxSize()) {
        Scoreboard(room)
        LinearProgressIndicator(
            progress = { room.remaining / DuelRules.PER_QUESTION.toFloat() },
            color = if (room.remaining <= 10) Mx.colors.wrong else Mx.colors.ink,
            trackColor = Mx.colors.hairline, drawStopIndicator = {}, gapSize = 0.dp,
            modifier = Modifier.fillMaxWidth().height(2.dp),
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                DotText("%02d".format(room.qIndex + 1), size = 28.sp)
                DotText(" / ${maxOf(room.game?.total ?: 0, room.deck.size)}", size = 16.sp, color = Mx.colors.tertiaryText, modifier = Modifier.padding(bottom = 3.dp))
                Spacer(Modifier.weight(1f))
                DotText("${room.remaining}s", size = 24.sp, color = if (room.remaining <= 10) Mx.colors.wrong else Mx.colors.ink)
            }
            Spacer(Modifier.height(6.dp))
            HtmlText(q.displayText, sizeSp = 18f, bold = true)
            Spacer(Modifier.height(14.dp))
            q.options.forEachIndexed { i, o ->
                OptionButton(
                    o, i, chosen = mine?.optionId == o.id, correct = q.isCorrect(o.id), revealed = reveal,
                    locked = room.phase != DuelPhase.ASKED,
                ) { room.answer(o.id) }
                if (reveal && theirs?.optionId == o.id) {
                    Text("↑ ${room.theirName}'s pick", style = MaterialTheme.typography.labelSmall, color = Mx.colors.secondaryText, modifier = Modifier.padding(start = 12.dp, top = 4.dp))
                }
                Spacer(Modifier.height(8.dp))
            }
            if (reveal) {
                val row = DuelRules.logRow(room.qIndex, q.id, listOf(room.uid to room.shownMine, "them" to room.shownTheirs))
                Text(
                    "You +${row.answers[room.uid]?.points ?: 0} · ${room.theirName} +${row.answers["them"]?.points ?: 0}",
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Explanation(q)
            }
        }
        Box(Modifier.fillMaxWidth().background(Mx.colors.groupedBackground).navigationBarsPadding().padding(16.dp)) {
            when (room.phase) {
                DuelPhase.ASKED -> Text("Pick an answer — faster scores more.", color = Mx.colors.secondaryText)
                DuelPhase.WAITING -> Text("Locked in. Waiting for ${room.theirName}…", color = Mx.colors.secondaryText)
                else -> if (room.iVoted) {
                    Text(if (room.theyVoted) "Next question coming…" else "Waiting for ${room.theirName} to tap Next…", color = Mx.colors.secondaryText)
                } else {
                    NPill("Next", { room.voteNext() }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun Result(room: DuelRoom, onClose: () -> Unit) {
    val s = room.scores
    val me = s[room.uid] ?: DuelScore()
    val theirUid = room.uids.firstOrNull { it != room.uid }
    val them = theirUid?.let { s[it] } ?: DuelScore()
    val verdict = when {
        me.points > them.points -> "You win!"
        me.points < them.points -> "${room.theirName} wins"
        else -> "It's a draw"
    }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.EmojiEvents, null, tint = if (me.points > them.points) Mx.colors.accent else Mx.colors.ink, modifier = Modifier.height(48.dp).width(48.dp))
        Spacer(Modifier.height(10.dp))
        Text(verdict, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf("You" to me, room.theirName to them).forEach { (name, sc) ->
                Column(
                    Modifier.clip(WidgetShape).background(Mx.colors.card).border(1.dp, Mx.colors.hairline, WidgetShape).padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    NLabel(name)
                    DotText("${sc.points}", size = 44.sp)
                    Text("${sc.correct} correct · ${sc.firsts} first", style = MaterialTheme.typography.labelSmall, color = Mx.colors.secondaryText)
                    sc.fastest?.let { Text("fastest ${it}s", style = MaterialTheme.typography.labelSmall, color = Mx.colors.secondaryText) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        NPill("Done", onClose, filled = false)
    }
}
