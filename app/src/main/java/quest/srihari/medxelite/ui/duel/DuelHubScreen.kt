package quest.srihari.medxelite.ui.duel

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import quest.srihari.medxelite.Medx
import quest.srihari.medxelite.data.ServerClock
import quest.srihari.medxelite.duel.DuelCenter
import quest.srihari.medxelite.duel.DuelError
import quest.srihari.medxelite.duel.DuelGameDoc
import quest.srihari.medxelite.duel.DuelInvite
import quest.srihari.medxelite.duel.DuelRecord
import quest.srihari.medxelite.duel.DuelRepo
import quest.srihari.medxelite.duel.Elo
import quest.srihari.medxelite.duel.InviteStatus
import quest.srihari.medxelite.duel.MatchStatus
import quest.srihari.medxelite.duel.PublicProfile
import quest.srihari.medxelite.ui.Navigator
import quest.srihari.medxelite.ui.components.Card
import quest.srihari.medxelite.ui.components.GroupedSection
import quest.srihari.medxelite.ui.components.MedxRow
import quest.srihari.medxelite.ui.components.MedxTopBar
import quest.srihari.medxelite.ui.components.Pill
import quest.srihari.medxelite.ui.components.PrimaryButton
import quest.srihari.medxelite.ui.components.ProgressRing
import quest.srihari.medxelite.ui.components.SectionHeader
import quest.srihari.medxelite.ui.components.Space
import quest.srihari.medxelite.ui.components.TonalButton
import quest.srihari.medxelite.ui.components.softShadow
import quest.srihari.medxelite.ui.components.tappable
import quest.srihari.medxelite.ui.theme.Hues
import quest.srihari.medxelite.ui.theme.Jakarta
import quest.srihari.medxelite.ui.theme.Mx
import quest.srihari.medxelite.ui.theme.onTint
import quest.srihari.medxelite.ui.theme.tint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class SheetMode { INVITE, ROOM }

/** Duels hub: challenge, quick match, join/create by code, invites, active duels, history and stats. */
@Composable
fun DuelHubScreen(nav: Navigator, startSheet: SheetMode? = null, live: Boolean = true) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val record by DuelCenter.record.collectAsState()
    val incoming by DuelCenter.incoming.collectAsState()
    val outgoing by DuelCenter.outgoing.collectAsState()
    val active by DuelCenter.active.collectAsState()
    val history by DuelCenter.history.collectAsState()
    val loaded by DuelCenter.loaded.collectAsState()
    var sheet by remember { mutableStateOf(startSheet) }
    var sheetTarget by remember { mutableStateOf<PublicProfile?>(null) }
    var showJoin by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(ServerClock.now()) }
    val myUid = Medx.auth.uid ?: ""

    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (DuelUiFlags.askNotificationPermission && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) runCatching { askPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
    if (live) {
        LaunchedEffect(Unit) { runCatching { DuelCenter.refresh(full = true) } }
    }
    LaunchedEffect(Unit) { while (true) { now = ServerClock.now(); delay(1000) } }

    val liveIncoming = incoming.filter { it.isLive(now) }
    val liveOutgoing = outgoing.filter { it.isLive(now) }
    val opponents = remember(history) {
        history.mapNotNull { g -> g.opponentOf(myUid)?.let { PublicProfile(it, g.nameOf(it), g.photos[it]?.ifBlank { null }) } }.distinctBy { it.uid }.take(10)
    }
    val busy = DuelCenter.inMatch.collectAsState().value != null || active.any { it.status == MatchStatus.LIVE }

    Scaffold(containerColor = Mx.colors.groupedBackground, topBar = { MedxTopBar("Duels", onBack = nav::back) }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 40.dp)) {
            item { StatsCard(record ?: DuelRecord(), loaded || record != null) }
            item {
                ActionGrid(
                    onChallenge = { sheetTarget = null; sheet = SheetMode.INVITE },
                    onQuick = { if (busy) message = "Finish or forfeit your current duel first." else nav.go("duels/quick") },
                    onJoin = { showJoin = true },
                    onRoom = { sheet = SheetMode.ROOM },
                )
            }
            message?.let { m -> item { Text(m, color = Mx.colors.wrong, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) } }

            if (active.isNotEmpty()) item {
                GroupedSection("In progress") {
                    active.forEachIndexed { i, g ->
                        val opp = g.opponentOf(myUid)
                        MedxRow(
                            if (opp == null) "Room ${g.code} · waiting for a player" else "vs ${g.nameOf(opp)}",
                            "${g.settings.label} · ${if (g.status == MatchStatus.LIVE) "Q${g.qIndex + 1} of ${g.total}" else "lobby"}",
                            icon = Icons.Rounded.Bolt, iconTint = Hues.yellow, trailing = if (g.status == MatchStatus.LIVE) "Rejoin" else "Open",
                            divider = i < active.size - 1,
                        ) { nav.go("duel/${g.code}") }
                    }
                }
            }
            if (liveIncoming.isNotEmpty()) item {
                SectionHeader("Invites for you", Modifier.padding(start = Space.margin, end = Space.margin, top = 12.dp))
            }
            items(liveIncoming, key = { "in_" + it.id }) { inv -> IncomingCard(inv, now, onOpen = { nav.go("duel/$it") }) }
            if (liveOutgoing.isNotEmpty()) item {
                GroupedSection("Sent") {
                    liveOutgoing.forEachIndexed { i, inv ->
                        MedxRow(
                            "Waiting for ${inv.toName}", "${inv.settings.label} · expires in ${mmss(inv.expiresAt - now)}",
                            leading = { PlayerAvatar(inv.toName, null, 40.dp, DuelHues.them) }, divider = i < liveOutgoing.size - 1,
                            trailingContent = {
                                Text(
                                    "Cancel", color = Mx.colors.wrong, style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { scope.launch { runCatching { DuelRepo.cancelInvite(inv.id) }; runCatching { DuelCenter.refresh() } } }.padding(10.dp),
                                )
                            },
                        ) { nav.go("duel/${inv.gameId}") }
                    }
                }
            }
            if (opponents.isNotEmpty()) item {
                Column(Modifier.padding(top = 12.dp)) {
                    SectionHeader("Recent opponents", Modifier.padding(horizontal = Space.margin))
                    LazyRow(contentPadding = PaddingValues(horizontal = Space.margin), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(opponents, key = { it.uid }) { p ->
                            Column(
                                Modifier.width(72.dp).clip(RoundedCornerShape(16.dp)).clickable { sheetTarget = p; sheet = SheetMode.INVITE }.padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                PlayerAvatar(p.displayName, p.photoUrl, 52.dp, Hues.forName(p.displayName))
                                Spacer(Modifier.height(6.dp))
                                Text(p.displayName.split(" ").first(), style = MaterialTheme.typography.labelMedium, color = Mx.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            item {
                GroupedSection("History", footer = if (history.isEmpty()) null else "Rating moves with every finished duel (Elo).") {
                    if (history.isEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.EmojiEvents, null, tint = Mx.colors.tertiaryText, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("No duels yet", style = MaterialTheme.typography.titleMedium, color = Mx.colors.ink)
                            Text("Challenge a friend or try a quick match.", style = MaterialTheme.typography.bodyMedium, color = Mx.colors.secondaryText)
                        }
                    }
                    history.take(25).forEachIndexed { i, g -> HistoryRow(g, myUid, i < minOf(25, history.size) - 1) { nav.go("duel/${g.code}") } }
                }
            }
            item {
                Text(
                    "Classic Faceoff rooms (deal a Marrow paper) →", color = Mx.colors.accent, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp).clip(RoundedCornerShape(50)).clickable { nav.go("faceoff") }.padding(8.dp),
                )
            }
        }
    }

    sheet?.let { mode ->
        DuelSetupSheet(
            mode = mode, initialTarget = sheetTarget, onDismiss = { sheet = null },
            onCreated = { code -> sheet = null; nav.go("duel/$code") },
        )
    }
    if (showJoin) JoinByCodeDialog(onDismiss = { showJoin = false }, onDuel = { showJoin = false; nav.go("duel/$it") }, onLegacy = { showJoin = false; nav.go("faceoff?code=$it") })
}

@Composable
private fun StatsCard(r: DuelRecord, loaded: Boolean) {
    val c = Mx.colors
    Box(
        Modifier.padding(horizontal = Space.margin, vertical = 8.dp).fillMaxWidth().softShadow(RoundedCornerShape(28.dp), 14.dp)
            .clip(RoundedCornerShape(28.dp)).background(if (c.isDark) c.accentContainer else c.accent),
    ) {
        val fg = if (c.isDark) c.onAccentContainer else Color.White
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Duel rating", style = MaterialTheme.typography.labelLarge, color = fg.copy(alpha = 0.8f))
                    Text(
                        if (loaded) "%,d".format(r.rating) else "—", fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold,
                        fontSize = 44.sp, lineHeight = 48.sp, color = fg,
                    )
                    Text(
                        if (r.played == 0) "Starts at ${Elo.START} · win to climb" else "Peak %,d · %d duel%s".format(r.peak, r.played, if (r.played == 1) "" else "s"),
                        style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.8f),
                    )
                }
                ProgressRing(r.winRate, 84.dp, 9.dp, if (c.isDark) c.accent else Color.White, fg.copy(alpha = 0.18f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(r.winRate * 100).toInt()}%", fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = fg)
                        Text("win rate", fontSize = 10.sp, color = fg.copy(alpha = 0.8f))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip("${r.wins}", "Won", fg, Modifier.weight(1f))
                StatChip("${r.losses}", "Lost", fg, Modifier.weight(1f))
                StatChip("${r.draws}", "Drawn", fg, Modifier.weight(1f))
                StatChip(
                    when { r.streak > 0 -> "${r.streak}W"; r.streak < 0 -> "${-r.streak}L"; else -> "0" }, "Streak", fg, Modifier.weight(1f),
                    icon = if (r.streak >= 2) Icons.Rounded.LocalFireDepartment else null,
                )
            }
        }
    }
}

@Composable
private fun StatChip(value: String, label: String, fg: Color, modifier: Modifier, icon: ImageVector? = null) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(fg.copy(alpha = 0.12f)).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Icon(icon, null, tint = Color(0xFFFFB259), modifier = Modifier.size(16.dp))
            Text(value, fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = fg)
        }
        Text(label, fontSize = 11.sp, color = fg.copy(alpha = 0.8f))
    }
}

@Composable
private fun ActionGrid(onChallenge: () -> Unit, onQuick: () -> Unit, onJoin: () -> Unit, onRoom: () -> Unit) {
    Column(Modifier.padding(horizontal = Space.margin, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile("Challenge a friend", "Search by name or email", Icons.Rounded.PersonAdd, Hues.blue, Modifier.weight(1f), onChallenge)
            ActionTile("Quick match", "Random online opponent", Icons.Rounded.Bolt, Hues.orange, Modifier.weight(1f), onQuick)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile("Join by code", "Enter a room code", Icons.Rounded.Key, Hues.teal, Modifier.weight(1f), onJoin)
            ActionTile("Create a room", "Get a code to share", Icons.Rounded.Tag, Hues.purple, Modifier.weight(1f), onRoom)
        }
    }
}

@Composable
private fun ActionTile(title: String, subtitle: String, icon: ImageVector, hue: Color, modifier: Modifier, onClick: () -> Unit) {
    val c = Mx.colors
    Column(
        modifier.softShadow(RoundedCornerShape(24.dp), 8.dp).clip(RoundedCornerShape(24.dp)).background(c.card).tappable(onClick = onClick).padding(16.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(tint(hue)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = onTint(hue), modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun IncomingCard(inv: DuelInvite, now: Long, onOpen: (String) -> Unit) {
    val c = Mx.colors
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    Card(Modifier.padding(horizontal = Space.margin, vertical = 6.dp), padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(inv.fromName, inv.fromPhoto, 44.dp, DuelHues.them)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${inv.fromName} challenged you", fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = c.ink)
                Text("${inv.settings.label} · ${mmss(inv.expiresAt - now)} left", style = MaterialTheme.typography.bodySmall, color = c.secondaryText)
            }
        }
        err?.let { Text(it, color = c.wrong, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Accept", {
                busy = true
                scope.launch {
                    try { onOpen(DuelRepo.acceptInvite(inv.id)) } catch (e: DuelError) { err = e.message } catch (e: Exception) { err = "Couldn't accept. Check your connection." }
                    busy = false
                }
            }, loading = busy, enabled = !busy, height = 44.dp, modifier = Modifier.weight(1f))
            TonalButton("Decline", { scope.launch { runCatching { DuelRepo.declineInvite(inv.id) }; runCatching { DuelCenter.refresh() } } }, enabled = !busy, height = 44.dp, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun HistoryRow(g: DuelGameDoc, myUid: String, divider: Boolean, onClick: () -> Unit) {
    val c = Mx.colors
    val r = g.result ?: return
    val opp = g.opponentOf(myUid)
    val won = r.winner == myUid
    val draw = r.winner == null
    val mine = r.scores[myUid]?.points ?: 0
    val theirs = opp?.let { r.scores[it]?.points } ?: 0
    val delta = r.eloDelta[myUid] ?: 0
    val date = g.finishedAtMs?.let { DateTimeFormatter.ofPattern("d MMM").format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) } ?: ""
    MedxRow(
        title = "${if (draw) "Draw" else if (won) "Won" else "Lost"} vs ${g.nameOf(opp)}",
        subtitle = "$mine – $theirs · ${g.settings.subjectName} · $date",
        leading = {
            val hue = if (draw) Hues.gray else if (won) c.correct else c.wrong
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(tint(hue)), contentAlignment = Alignment.Center) {
                Text(if (draw) "D" else if (won) "W" else "L", fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, color = onTint(hue))
            }
        },
        trailingContent = { Pill(if (delta >= 0) "+$delta" else "$delta", if (delta >= 0) c.correct else c.wrong) },
        divider = divider, onClick = onClick,
    )
}

@Composable
fun JoinByCodeDialog(onDismiss: () -> Unit, onDuel: (String) -> Unit, onLegacy: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val c = Mx.colors
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = c.card,
        title = { Text("Join by code") },
        text = {
            Column {
                Text("Enter the room code your friend shared.", style = MaterialTheme.typography.bodyMedium, color = c.secondaryText)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    code, { code = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(8); err = null },
                    placeholder = { Text("ABC123") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    textStyle = MaterialTheme.typography.titleLarge.copy(letterSpacing = 4.sp, color = c.ink),
                    shape = RoundedCornerShape(16.dp), isError = err != null,
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = c.sunken, unfocusedContainerColor = c.sunken, unfocusedBorderColor = Color.Transparent, focusedBorderColor = c.accent),
                )
                err?.let { Text(it, color = c.wrong, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && code.length >= 4, onClick = {
                busy = true
                scope.launch {
                    try {
                        if (DuelCenter.inMatch.value != null) throw DuelError("Finish your current duel first.")
                        when (val r = DuelRepo.joinByCode(code)) {
                            is DuelRepo.JoinResult.Duel -> onDuel(r.code)
                            is DuelRepo.JoinResult.Legacy -> onLegacy(r.code)
                        }
                    } catch (e: DuelError) { err = e.message } catch (e: Exception) { err = "Couldn't join. Check the code and your connection." }
                    busy = false
                }
            }) { Text(if (busy) "Joining…" else "Join") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
    )
}

@Suppress("unused") private val keep = InviteStatus.PENDING
