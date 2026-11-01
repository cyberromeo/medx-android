package quest.srihari.medxelite.ui.duel

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import quest.srihari.medxelite.data.ServerClock
import quest.srihari.medxelite.duel.DuelAnswer
import quest.srihari.medxelite.duel.DuelCenter
import quest.srihari.medxelite.duel.DuelError
import quest.srihari.medxelite.duel.DuelInvite
import quest.srihari.medxelite.duel.DuelRepo
import quest.srihari.medxelite.ui.components.Pill
import quest.srihari.medxelite.ui.components.PrimaryButton
import quest.srihari.medxelite.ui.components.TonalButton
import quest.srihari.medxelite.ui.components.softShadow
import quest.srihari.medxelite.ui.theme.Hues
import quest.srihari.medxelite.ui.theme.Jakarta
import quest.srihari.medxelite.ui.theme.Mx
import quest.srihari.medxelite.ui.theme.onTint
import quest.srihari.medxelite.ui.theme.tint

/** Duel colours: you are always the brand accent, the opponent always coral-pink. */
object DuelHues {
    val them = Hues.pink
    @Composable fun me() = Mx.colors.accent
}

/** Test switch: screenshot runs never ask for the notification permission. */
object DuelUiFlags {
    var askNotificationPermission = true
}

fun initialsOf(name: String) = name.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase().ifEmpty { "?" }

fun mmss(ms: Long): String {
    val s = (ms.coerceAtLeast(0) + 999) / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun PlayerAvatar(name: String, photoUrl: String?, size: Dp = 44.dp, hue: Color = Mx.colors.accent, ring: Boolean = false, online: Boolean? = null) {
    Box(Modifier.size(size)) {
        Box(
            Modifier.size(size).clip(CircleShape).background(tint(hue))
                .then(if (ring) Modifier.border(2.dp, hue, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(initialsOf(name), color = onTint(hue), fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp)
            if (!photoUrl.isNullOrBlank()) {
                AsyncImage(photoUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
            }
        }
        if (online != null) {
            Box(
                Modifier.align(Alignment.BottomEnd).size(size * 0.3f).clip(CircleShape).background(Mx.colors.card).padding(2.dp)
                    .clip(CircleShape).background(if (online) Mx.colors.correct else Mx.colors.tertiaryText),
            )
        }
    }
}

/** A row of dots, one per question: filled green/red once answered, outlined for the current one. */
@Composable
fun QuestionDots(total: Int, answers: Map<Int, DuelAnswer>, current: Int, revealed: (Int) -> Boolean, hue: Color, modifier: Modifier = Modifier) {
    val c = Mx.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(if (total > 12) 3.dp else 5.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until total) {
            val a = answers[i]
            val color = when {
                a == null -> if (i < current) c.wrong.copy(alpha = 0.35f) else c.sunken
                !revealed(i) -> hue
                a.correct && !a.timedOut -> c.correct
                else -> c.wrong
            }
            Box(
                Modifier.weight(1f).height(if (i == current) 8.dp else 6.dp).clip(RoundedCornerShape(50)).background(color)
                    .then(if (i == current && a == null) Modifier.border(1.5.dp, hue, RoundedCornerShape(50)) else Modifier),
            )
        }
    }
}

/** "Asha answered" / "Asha is thinking…" with a soft pulse while thinking. */
@Composable
fun OpponentStatus(name: String, answered: Boolean, stale: Boolean) {
    val c = Mx.colors
    val pulse = rememberInfiniteTransition(label = "pulse")
    val a by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
    val (text, hue) = when {
        stale -> "$name's connection dropped" to c.flagged
        answered -> "$name answered" to DuelHues.them
        else -> "$name is thinking…" to c.tertiaryText
    }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (answered || stale) tint(hue) else c.sunken).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).alpha(if (answered || Mx.reduceMotion) 1f else a).clip(CircleShape).background(hue))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (answered || stale) onTint(hue) else c.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (answered) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.Check, null, tint = onTint(hue), modifier = Modifier.size(14.dp))
        }
    }
}

/**
 * The in-app invite prompt, drawn over whatever screen is open (not a separate window, so it also
 * appears in screenshots). Accept takes the guest seat atomically and opens the room.
 */
@Composable
fun InvitePrompt(invite: DuelInvite, onOpenRoom: (String) -> Unit, onDismiss: () -> Unit) {
    val c = Mx.colors
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(ServerClock.now()) }
    LaunchedEffect(invite.id) { while (true) { now = ServerClock.now(); delay(1000) } }
    val left = invite.expiresAt - now
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(enabled = busy == null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(24.dp).widthIn(max = 420.dp).fillMaxWidth().softShadow(RoundedCornerShape(32.dp), 24.dp)
                .clip(RoundedCornerShape(32.dp)).background(c.card).clickable(enabled = false) {}.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerAvatar(invite.fromName, invite.fromPhoto, 56.dp, DuelHues.them, ring = true)
                Box(Modifier.padding(horizontal = 10.dp).size(36.dp).clip(CircleShape).background(tint(Hues.yellow)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Bolt, null, tint = onTint(Hues.yellow), modifier = Modifier.size(22.dp))
                }
                PlayerAvatar("You", null, 56.dp, c.accent, ring = true)
            }
            Spacer(Modifier.height(18.dp))
            Text("${invite.fromName} challenged you", style = MaterialTheme.typography.headlineSmall, color = c.ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(invite.settings.subjectName, Hues.forName(invite.settings.subjectName))
                Pill("${invite.settings.count} Qs", Hues.blue)
                Pill("${invite.settings.perQuestion}s each", Hues.orange)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (left > 0) "Expires in ${mmss(left)}" else "This invite has expired",
                style = MaterialTheme.typography.bodyMedium, color = if (left > 60_000) c.secondaryText else c.wrong,
            )
            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = c.wrong, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                "Accept & play", {
                    busy = "accept"; error = null
                    scope.launch {
                        try {
                            if (DuelCenter.inMatch.value != null) throw DuelError("Finish your current duel first.")
                            val code = DuelRepo.acceptInvite(invite.id)
                            onDismiss(); onOpenRoom(code)
                        } catch (e: DuelError) { error = e.message } catch (e: Exception) { error = "Couldn't accept. Check your connection." } finally { busy = null }
                    }
                },
                enabled = left > 0 && busy == null, loading = busy == "accept", icon = Icons.Rounded.Bolt, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TonalButton("Decline", {
                    busy = "decline"
                    scope.launch { runCatching { DuelRepo.declineInvite(invite.id) }; busy = null; onDismiss() }
                }, enabled = busy == null, modifier = Modifier.weight(1f))
                TonalButton("Later", onDismiss, enabled = busy == null, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Floating "duel in progress" pill on the tabs, for rejoining after leaving the room or a restart. */
@Composable
fun RejoinPill(opponent: String, live: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Mx.colors
    Row(
        modifier.softShadow(RoundedCornerShape(50), 12.dp).clip(RoundedCornerShape(50)).background(c.ink).clickable(onClick = onClick)
            .padding(start = 8.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(Hues.yellow), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Bolt, null, tint = Color.Black, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            if (live) "Duel vs $opponent in progress · Rejoin" else "Duel lobby with $opponent · Open",
            color = c.groupedBackground, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
