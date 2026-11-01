package quest.srihari.medxelite.ui.components

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.ui.text.TextStyle
import quest.srihari.medxelite.ui.theme.NType
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import quest.srihari.medxelite.auth.AuthSession
import quest.srihari.medxelite.ui.theme.Mx

// ---------------------------------------------------------------- Logo mark

/** The MedX heartbeat glyph drawn as a dot-matrix trace, last dot in red. */
@Composable
fun MedxLogoMark(size: Dp = 96.dp, modifier: Modifier = Modifier, framed: Boolean = true) {
    val points = listOf(6f to 62f, 20f to 62f, 33f to 20f, 50f to 78f, 67f to 20f, 80f to 62f, 94f to 62f)
    val ink = if (framed) Color.White else Mx.colors.ink
    val accent = Mx.colors.accent
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .then(if (framed) Modifier.background(Color.Black).border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(size * 0.28f)) else Modifier),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = this.size.width * (if (framed) 0.16f else 0.04f)
            val w = this.size.width - inset * 2
            val pts = points.map { (x, y) -> Offset(inset + x / 100f * w, inset + y / 100f * w) }
            // Walk the polyline and drop a dot every `gap` px.
            val gap = this.size.width * 0.075f
            val r = this.size.width * 0.03f
            val dots = mutableListOf<Offset>()
            var carry = 0f
            for (i in 0 until pts.size - 1) {
                val a = pts[i]
                val b = pts[i + 1]
                val len = (b - a).getDistance()
                var t = carry
                while (t <= len) {
                    dots.add(a + (b - a) * (t / len))
                    t += gap
                }
                carry = t - len
            }
            dots.add(pts.last())
            dots.forEachIndexed { i, d -> drawCircle(if (i == dots.size - 1) accent else ink, if (i == dots.size - 1) r * 1.35f else r, d) }
        }
    }
}

// ---------------------------------------------------------------- Grouped lists (widget sections)

fun LazyListScope.groupedSection(
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    item {
        GroupedSection(header, footer, content = content)
    }
}

@Composable
fun GroupedSection(
    header: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (header != null) {
            Row(Modifier.padding(start = 6.dp, end = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                NLabel(header, maxLines = 2)
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Mx.colors.card)
                .border(1.dp, Mx.colors.hairline, RoundedCornerShape(24.dp))
                .padding(vertical = 4.dp),
            content = content,
        )
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = Mx.colors.secondaryText,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp),
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
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    leading: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
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
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Mx.colors.secondaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (trailing != null) {
                Text(
                    trailing.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Mx.colors.secondaryText,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            trailingContent?.invoke()
            if (showChevron && onClick != null) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Mx.colors.tertiaryText, modifier = Modifier.size(20.dp))
            }
        }
        if (divider) {
            HorizontalDivider(
                Modifier.padding(start = if (icon != null || leading != null) 66.dp else 16.dp, end = 16.dp),
                color = Mx.colors.hairline, thickness = 1.dp,
            )
        }
    }
}

/**
 * Circular monochrome icon tile. A tint of accent / wrong is honoured (it means "attention");
 * every other hue renders as ink on a raised disc.
 */
@Composable
fun IconTile(icon: ImageVector, tint: Color, size: Dp = 36.dp) {
    val c = Mx.colors
    val attention = tint == c.accent || tint == c.wrong
    Box(
        Modifier.size(size).clip(CircleShape).background(c.raised).border(1.dp, c.hairline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (attention) c.accent else c.ink, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun Avatar(session: AuthSession?, size: Dp = 32.dp, modifier: Modifier = Modifier) {
    val c = Mx.colors
    Box(
        modifier.size(size).clip(CircleShape).background(c.raised).border(1.dp, c.separator, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            session?.initials ?: "?", color = c.ink,
            style = TextStyle(fontFamily = NType.mono, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp),
        )
        if (!session?.photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = session?.photoUrl, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        }
    }
}

// ---------------------------------------------------------------- States

/** Three dots that light in turn (static when animations are off). */
@Composable
fun DotLoader(modifier: Modifier = Modifier) {
    val c = Mx.colors
    val reduce = Mx.reduceMotion
    val phase = if (reduce) {
        0f
    } else {
        val t = rememberInfiniteTransition(label = "dots")
        t.animateFloat(0f, 3f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "dot").value
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { i ->
            val on = phase.toInt() == i
            Box(Modifier.size(9.dp).clip(CircleShape).background(if (on) c.accent else c.ink.copy(alpha = 0.35f)))
        }
    }
}

@Composable
fun LoadingState(title: String = "Loading", subtitle: String? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DotLoader()
        Spacer(Modifier.height(18.dp))
        NLabel(title, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Mx.colors.secondaryText, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String? = null,
    icon: ImageVector = Icons.Outlined.WarningAmber,
    actionTitle: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
            DotRing(0f, Modifier.matchParentSize(), dots = 24, dotSize = 4.dp)
            Icon(icon, null, tint = Mx.colors.ink, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(6.dp))
            Text(message, color = Mx.colors.secondaryText, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.widthIn(max = 360.dp))
        }
        if (actionTitle != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            NPill(actionTitle, onAction)
        }
    }
}

// ---------------------------------------------------------------- Top bars

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedxTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    large: Boolean = false,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = Mx.colors.groupedBackground,
        scrolledContainerColor = Mx.colors.groupedBackground,
        navigationIconContentColor = Mx.colors.ink,
        actionIconContentColor = Mx.colors.ink,
        titleContentColor = Mx.colors.ink,
    )
    val nav: @Composable () -> Unit = {
        if (onBack != null) {
            NCircleButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack, Modifier.padding(start = 8.dp, end = 4.dp))
        }
    }
    if (large) {
        LargeTopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = nav, actions = actions, colors = colors, scrollBehavior = scrollBehavior,
        )
    } else {
        TopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge) },
            navigationIcon = nav, actions = actions, colors = colors, scrollBehavior = scrollBehavior,
        )
    }
}

@Composable
fun Pill(text: String, color: Color = Mx.colors.ink) {
    NChip(text, color = if (color == Mx.colors.ink) null else color)
}

@Composable
fun Card(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(18.dp), content: @Composable ColumnScope.() -> Unit) {
    NWidget(modifier.fillMaxWidth(), padding = padding, content = content)
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
