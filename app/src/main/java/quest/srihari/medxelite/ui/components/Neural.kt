package quest.srihari.medxelite.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import kotlinx.coroutines.launch
import quest.srihari.medxelite.ui.theme.Aurora
import quest.srihari.medxelite.ui.theme.Grotesk
import quest.srihari.medxelite.ui.theme.Mx
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** Fully transparent version of the same hue, so gradients never dip through grey/black. */
private val Color.clear: Color get() = copy(alpha = 0f)

// ================================================================ Time

/**
 * Seconds since first composition, advanced on animation frames. Read it only in draw lambdas so
 * motion never recomposes. Holds still when motion is reduced.
 */
@Composable
fun rememberMotionClock(): State<Float> {
    val t = remember { mutableFloatStateOf(0f) }
    val still = Mx.reduceMotion
    if (!still) {
        LaunchedEffect(Unit) {
            val start = withInfiniteAnimationFrameMillis { it }
            while (true) withInfiniteAnimationFrameMillis { t.floatValue = (it - start) / 1000f }
        }
    }
    return t
}

// ================================================================ Backdrop

/** Deep-night base, a slow aurora behind the header area and a faint dot grid that fades out. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val c = Mx.colors
    val time = rememberMotionClock()
    val dark = c.isDark
    Box(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val step = 22.dp.toPx()
                val gw = size.width
                val gh = size.height
                val pts = buildList {
                    var y = step / 2
                    while (y < gh * 0.42f) {
                        var x = step / 2
                        while (x < gw) { add(Offset(x, y)); x += step }
                        y += step
                    }
                }
                val rows = pts.groupBy { it.y }.toList()
                val dotColor = if (dark) Color.White else Color(0xFF3B2F8F)
                val dotMax = if (dark) 0.07f else 0.08f
                val w = size.width
                val h = size.height
                onDrawBehind {
                    drawRect(c.base)
                    val t = time.value
                    fun blob(color: Color, cx: Float, cy: Float, r: Float, a: Float) {
                        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = a), color.clear), Offset(cx, cy), r), r, Offset(cx, cy))
                    }
                    val k = if (dark) 1f else 0.55f
                    blob(Aurora.violet, w * (0.12f + 0.08f * sin(t * 0.21f)), h * (0.04f + 0.03f * cos(t * 0.17f)), w * 0.95f, 0.38f * k)
                    blob(Aurora.cyan, w * (0.98f + 0.06f * cos(t * 0.19f)), h * (0.13f + 0.03f * sin(t * 0.23f)), w * 0.8f, 0.24f * k)
                    blob(Aurora.magenta, w * (0.55f + 0.12f * sin(t * 0.13f)), h * (-0.04f + 0.02f * sin(t * 0.29f)), w * 0.6f, 0.20f * k)
                    rows.forEach { (y, row) ->
                        val f = (1f - y / (h * 0.42f)).coerceIn(0f, 1f)
                        drawPoints(row, PointMode.Points, dotColor.copy(alpha = dotMax * f), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
                    }
                    // Soft vignette at the bottom keeps lower content calm.
                    drawRect(Brush.verticalGradient(listOf(c.base.copy(alpha = 0f), c.base.copy(alpha = 0.6f)), startY = h * 0.6f, endY = h), topLeft = Offset(0f, h * 0.6f))
                }
            },
    )
}

private class Node(val x: Float, val y: Float, val ax: Float, val ay: Float, val fx: Float, val fy: Float, val ph: Float, val r: Float, val hub: Boolean)

/** A drifting "neural" constellation: nodes, links between near neighbours and pulsing hubs. */
@Composable
fun NeuralField(
    modifier: Modifier = Modifier,
    nodes: Int = 34,
    seed: Int = 7,
    intensity: Float = 1f,
    /** Fractional centre of a region kept clear (text sits there); null keeps the whole field. */
    hollow: Offset? = null,
    /** Radius of the clear region as a fraction of the width. */
    hollowRadius: Float = 0.4f,
) {
    val time = rememberMotionClock()
    val c = Mx.colors
    val field = remember(nodes, seed) {
        val rnd = Random(seed)
        List(nodes) {
            Node(
                rnd.nextFloat(), rnd.nextFloat(),
                0.02f + rnd.nextFloat() * 0.05f, 0.02f + rnd.nextFloat() * 0.05f,
                0.15f + rnd.nextFloat() * 0.35f, 0.15f + rnd.nextFloat() * 0.35f,
                rnd.nextFloat() * 6.28f, 1.2f + rnd.nextFloat() * 1.4f, rnd.nextFloat() < 0.16f,
            )
        }
    }
    val lineA = if (c.isDark) 0.55f else 0.4f
    Canvas(modifier) {
        val t = time.value
        val w = size.width
        val h = size.height
        val pos = field.map { n -> Offset((n.x + n.ax * sin(t * n.fx + n.ph)) * w, (n.y + n.ay * cos(t * n.fy + n.ph)) * h) }
        val reach = 92.dp.toPx()
        val hc = hollow?.let { Offset(it.x * w, it.y * h) }
        val hr = hollowRadius * w
        fun keep(p: Offset): Float {
            if (hc == null) return 1f
            val d = hypot(p.x - hc.x, p.y - hc.y) / hr
            return ((d - 0.55f) / 0.45f).coerceIn(0f, 1f)
        }
        val line = 1.dp.toPx()
        for (i in pos.indices) for (j in i + 1 until pos.size) {
            val d = hypot(pos[i].x - pos[j].x, pos[i].y - pos[j].y)
            if (d < reach) {
                val a = (1f - d / reach) * lineA * intensity * minOf(keep(pos[i]), keep(pos[j]))
                val col = lerp(c.glowStart, c.glowEnd, ((pos[i].x + pos[j].x) / 2f / w).coerceIn(0f, 1f))
                drawLine(col.copy(alpha = a), pos[i], pos[j], line)
            }
        }
        field.forEachIndexed { i, n ->
            val p = pos[i]
            val k = keep(p)
            if (k <= 0f) return@forEachIndexed
            val pulse = 0.5f + 0.5f * sin(t * 1.6f + n.ph)
            val col = lerp(c.glowStart, c.glowEnd, (p.x / w).coerceIn(0f, 1f))
            if (n.hub) {
                val rr = (7f + 5f * pulse).dp.toPx()
                drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.55f * intensity * k), col.clear), p, rr), rr, p)
            }
            drawCircle(Color.White.copy(alpha = (0.55f + 0.4f * pulse) * intensity * k), n.r.dp.toPx() * (if (n.hub) 1.5f else 1f), p)
        }
    }
}

// ================================================================ Glass

/** Soft coloured halo behind a surface; draws outside the bounds, so place it before clip. */
fun Modifier.softGlow(start: Color, end: Color = start, alpha: Float = 0.35f, corner: Dp? = 24.dp): Modifier = drawBehind {
    val w = size.width
    val h = size.height
    val r = corner?.toPx() ?: (minOf(w, h) / 2)
    val hole = androidx.compose.ui.graphics.Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, w, h, androidx.compose.ui.geometry.CornerRadius(r, r)))
    }
    clipPath(hole, androidx.compose.ui.graphics.ClipOp.Difference) {
    fun halo(color: Color, cx: Float) {
        scale(1f, h / w * 1.25f, Offset(cx, h / 2)) {
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), color.clear), Offset(cx, h / 2), w * 0.55f), w * 0.55f, Offset(cx, h / 2))
        }
    }
    halo(start, w * 0.3f)
    halo(end, w * 0.7f)
    }
}

/**
 * The glass surface used by every card: translucent fill, a top sheen and a violet→cyan hairline.
 * With [onClick] it also scales on press and runs a holographic shimmer sweep across the card.
 */
@Composable
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(24.dp),
    fill: Color = Mx.colors.card,
    glow: Boolean = false,
    glowCorner: Dp? = 24.dp,
    hairline: Boolean = true,
    pressScale: Boolean = true,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
): Modifier {
    val c = Mx.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && pressScale) 0.975f else 1f, spring(stiffness = 700f), label = "press")
    val sweep = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(source) {
        source.interactions.collect { i ->
            if (i is PressInteraction.Press) scope.launch {
                sweep.snapTo(0f)
                sweep.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
            }
        }
    }
    val sheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = if (c.isDark) 0.06f else 0.35f), Color.White.clear))
    var m = this
    if (glow) m = m.softGlow(c.glowStart, c.glowEnd, if (c.isDark) 0.28f else 0.18f, glowCorner)
    m = m.graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape)
        .background(fill)
        .background(sheen)
    if (hairline) m = m.border(1.dp, c.hairline, shape)
    if (onClick != null) {
        m = m.clickable(interactionSource = source, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
            .holoSweep { sweep.value }
    }
    return m
}

/** Holographic band (cyan → white → magenta) that sweeps once across the content. */
fun Modifier.holoSweep(progress: () -> Float): Modifier = drawWithContent {
    drawContent()
    val p = progress()
    if (p <= 0f || p >= 1f) return@drawWithContent
    val w = size.width
    val band = w * 0.45f
    val x = -band + (w + band * 2) * p
    val fade = sin(p * PI.toFloat())
    rotate(18f) {
        drawRect(
            Brush.horizontalGradient(
                listOf(Aurora.cyan.clear, Aurora.cyan.copy(alpha = 0.16f * fade), Color.White.copy(alpha = 0.22f * fade), Aurora.magenta.copy(alpha = 0.14f * fade), Aurora.magenta.clear),
                startX = x, endX = x + band,
            ),
            topLeft = Offset(-w, -size.height), size = Size(w * 3, size.height * 3),
        )
    }
}

/** Press-scale and shimmer for something that is not a glass card (tiles, chips, rows). */
@Composable
fun Modifier.pressFx(enabled: Boolean = true, scaleOnPress: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && scaleOnPress) 0.96f else 1f, spring(stiffness = 700f), label = "press")
    val sweep = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(source) {
        source.interactions.collect { i ->
            if (i is PressInteraction.Press) scope.launch { sweep.snapTo(0f); sweep.animateTo(1f, tween(700)) }
        }
    }
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
        .holoSweep { sweep.value }
}

// ================================================================ Buttons

@Composable
fun GradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    solid: Color? = null,
    height: Dp = 52.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 22.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val c = Mx.colors
    val start = solid ?: c.fillStart
    val end = solid ?: c.fillEnd
    val glowA = solid ?: c.glowStart
    val glowB = solid ?: c.glowEnd
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .heightIn(min = height)
            .then(if (enabled) Modifier.softGlow(glowA, glowB, if (c.isDark) 0.42f else 0.25f, null) else Modifier)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(start, end)))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.clear, Color.Black.copy(alpha = 0.08f))))
            .border(1.dp, Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.08f))), shape)
            .pressFx(enabled = enabled, onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                content = {
                    androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)) {
                        content()
                    }
                },
            )
        }
    }
}

/** Glass outline pill (secondary action, e.g. "Continue with Google"). */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 52.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val c = Mx.colors
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .heightIn(min = height)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .clip(shape)
            .background(c.card)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (c.isDark) 0.07f else 0.4f), Color.White.clear)))
            .border(1.dp, c.hairline, shape)
            .pressFx(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontFamily = Grotesk, fontSize = 16.sp, color = c.text)) {
                content()
            }
        }
    }
}

// ================================================================ Text

@Composable
fun GradientText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    brush: Brush = Mx.colors.glowBrush3,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
) {
    Text(text, modifier, style = style.copy(brush = brush), maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = textAlign)
}

/** Light, readable gradient for large screen titles (white → lavender → ice). */
@Composable
fun titleBrush(): Brush {
    val c = Mx.colors
    return if (c.isDark) {
        Brush.linearGradient(listOf(Color.White, Color(0xFFD9D1FF), Color(0xFFB9F3FF)))
    } else {
        Brush.linearGradient(listOf(Color(0xFF0E1024), Color(0xFF3A2A9E), Color(0xFF0B6A8F)))
    }
}

// ================================================================ Rings & orbs

/**
 * Gradient progress ring. [orbit] adds a small satellite that circles the ring continuously and a
 * glowing head at the end of the arc.
 */
@Composable
fun GlowRing(
    progress: Float,
    modifier: Modifier = Modifier,
    stroke: Dp = 10.dp,
    colors: List<Color> = listOf(Mx.colors.glowStart, Mx.colors.glowMid, Mx.colors.glowEnd, Mx.colors.glowStart),
    track: Color = Mx.colors.separator,
    orbit: Boolean = false,
    ticks: Int = 0,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val time = rememberMotionClock()
    val head = colors.getOrElse(2) { colors.last() }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val inset = sw / 2 + (if (orbit) 6.dp.toPx() else 0f)
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val tl = Offset(inset, inset)
            val center = Offset(size.width / 2, size.height / 2)
            val radius = arcSize.width / 2
            if (ticks > 0) {
                val r1 = radius + sw / 2 + 3.dp.toPx()
                val r2 = r1 + 4.dp.toPx()
                for (i in 0 until ticks) {
                    val a = (i.toFloat() / ticks) * 2 * PI.toFloat()
                    val lit = i.toFloat() / ticks <= progress
                    drawLine(
                        (if (lit) head else track).copy(alpha = if (lit) 0.7f else 0.6f),
                        Offset(center.x + cos(a - PI.toFloat() / 2) * r1, center.y + sin(a - PI.toFloat() / 2) * r1),
                        Offset(center.x + cos(a - PI.toFloat() / 2) * r2, center.y + sin(a - PI.toFloat() / 2) * r2),
                        1.2.dp.toPx(), cap = StrokeCap.Round,
                    )
                }
            }
            drawArc(track, 0f, 360f, false, tl, arcSize, style = Stroke(sw))
            val sweepAngle = 360f * progress.coerceIn(0f, 1f)
            rotate(-90f, center) {
                val brush = Brush.sweepGradient(colors, center)
                // Glow under the arc.
                drawArc(brush, 0f, sweepAngle, false, tl, arcSize, alpha = 0.35f, style = Stroke(sw * 2.2f, cap = StrokeCap.Round))
                drawArc(brush, 0f, sweepAngle, false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            }
            if (progress > 0.01f) {
                val a = (sweepAngle - 90f) * PI.toFloat() / 180f
                val p = Offset(center.x + cos(a) * radius, center.y + sin(a) * radius)
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), head.copy(alpha = 0.5f), head.clear), p, sw * 1.6f), sw * 1.6f, p)
                drawCircle(Color.White, sw * 0.32f, p)
            }
            if (orbit) {
                val ro = radius + sw / 2 + 5.dp.toPx()
                val a = time.value * 0.9f
                val p = Offset(center.x + cos(a) * ro, center.y + sin(a) * ro)
                drawCircle(Brush.radialGradient(listOf(Aurora.cyan.copy(alpha = 0.8f), Aurora.cyan.clear), p, 7.dp.toPx()), 7.dp.toPx(), p)
                drawCircle(Color.White, 2.2.dp.toPx(), p)
                // Faint orbit path.
                drawCircle(Color.White.copy(alpha = 0.06f), ro, center, style = Stroke(1.dp.toPx()))
            }
        }
        content()
    }
}

/** A pulsing "AI core": a luminous orb inside a progress ring; [met] turns it emerald. */
@Composable
fun AiCoreOrb(progress: Float, met: Boolean, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = Mx.colors
    val time = rememberMotionClock()
    val a = if (met) c.correct else c.glowStart
    val b = if (met) Aurora.cyan else c.glowEnd
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val t = time.value
            val pulse = 0.5f + 0.5f * sin(t * 2.2f)
            val center = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension / 2 * 0.62f
            // Halo breathes.
            val halo = r * (1.35f + 0.12f * pulse)
            drawCircle(Brush.radialGradient(listOf(a.copy(alpha = 0.45f), b.copy(alpha = 0.12f), b.clear), center, halo), halo, center)
            // Core.
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.95f), lerp(a, Color.White, 0.35f), a, lerp(b, Color.Black, 0.35f)),
                    Offset(center.x - r * 0.3f, center.y - r * 0.35f), r * 1.5f,
                ),
                r * (0.97f + 0.03f * pulse), center,
            )
            // Inner rotating filaments.
            rotate(t * 30f, center) {
                drawArc(Color.White.copy(alpha = 0.35f), 20f, 80f, false, Offset(center.x - r * 0.7f, center.y - r * 0.7f), Size(r * 1.4f, r * 1.4f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
                drawArc(Color.White.copy(alpha = 0.2f), 200f, 110f, false, Offset(center.x - r * 0.5f, center.y - r * 0.5f), Size(r * 1.0f, r * 1.0f), style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round))
            }
            // Darken the middle so the figure on top stays readable.
            drawCircle(Brush.radialGradient(listOf(Color(0xFF0B0820).copy(alpha = 0.55f), Color(0xFF0B0820).clear), center, r * 0.85f), r * 0.85f, center)
        }
        GlowRing(progress, Modifier.fillMaxSize(), stroke = 6.dp, colors = listOf(a, b, Color.White, a), track = Color.White.copy(alpha = if (c.isDark) 0.08f else 0.4f))
        content()
    }
}

// ================================================================ Icon chips

/** The one icon treatment: rounded glass chip with a tint gradient, hairline and tinted glyph. */
@Composable
fun IconChip(icon: ImageVector, tint: Color, size: Dp = 36.dp, modifier: Modifier = Modifier) {
    val c = Mx.colors
    val glyph = if (c.isDark) lerp(tint, Color.White, 0.15f) else lerp(tint, Color.Black, 0.3f)
    val shape = RoundedCornerShape(size * 0.32f)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(tint.copy(alpha = if (c.isDark) 0.32f else 0.24f), tint.copy(alpha = if (c.isDark) 0.08f else 0.08f))))
            .border(1.dp, Brush.linearGradient(listOf(tint.copy(alpha = 0.7f), tint.copy(alpha = 0.12f))), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = glyph, modifier = Modifier.size(size * 0.56f))
    }
}

/** A distinct icon per FMGE subject. */
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
        "ent" == n || "ent " in n || "otorhino" in n || n.startsWith("ent") -> Icons.Rounded.Hearing
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

// ================================================================ Segmented

@Composable
fun GlassSegmented(
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
            .background(c.card)
            .border(1.dp, c.hairline, RoundedCornerShape(50))
            .padding(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(50))
                    .then(if (on) Modifier.background(c.fillBrush) else Modifier)
                    .then(if (on) Modifier.background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.2f), Color.White.clear))) else Modifier)
                    .clickable(enabled = enabled(i)) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        on -> Color.White
                        !enabled(i) -> c.tertiaryText.copy(alpha = 0.6f)
                        else -> c.secondaryText
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

// ================================================================ Bottom bar

class NavItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector = icon)

/** Floating glass navigation bar with a glowing gradient pill behind the selected tab. */
@Composable
fun GlassBottomBar(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit) {
    val c = Mx.colors
    val shape = RoundedCornerShape(30.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .softGlow(c.glowStart, c.glowEnd, if (c.isDark) 0.14f else 0.08f, 30.dp)
                .clip(shape)
                .background(if (c.isDark) c.raised.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.9f))
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (c.isDark) 0.06f else 0.3f), Color.White.clear)))
                .border(1.dp, c.hairline, shape)
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            items.forEachIndexed { i, item ->
                val on = i == selected
                val glowAlpha by animateFloatAsState(if (on) 1f else 0f, tween(260), label = "tab")
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable { onSelect(i) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(width = 54.dp, height = 32.dp)
                            .drawBehind {
                                if (glowAlpha > 0f) {
                                    val r = size.width * 0.75f
                                    drawCircle(Brush.radialGradient(listOf(c.glowStart.copy(alpha = 0.45f * glowAlpha), c.glowStart.clear), center, r), r, center)
                                }
                            }
                            .clip(RoundedCornerShape(50))
                            .then(if (on) Modifier.background(c.fillBrush) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (on) item.selectedIcon else item.icon, null,
                            tint = if (on) Color.White else c.secondaryText,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Medium),
                        color = if (on) c.text else c.secondaryText,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

// ================================================================ Burst

/** One-shot radial burst (ring + sparks) used when an answer is right. [play] starts it. */
@Composable
fun Burst(play: Boolean, color: Color, modifier: Modifier = Modifier) {
    val p = remember { Animatable(1f) }
    val armed = remember { mutableStateOf(!play) }
    LaunchedEffect(play) {
        if (play && armed.value) { p.snapTo(0f); p.animateTo(1f, tween(700, easing = LinearEasing)) }
        if (!play) armed.value = true
    }
    Canvas(modifier) {
        val v = p.value
        if (v <= 0f || v >= 1f) return@Canvas
        val center = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 * (0.6f + 1.6f * v)
        drawCircle(color.copy(alpha = 0.7f * (1f - v)), r, center, style = Stroke(3.dp.toPx() * (1f - v) + 0.5f))
        repeat(10) { i ->
            val a = i / 10f * 2 * PI.toFloat() + 0.3f
            val d = size.minDimension / 2 * (0.8f + 2.2f * v)
            drawCircle(Color.White.copy(alpha = 1f - v), 2.dp.toPx() * (1f - v * 0.6f), Offset(center.x + cos(a) * d, center.y + sin(a) * d))
        }
    }
}

/** Utility used in a couple of places: draws [block] inside a translate. */
internal inline fun DrawScope.at(x: Float, y: Float, block: DrawScope.() -> Unit) = translate(x, y, block)

// ================================================================ Chips & bars

/** Filter chip: glass when idle, gradient-filled with a glow when selected. */
@Composable
fun GlowChip(selected: Boolean, onClick: () -> Unit, label: String, icon: ImageVector? = null) {
    val c = Mx.colors
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .then(if (selected) Modifier.softGlow(c.glowStart, c.glowEnd, 0.25f, null) else Modifier)
            .clip(shape)
            .then(if (selected) Modifier.background(c.fillBrush) else Modifier.background(c.card))
            .border(1.dp, if (selected) Brush.linearGradient(listOf(Color.White.copy(alpha = 0.4f), Color.White.copy(alpha = 0.1f))) else c.hairline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) Color.White else c.accent, modifier = Modifier.size(16.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else c.text)
    }
}

/** Thin gradient progress bar with a glowing head. */
@Composable
fun GradientBar(progress: Float, modifier: Modifier = Modifier, height: Dp = 6.dp, colors: List<Color>? = null, track: Color = Mx.colors.separator) {
    val c = Mx.colors
    val stops = colors ?: listOf(c.glowStart, c.glowEnd)
    Canvas(modifier.height(height)) {
        val h = size.height
        val r = androidx.compose.ui.geometry.CornerRadius(h / 2, h / 2)
        drawRoundRect(track, cornerRadius = r)
        val w = size.width * progress.coerceIn(0f, 1f)
        if (w > 0f) {
            drawRoundRect(Brush.horizontalGradient(stops, 0f, size.width), size = Size(maxOf(w, h), h), cornerRadius = r)
            val p = Offset(maxOf(w, h) - h / 2, h / 2)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.8f), stops.last().copy(alpha = 0.3f), stops.last().clear), p, h * 1.8f), h * 1.8f, p)
        }
    }
}

@Composable
fun neonSwitchColors(): androidx.compose.material3.SwitchColors {
    val c = Mx.colors
    return androidx.compose.material3.SwitchDefaults.colors(
        checkedThumbColor = Color.White, checkedTrackColor = c.fillStart, checkedBorderColor = c.glowEnd.copy(alpha = 0.6f),
        checkedIconColor = c.fillStart,
        uncheckedThumbColor = c.secondaryText, uncheckedTrackColor = c.card, uncheckedBorderColor = c.secondaryText.copy(alpha = 0.5f),
    )
}
