package quest.srihari.medxelite.ui.duel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import quest.srihari.medxelite.Medx
import quest.srihari.medxelite.data.Catalog
import quest.srihari.medxelite.data.Load
import quest.srihari.medxelite.data.MedxBank
import quest.srihari.medxelite.data.ServerClock
import quest.srihari.medxelite.duel.DuelCenter
import quest.srihari.medxelite.duel.DuelError
import quest.srihari.medxelite.duel.DuelRepo
import quest.srihari.medxelite.duel.DuelSettings
import quest.srihari.medxelite.duel.MatchStatus
import quest.srihari.medxelite.duel.PublicProfile
import quest.srihari.medxelite.ui.components.ChoiceChip
import quest.srihari.medxelite.ui.components.PrimaryButton
import quest.srihari.medxelite.ui.components.SearchField
import quest.srihari.medxelite.ui.components.SegmentedTabs
import quest.srihari.medxelite.ui.theme.Hues
import quest.srihari.medxelite.ui.theme.Jakarta
import quest.srihari.medxelite.ui.theme.Mx

@Composable
fun DuelSetupSheet(mode: SheetMode, initialTarget: PublicProfile?, onDismiss: () -> Unit, onCreated: (String) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Mx.colors.groupedBackground) {
        DuelSetupContent(mode, initialTarget, onCreated)
    }
}

/**
 * Pick who (invite mode) and how: subject or mixed, 5/10/20 questions, seconds per question.
 * Sending deals the deck, opens the lobby and the invite in one go; the inviter then waits in the lobby.
 */
@Composable
fun DuelSetupContent(mode: SheetMode, initialTarget: PublicProfile?, onCreated: (String) -> Unit, live: Boolean = true, initialQuery: String = "", previewResults: List<PublicProfile>? = null) {
    val c = Mx.colors
    val scope = rememberCoroutineScope()
    val banks by Catalog.banks.collectAsState()
    val bankPref by Medx.prefs.qbankBank.collectAsState()
    val history by DuelCenter.history.collectAsState()
    val myUid = Medx.auth.uid ?: ""
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf(previewResults ?: emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf(initialTarget) }
    var settings by remember { mutableStateOf(DuelSettings()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    if (live) LaunchedEffect(Unit) { Catalog.loadBanks() }
    if (live && mode == SheetMode.INVITE) LaunchedEffect(query) {
        error = null
        if (query.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(350)
        searching = true
        try { results = DuelRepo.searchPeople(query) } catch (e: DuelError) { error = e.message; results = emptyList() } catch (e: Exception) { error = "Search failed. Check your connection." }
        searching = false
    }
    val recent = remember(history) {
        history.mapNotNull { g -> g.opponentOf(myUid)?.let { PublicProfile(it, g.nameOf(it), g.photos[it]?.ifBlank { null }) } }.distinctBy { it.uid }.take(6)
    }
    val subjects = (banks as? Load.Ready)?.value?.filter { it.bank == (MedxBank.fromRaw(bankPref) ?: MedxBank.ARISE) && it.modules.isNotEmpty() }?.sortedBy { it.name } ?: emptyList()
    val now = ServerClock.now()

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        Text(if (mode == SheetMode.INVITE) "Challenge a friend" else "Create a room", style = MaterialTheme.typography.headlineSmall, color = c.ink)
        Text(
            if (mode == SheetMode.INVITE) "They get an invite in the app. It expires in 10 minutes." else "You'll get a code to share. Anyone with it can join.",
            style = MaterialTheme.typography.bodyMedium, color = c.secondaryText, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        if (mode == SheetMode.INVITE) {
            SearchField(query, { query = it }, "Name, or their exact email")
            Spacer(Modifier.height(10.dp))
            val list = if (query.trim().length >= 2) results else recent
            if (query.trim().length < 2 && recent.isNotEmpty()) Label("Recent opponents")
            if (searching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(50)), color = c.accent, trackColor = c.accentContainer)
            if (query.trim().length >= 2 && !searching && results.isEmpty() && error == null) {
                Text(
                    if (query.contains("@")) "No MedX user with that exact email." else "No one found. Names match from the start of any word.",
                    style = MaterialTheme.typography.bodyMedium, color = c.secondaryText, modifier = Modifier.padding(8.dp),
                )
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.wrong, modifier = Modifier.padding(8.dp)) }
            Column(Modifier.clip(RoundedCornerShape(20.dp)).background(if (list.isEmpty()) c.groupedBackground else c.card)) {
                list.forEach { p ->
                    val on = target?.uid == p.uid
                    Row(
                        Modifier.fillMaxWidth().clickable { target = if (on) null else p }.background(if (on) c.accentContainer else c.card).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlayerAvatar(p.displayName, p.photoUrl, 40.dp, Hues.forName(p.displayName), online = p.isOnline(now))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.displayName, fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(
                                    p.rating?.let { "Rating $it" },
                                    when { p.inDuel != null -> "In a duel"; p.isOnline(now) -> "Online"; else -> null },
                                ).joinToString(" · ").ifEmpty { "MedX user" },
                                style = MaterialTheme.typography.bodySmall, color = c.secondaryText,
                            )
                        }
                        if (on) Icon(Icons.Rounded.CheckCircle, "Selected", tint = c.accent)
                    }
                }
            }
            target?.takeIf { it.inDuel != null }?.let {
                Text("${it.displayName} is in a duel right now — they'll see your invite when they finish.", style = MaterialTheme.typography.bodySmall, color = c.flagged, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(18.dp))
        }

        Label("Subject")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
            item { ChoiceChip("Mixed", settings.subjectId == null, { settings = settings.copy(subjectId = null, subjectName = "Mixed") }) }
            items(subjects, key = { it.id }) { s ->
                ChoiceChip(s.name, settings.subjectId == s.id, { settings = settings.copy(subjectId = s.id, subjectName = s.name) })
            }
        }
        Spacer(Modifier.height(16.dp))
        Label("Questions")
        SegmentedTabs(DuelSettings.COUNTS.map { "$it" }, DuelSettings.COUNTS.indexOf(settings.count)) { settings = settings.copy(count = DuelSettings.COUNTS[it]) }
        Spacer(Modifier.height(16.dp))
        Label("Time per question")
        SegmentedTabs(DuelSettings.TIMES.map { "${it}s" }, DuelSettings.TIMES.indexOf(settings.perQuestion)) { settings = settings.copy(perQuestion = DuelSettings.TIMES[it]) }
        Spacer(Modifier.height(8.dp))
        Text(
            "Right answers score 100 + up to 100 for speed. Same questions for both of you.",
            style = MaterialTheme.typography.bodySmall, color = c.secondaryText, modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(20.dp))
        val enabled = !busy && (mode == SheetMode.ROOM || target != null)
        PrimaryButton(
            when {
                busy -> "Dealing questions…"
                mode == SheetMode.ROOM -> "Create room"
                target != null -> "Send invite to ${target!!.displayName.split(" ").first()}"
                else -> "Pick someone to challenge"
            },
            {
                busy = true; error = null
                scope.launch {
                    try {
                        val inLive = DuelCenter.active.value.any { it.status == MatchStatus.LIVE } || DuelCenter.inMatch.value != null
                        if (inLive) throw DuelError("Finish or forfeit your current duel first.")
                        val code = if (mode == SheetMode.ROOM) {
                            DuelRepo.createLobby(settings, "code", DuelRepo.dealQuestions(settings))
                        } else {
                            DuelRepo.sendInvite(target!!, settings).gameId
                        }
                        runCatching { DuelCenter.refresh() }
                        onCreated(code)
                    } catch (e: DuelError) { error = e.message } catch (e: Exception) { error = "Couldn't set up the duel. Check your connection." }
                    busy = false
                }
            },
            enabled = enabled, loading = busy, icon = if (busy) null else Icons.Rounded.Bolt, modifier = Modifier.fillMaxWidth(),
        )
        if (mode == SheetMode.ROOM) error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.wrong, modifier = Modifier.padding(8.dp)) }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = Mx.colors.ink, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
}

@Suppress("unused") private val keepBorder = Modifier.border(0.dp, androidx.compose.ui.graphics.Color.Transparent)
