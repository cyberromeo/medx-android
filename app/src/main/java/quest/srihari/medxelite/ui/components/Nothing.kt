package quest.srihari.medxelite.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import quest.srihari.medxelite.ui.theme.Mx
import quest.srihari.medxelite.ui.theme.NType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// Nothing OS building blocks: widgets, dot-matrix type and dot progress.

val WidgetShape = RoundedCornerShape(26.dp)

/** A Nothing-style widget: flat surface, generous radius, hairline edge. */
@Composable
fun NWidget(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(18.dp),
    color: Color = Mx.colors.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(WidgetShape)
            .background(color)
            .border(1.dp, Mx.colors.hairline, WidgetShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Monospace, letter-spaced, upper-case label (the Nothing "caption" voice). */
@Composable
fun NLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Mx.colors.secondaryText,
    size: TextUnit = 11.sp,
    maxLines: Int = 1,
) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = color,
        style = TextStyle(fontFamily = NType.mono, fontSize = size, letterSpacing = 1.4.sp, lineHeight = (size.value + 4).sp),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Dot-matrix numerals / headings in Doto. Doto's line box is 1.2em tall but its glyphs are only
 * 0.7em (cap height, sitting on the baseline), so the box is cropped to the glyphs: big numbers
 * then sit tight in their widgets like Nothing's NDot widgets. Avoid ":" and "." in Doto (they
 * are drawn as crosses); use [DotValue] with a mono suffix instead.
 */
@Composable
fun DotText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 48.sp,
    color: Color = Mx.colors.ink,
    weight: FontWeight = FontWeight.Black,
    maxLines: Int = 1,
) {
    Text(
        text,
        modifier = modifier.layout { measurable, constraints ->
            val p = measurable.measure(constraints)
            val baseline = p[FirstBaseline]
            if (maxLines != 1 || baseline == AlignmentLine.Unspecified) {
                layout(p.width, p.height) { p.place(0, 0) }
            } else {
                val em = size.toPx()
                val top = (baseline - em * 0.72f).toInt().coerceAtLeast(0)
                val h = (em * 0.74f).toInt().coerceAtMost(p.height - top)
                layout(p.width, h, mapOf(FirstBaseline to baseline - top, LastBaseline to baseline - top)) { p.place(0, -top) }
            }
        },
        color = color,
        style = TextStyle(
            fontFamily = NType.dot, fontWeight = weight, fontSize = size, letterSpacing = 0.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Clip,
        softWrap = maxLines > 1,
    )
}

/** The small red "recording" dot. */
@Composable
fun RecDot(modifier: Modifier = Modifier, size: Dp = 7.dp, color: Color = Mx.colors.accent) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

/** Label row with a leading red dot, used as a widget header. */
@Composable
fun WidgetHeader(title: String, modifier: Modifier = Modifier, dot: Boolean = true, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (dot) {
            RecDot()
            Spacer(Modifier.width(8.dp))
        }
        NLabel(title, Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

/** 0→1 once on first show; instant when the user removed animations. */
@Composable
fun rememberReveal(key: Any? = Unit, durationMs: Int = 650): Float {
    val reduce = Mx.reduceMotion
    val a = remember(key) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(key, reduce) {
        if (reduce) a.snapTo(1f) else a.animateTo(1f, tween(durationMs, easing = LinearEasing))
    }
    return a.value
}

/**
 * A row of dots, lit up to [fraction]. Lit dots are ink; [accentLast] paints the leading dot red,
 * like the Nothing Glyph progress.
 */
@Composable
fun DotProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    dots: Int = 24,
    dotSize: Dp = 6.dp,
    on: Color = Mx.colors.ink,
    off: Color = Mx.colors.dotOff,
    accentLast: Boolean = true,
    animate: Boolean = true,
) {
    val reveal = if (animate) rememberReveal(fraction) else 1f
    val accent = Mx.colors.accent
    val lit = (fraction.coerceIn(0f, 1f) * dots * reveal).roundToInt()
    Canvas(modifier.fillMaxWidth().height(dotSize)) {
        val r = dotSize.toPx() / 2f
        val step = if (dots > 1) (size.width - 2 * r) / (dots - 1) else 0f
        for (i in 0 until dots) {
            val c = Offset(r + i * step, size.height / 2f)
            val color = when {
                i < lit - 1 -> on
                i == lit - 1 -> if (accentLast) accent else on
                else -> off
            }
            drawCircle(color, r, c)
        }
    }
}

/** A ring of dots (daily-goal style). [fraction] lights them clockwise from 12 o'clock. */
@Composable
fun DotRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    dots: Int = 36,
    dotSize: Dp = 6.dp,
    on: Color = Mx.colors.ink,
    off: Color = Mx.colors.dotOff,
    head: Color = Mx.colors.accent,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val reveal = rememberReveal(fraction, 800)
    val lit = (fraction.coerceIn(0f, 1f) * dots * reveal).roundToInt()
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val r = dotSize.toPx() / 2f
            val radius = min(size.width, size.height) / 2f - r
            for (i in 0 until dots) {
                val a = -PI / 2 + 2 * PI * i / dots
                val c = Offset(center.x + (radius * cos(a)).toFloat(), center.y + (radius * sin(a)).toFloat())
                val color = when {
                    i < lit - 1 -> on
                    i == lit - 1 -> head
                    else -> off
                }
                drawCircle(color, r, c)
            }
        }
        content()
    }
}

/** A dotted hairline divider. */
@Composable
fun DotDivider(modifier: Modifier = Modifier, color: Color = Mx.colors.dotOff) {
    Canvas(modifier.fillMaxWidth().height(2.dp)) {
        drawLine(
            color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
            strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 6.dp.toPx())),
        )
    }
}

/** Subtle dot-grid backdrop (sign-in, hero widgets). */
@Composable
fun DotGrid(modifier: Modifier = Modifier, spacing: Dp = 14.dp, dot: Dp = 1.6.dp, color: Color = Mx.colors.dotOff) {
    Canvas(modifier) {
        val s = spacing.toPx()
        val r = dot.toPx() / 2f
        var y = s / 2
        while (y < size.height) {
            var x = s / 2
            while (x < size.width) {
                drawCircle(color, r, Offset(x, y))
                x += s
            }
            y += s
        }
    }
}

/** Pill button. Filled = ink fill (white on black); accent = red; otherwise hairline outline. */
@Composable
fun NPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    accent: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
) {
    val c = Mx.colors
    val bg = when {
        accent -> c.accent
        filled -> c.ink
        else -> Color.Transparent
    }
    val fg = when {
        accent -> Color.White
        filled -> c.onInk
        else -> c.ink
    }
    Row(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(CircleShape)
            .background(if (enabled) bg else bg.copy(alpha = if (filled || accent) 0.35f else 0f))
            .then(if (!filled && !accent) Modifier.border(1.dp, c.separator, CircleShape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val tint = if (enabled) fg else fg.copy(alpha = 0.6f)
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = tint, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (trailingIcon != null) {
            Spacer(Modifier.width(8.dp))
            Icon(trailingIcon, null, tint = tint, modifier = Modifier.size(18.dp))
        }
    }
}

/** 48dp circular icon button with a hairline ring. */
@Composable
fun NCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
    tint: Color? = null,
    size: Dp = 48.dp,
) {
    val c = Mx.colors
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) c.ink else c.card)
            .border(1.dp, if (filled) Color.Transparent else c.hairline, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        val t = tint ?: if (filled) c.onInk else c.ink
        Icon(icon, null, tint = if (enabled) t else t.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
    }
}

/** Small mono chip, e.g. a tag or a score. */
@Composable
fun NChip(text: String, modifier: Modifier = Modifier, inverted: Boolean = false, color: Color? = null) {
    val c = Mx.colors
    val fg = color ?: if (inverted) c.onInk else c.ink
    Box(
        modifier
            .clip(CircleShape)
            .background(if (inverted) c.ink else Color.Transparent)
            .border(1.dp, if (inverted) Color.Transparent else (color ?: c.separator), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text.uppercase(), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = NType.mono, fontSize = 10.5.sp, letterSpacing = 1.sp, lineHeight = 14.sp),
        )
    }
}

/** Stat block inside a widget: dot-matrix value over a mono caption. */
@Composable
fun DotStat(value: String, label: String, modifier: Modifier = Modifier, size: TextUnit = 38.sp, color: Color = Mx.colors.ink) {
    Column(modifier) {
        DotText(value, size = size, color = color)
        Spacer(Modifier.height(8.dp))
        NLabel(label, size = 10.sp)
    }
}

/** A dot-matrix bar chart: each column is a stack of dots (Nothing weather/steps widgets). */
@Composable
fun DotBars(
    values: List<Int>,
    modifier: Modifier = Modifier,
    rows: Int = 8,
    highlightLast: Boolean = true,
) {
    val c = Mx.colors
    val max = maxOf(values.maxOrNull() ?: 0, 1)
    val reveal = rememberReveal(values)
    Canvas(modifier) {
        val cols = values.size.coerceAtLeast(1)
        val cellW = size.width / cols
        val cellH = size.height / rows
        val r = min(cellW, cellH) * 0.30f
        values.forEachIndexed { i, v ->
            val lit = if (v <= 0) 0 else maxOf(1, (v.toFloat() / max * rows * reveal).roundToInt())
            for (j in 0 until rows) {
                val cx = cellW * i + cellW / 2
                val cy = size.height - cellH * j - cellH / 2
                val on = j < lit
                val color = when {
                    !on -> c.dotOff
                    highlightLast && i == values.size - 1 && j == lit - 1 -> c.accent
                    else -> c.ink
                }
                drawCircle(color, r, Offset(cx, cy))
            }
        }
    }
}

/** A dot plot line chart (accuracy trend). */
@Composable
fun DotLine(points: List<Int>, modifier: Modifier = Modifier) {
    val c = Mx.colors
    val reveal = rememberReveal(points)
    Canvas(modifier) {
        if (points.isEmpty()) return@Canvas
        val step = size.width / (points.size - 1).coerceAtLeast(1)
        val r = 3.5.dp.toPx()
        // Faint 0/50/100 guides as dotted rules.
        listOf(0f, 0.5f, 1f).forEach { g ->
            val y = r + (size.height - 2 * r) * g
            drawLine(
                c.dotOff, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 5.dp.toPx())),
            )
        }
        // Scale to the data (padded), so a 60-90% run uses the height instead of hugging the top.
        val lo = ((points.minOrNull() ?: 0) - 10).coerceAtLeast(0).toFloat()
        val hi = ((points.maxOrNull() ?: 100) + 10).coerceAtMost(100).toFloat().coerceAtLeast(lo + 10f)
        val pts = points.mapIndexed { i, p -> Offset(i * step, r + (size.height - 2 * r) * (1f - (p - lo) / (hi - lo))) }
        val shown = (pts.size * reveal).roundToInt().coerceIn(1, pts.size)
        for (i in 0 until shown - 1) {
            drawLine(c.ink.copy(alpha = 0.35f), pts[i], pts[i + 1], strokeWidth = 1.dp.toPx())
        }
        pts.take(shown).forEachIndexed { i, p ->
            if (i == pts.size - 1) drawCircle(c.accent, r * 1.2f, p) else drawCircle(c.ink, r, p)
        }
    }
}

/** Hollow-ring placeholder used when there is nothing yet. */
fun ringStroke(px: Float) = Stroke(width = px)

/** Hairline border helper. */
@Composable
fun hairline(): BorderStroke = BorderStroke(1.dp, Mx.colors.hairline)

/** Pill segmented control: the selected option is an inverted (ink) pill inside a hairline track. */
@Composable
fun <T> NSegmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: (T) -> Boolean = { true },
) {
    val c = Mx.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(c.card)
            .border(1.dp, c.hairline, CircleShape)
            .padding(4.dp),
    ) {
        options.forEach { o ->
            val on = o == selected
            val ok = enabled(o)
            Box(
                Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 44.dp)
                    .clip(CircleShape)
                    .background(if (on) c.ink else Color.Transparent)
                    .clickable(enabled = ok, role = Role.Tab) { onSelect(o) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(o), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = when {
                        on -> c.onInk
                        ok -> c.ink
                        else -> c.tertiaryText
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

/** Pill search field: hairline track, thin search glyph, no Material underline. */
@Composable
fun NSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
) {
    val c = Mx.colors
    val focus = focusRequester ?: remember { androidx.compose.ui.focus.FocusRequester() }
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .clip(CircleShape)
            .background(c.card)
            .border(1.dp, c.hairline, CircleShape)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(androidx.compose.material.icons.Icons.Outlined.Search, null, tint = c.secondaryText, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
            if (value.isEmpty()) {
                Text(placeholder, color = c.tertiaryText, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            androidx.compose.foundation.text.BasicTextField(
                value, onValueChange, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.ink),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (value.isNotEmpty()) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable { onValueChange("") }.semantics { contentDescription = "Clear" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(androidx.compose.material.icons.Icons.Outlined.Close, null, tint = c.secondaryText, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** A big dot-matrix value with a small mono suffix ("/ 10", "%"), baseline-ish aligned. */
@Composable
fun DotValue(value: String, suffix: String?, modifier: Modifier = Modifier, size: TextUnit = 56.sp, suffixColor: Color = Mx.colors.secondaryText, color: Color = Mx.colors.ink) {
    Row(modifier) {
        DotText(value, Modifier.alignByBaseline(), size = size, color = color)
        if (suffix != null) {
            Text(
                suffix, color = suffixColor, maxLines = 1,
                style = TextStyle(fontFamily = NType.mono, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.3f).coerceIn(12f, 16f).sp, letterSpacing = 0.5.sp),
                modifier = Modifier.alignByBaseline().padding(start = 6.dp),
            )
        }
    }
}
