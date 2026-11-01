package quest.srihari.medxelite.ui.components

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Biotech
import androidx.compose.material.icons.rounded.Bloodtype
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Coronavirus
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Masks
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.PersonalInjury
import androidx.compose.material.icons.rounded.PregnantWoman
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import quest.srihari.medxelite.auth.AuthSession
import quest.srihari.medxelite.ui.theme.Jakarta
import quest.srihari.medxelite.ui.theme.Mx
import quest.srihari.medxelite.ui.theme.onTint
import quest.srihari.medxelite.ui.theme.tint

// ================================================================ Tokens

object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    /** Horizontal screen margin. */
    val margin = 20.dp
}

val CardShape = RoundedCornerShape(24.dp)
val RowShape = RoundedCornerShape(20.dp)

// ================================================================ Depth + touch

/**
 * Soft, single-light-source elevation (Refactoring UI): a wide low-alpha shadow in light mode,
 * nothing in dark mode where the lighter surface carries the depth.
 */
@Composable
fun Modifier.softShadow(shape: Shape = CardShape, elevation: Dp = 10.dp): Modifier {
    val c = Mx.colors
    return if (c.isDark) this else this.shadow(
        elevation = elevation, shape = shape, clip = false,
        ambientColor = c.shadow.copy(alpha = 0.10f), spotColor = c.shadow.copy(alpha = 0.10f),
    )
}

/** Clickable with a ripple and a gentle spring press-scale (skipped when motion is reduced). */
@Composable
fun Modifier.tappable(enabled: Boolean = true, role: Role? = Role.Button, pressScale: Float = 0.98f, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduce = Mx.reduceMotion
    val s by animateFloatAsState(
        if (pressed && !reduce) pressScale else 1f,
        spring(dampingRatio = 0.6f, stiffness = 900f), label = "press",
    )
    return this
        .scale(s)
        .clickable(interactionSource = source, indication = ripple(), enabled = enabled, role = role, onClick = onClick)
}

// ================================================================ Logo mark

/** The MedX pulse-M on cobalt, ending in a coral beat (matches the launcher icon). */
@Composable
fun MedxLogoMark(size: Dp = 96.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .softShadow(RoundedCornerShape(size * 0.28f), 16.dp)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Brush.linearGradient(listOf(Color(0xFF5577F2), Color(0xFF2541C9)))),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val u = this.size.width / 108f
            val pts = listOf(28f to 60f, 36.5f to 60f, 44f to 38f, 54f to 68f, 64f to 38f, 71.5f to 60f)
            val path = Path()
            pts.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(x * u, y * u) else path.lineTo(x * u, y * u) }
            // scale the 108-unit adaptive canvas so the glyph fills the visible tile
            drawContext.transform.scale(1.32f, 1.32f, Offset(this.size.width / 2, this.size.height / 2))
            drawPath(path, Color.White, style = Stroke(width = 7f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(Color(0xFFFF8A5C), 5f * u, Offset(80f * u, 60f * u))
        }
    }
}

// ================================================================ Cards and grouped lists

fun LazyListScope.groupedSection(
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    item { GroupedSection(header, footer, content = content) }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 4.dp, end = 0.dp, bottom = 8.dp).heightIn(min = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Mx.colors.ink, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action, style = MaterialTheme.typography.labelLarge, color = Mx.colors.accent,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onAction).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun GroupedSection(
    header: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = Space.margin, vertical = 10.dp)) {
        if (header != null) SectionHeader(header)
        Column(
            Modifier
                .fillMaxWidth()
                .softShadow()
                .clip(CardShape)
                .background(Mx.colors.card)
                .padding(vertical = 4.dp),
            content = content,
        )
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = Mx.colors.secondaryText,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
            )
        }
    }
}

@Composable
fun MedxRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = Mx.colors.accent,
    trailing: String? = null,
    showChevron: Boolean = true,
    divider: Boolean = true,
    titleColor: Color = Mx.colors.ink,
    leading: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(14.dp))
            } else if (icon != null) {
                IconTile(icon, iconTint)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Mx.colors.secondaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (trailing != null) {
                Text(trailing, style = MaterialTheme.typography.labelLarge, color = Mx.colors.secondaryText, modifier = Modifier.padding(start = 8.dp))
            }
            trailingContent?.invoke()
            if (showChevron && onClick != null) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Mx.colors.tertiaryText, modifier = Modifier.padding(start = 4.dp))
            }
        }
        if (divider) {
            HorizontalDivider(
                Modifier.padding(start = if (icon != null || leading != null) 70.dp else 16.dp, end = 16.dp),
                color = Mx.colors.separator, thickness = 1.dp,
            )
        }
    }
}

/** Tinted squircle with a coloured glyph (never a loud solid block). */
@Composable
fun IconTile(icon: ImageVector, tint: Color, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(tint(tint)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = onTint(tint), modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun Avatar(session: AuthSession?, size: Dp = 36.dp, modifier: Modifier = Modifier) {
    val c = Mx.colors
    Box(
        modifier.size(size).clip(CircleShape).background(c.accentContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(session?.initials ?: "?", color = c.onAccentContainer, fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.38f).sp)
        if (!session?.photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = session?.photoUrl, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        }
    }
}

// ================================================================ Buttons

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    container: Color = Mx.colors.accent,
    content: Color = Mx.colors.onAccent,
    height: Dp = 56.dp,
) {
    val c = Mx.colors
    val bg by animateColorAsState(if (enabled) container else c.sunken, label = "btn")
    Row(
        modifier
            .heightIn(min = height)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .tappable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = content, strokeWidth = 2.5.dp)
        } else {
            Text(
                text, color = if (enabled) content else c.tertiaryText, style = MaterialTheme.typography.labelLarge,
                fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (icon != null) {
                Spacer(Modifier.width(8.dp))
                Icon(icon, null, tint = if (enabled) content else c.tertiaryText, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun TonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = 48.dp,
) {
    val c = Mx.colors
    Row(
        modifier
            .heightIn(min = height)
            .clip(RoundedCornerShape(50))
            .background(c.accentContainer)
            .tappable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = c.onAccentContainer, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = c.onAccentContainer, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** 48dp round icon button on a soft surface. */
@Composable
fun CircleIconButton(icon: ImageVector, description: String?, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, filled: Boolean = false) {
    val c = Mx.colors
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (filled) c.accentContainer else c.sunken.copy(alpha = if (c.isDark) 1f else 0.9f))
            .tappable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (!enabled) c.tertiaryText.copy(alpha = 0.5f) else if (filled) c.onAccentContainer else c.ink, modifier = Modifier.size(22.dp))
    }
}

// ================================================================ Segmented control + chips

/** iOS-style segmented control: sunken track, raised white thumb. Each segment is a 48dp target. */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    enabled: (Int) -> Boolean = { true },
    onSelect: (Int) -> Unit,
) {
    val c = Mx.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(if (c.isDark) c.card else c.sunken)
            .padding(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val ok = enabled(i)
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .then(if (on) Modifier.softShadow(RoundedCornerShape(50), 4.dp) else Modifier)
                    .clip(RoundedCornerShape(50))
                    .background(if (on) (if (c.isDark) c.raised else Color.White) else Color.Transparent)
                    .clickable(enabled = ok, role = Role.Tab) { onSelect(i) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = when {
                        !ok -> c.tertiaryText.copy(alpha = 0.6f)
                        on -> c.ink
                        else -> c.secondaryText
                    },
                )
            }
        }
    }
}

@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Mx.colors
    val bg by animateColorAsState(if (selected) c.ink else c.card, label = "chip")
    Box(
        modifier
            .heightIn(min = 40.dp)
            .then(if (!selected) Modifier.border(1.dp, c.separator, RoundedCornerShape(50)) else Modifier)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) c.groupedBackground else c.ink, maxLines = 1)
    }
}

@Composable
fun Pill(text: String, color: Color = Mx.colors.accent) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = onTint(color),
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(tint(color)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

// ================================================================ Data viz

@Composable
fun ProgressRing(
    fraction: Float,
    size: Dp,
    stroke: Dp,
    color: Color,
    track: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val reduce = Mx.reduceMotion
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), if (reduce) spring(stiffness = 100000f) else spring(dampingRatio = 1f, stiffness = 60f), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = Stroke(width = stroke.toPx(), cap = StrokeCap.Round)
            val inset = stroke.toPx() / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - inset * 2, this.size.height - inset * 2)
            drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = s)
            if (f > 0f) drawArc(color, -90f, 360f * f, false, topLeft = Offset(inset, inset), size = arcSize, style = s)
        }
        content()
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Mx.colors.accent, track: Color = Mx.colors.sunken, height: Dp = 8.dp) {
    val reduce = Mx.reduceMotion
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), if (reduce) spring(stiffness = 100000f) else spring(dampingRatio = 1f, stiffness = 80f), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(track)) {
        Box(Modifier.fillMaxWidth(f).height(height).clip(RoundedCornerShape(50)).background(color))
    }
}

@Composable
fun StatTile(value: String, label: String, icon: ImageVector, hue: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(tint(hue)).padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Icon(icon, null, tint = onTint(hue), modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(10.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = Mx.colors.ink, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Mx.colors.secondaryText, maxLines = 2)
    }
}

// ================================================================ Subjects

@Composable
fun SubjectIcon(name: String, size: Dp = 44.dp) {
    IconTile(subjectIcon(name), quest.srihari.medxelite.ui.theme.Hues.forName(name), size)
}

/** A distinct glyph per FMGE subject. */
fun subjectIcon(name: String): ImageVector {
    val n = name.lowercase()
    return when {
        "pharma" in n -> Icons.Rounded.Medication
        "cardio" in n -> Icons.Rounded.Favorite
        "physio" in n -> Icons.Rounded.MonitorHeart
        "anat" in n -> Icons.Rounded.AccessibilityNew
        "bioch" in n -> Icons.Rounded.Science
        "micro" in n -> Icons.Rounded.Coronavirus
        "patho" in n -> Icons.Rounded.Biotech
        "neuro" in n || "psych" in n -> Icons.Rounded.Psychology
        "ortho" in n -> Icons.Rounded.PersonalInjury
        "paed" in n || "pedia" in n -> Icons.Rounded.ChildCare
        "obst" in n || "gyn" in n || "obg" in n -> Icons.Rounded.PregnantWoman
        "ophth" in n || "eye" in n -> Icons.Rounded.Visibility
        n == "ent" || n.startsWith("ent ") || "otorhino" in n -> Icons.Rounded.Hearing
        "forens" in n || "fmt" in n || "legal" in n -> Icons.Rounded.Gavel
        "community" in n || "psm" in n || "social" in n || "preventive" in n -> Icons.Rounded.Groups
        "derma" in n || "skin" in n -> Icons.Rounded.Face
        "anaes" in n || "anes" in n -> Icons.Rounded.Masks
        "radio" in n -> Icons.Rounded.CenterFocusStrong
        "surg" in n -> Icons.Rounded.Healing
        "respir" in n || "pulmo" in n -> Icons.Rounded.Air
        "haem" in n || "hemat" in n -> Icons.Rounded.Bloodtype
        "dent" in n -> Icons.Rounded.Spa
        "medicine" in n -> Icons.Rounded.MedicalServices
        else -> Icons.Rounded.LocalHospital
    }
}

// ================================================================ States

@Composable
fun LoadingState(title: String = "Loading", subtitle: String? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(Modifier.size(36.dp), color = Mx.colors.accent, trackColor = Mx.colors.accentContainer, strokeWidth = 3.5.dp, strokeCap = StrokeCap.Round)
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Mx.colors.ink, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Mx.colors.secondaryText, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String? = null,
    icon: ImageVector = Icons.Rounded.WarningAmber,
    actionTitle: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = Mx.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(80.dp).clip(CircleShape).background(c.accentContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.onAccentContainer, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = c.ink, textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(6.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = c.secondaryText, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 360.dp))
        }
        if (actionTitle != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            PrimaryButton(actionTitle, onAction, height = 52.dp)
        }
    }
}

// ================================================================ Top bars

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedxTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    large: Boolean = false,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Mx.colors
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = c.groupedBackground,
        scrolledContainerColor = c.groupedBackground,
        titleContentColor = c.ink,
        navigationIconContentColor = c.ink,
        actionIconContentColor = c.ink,
    )
    val nav: @Composable () -> Unit = {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        }
    }
    if (large) {
        LargeTopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.headlineLarge) },
            navigationIcon = nav, actions = actions,
            colors = TopAppBarDefaults.largeTopAppBarColors(
                containerColor = c.groupedBackground, scrolledContainerColor = c.groupedBackground,
                titleContentColor = c.ink, navigationIconContentColor = c.ink, actionIconContentColor = c.ink,
            ),
            scrollBehavior = scrollBehavior,
        )
    } else {
        TopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge) },
            navigationIcon = nav, actions = actions, colors = colors, scrollBehavior = scrollBehavior,
        )
    }
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(20.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .softShadow()
            .clip(CardShape)
            .background(Mx.colors.card)
            .then(if (onClick != null) Modifier.tappable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

fun openCustomTab(context: Context, url: String) {
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url))
    } catch (_: Exception) {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Pill search / text field on a card surface. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = Icons.Rounded.Search,
) {
    val c = Mx.colors
    androidx.compose.material3.OutlinedTextField(
        value, onValueChange,
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = c.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = leading?.let { { Icon(it, null, tint = c.secondaryText) } },
        singleLine = true,
        shape = RoundedCornerShape(50),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.ink),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedContainerColor = c.card, unfocusedContainerColor = c.card,
            unfocusedBorderColor = if (c.isDark) c.separator else Color.Transparent, focusedBorderColor = c.accent,
            cursorColor = c.accent,
        ),
        modifier = modifier.fillMaxWidth().softShadow(RoundedCornerShape(50), 4.dp),
    )
}
