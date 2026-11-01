package quest.srihari.medxelite.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import quest.srihari.medxelite.R
import quest.srihari.medxelite.data.MedxAccent
import quest.srihari.medxelite.data.MedxAppearance
import quest.srihari.medxelite.ui.components.AuroraBackground

/**
 * "Neural Aurora" palette. Field names from the original iOS-style palette are kept so every
 * screen keeps compiling; [groupedBackground] is now transparent because the aurora backdrop is
 * painted once, under every screen, by [MedxTheme].
 */
@Immutable
data class MedxColors(
    val groupedBackground: Color,
    /** Glass card fill (translucent). */
    val card: Color,
    val separator: Color,
    val secondaryText: Color,
    val tertiaryText: Color,
    val correct: Color,
    val wrong: Color,
    val flagged: Color,
    /** Accent tuned for text and icons on the current background (always readable). */
    val accent: Color,
    val isDark: Boolean,
    /** Opaque base behind the aurora; also used for sheets, bars and dialogs. */
    val base: Color = Color(0xFF070A14),
    /** Slightly raised opaque surface (dialogs, sheets, floating bar). */
    val raised: Color = Color(0xFF111628),
    /** Stronger glass for pressed / selected / nested surfaces. */
    val glassStrong: Color = Color(0x1FFFFFFF),
    /** Bright gradient stops for rings, numbers and hairlines. */
    val glowStart: Color = Aurora.violet,
    val glowEnd: Color = Aurora.cyan,
    val glowMid: Color = Aurora.magenta,
    /** Deep gradient stops for filled buttons (white text stays ≥ 4.5:1 on both ends). */
    val fillStart: Color = Color(0xFF6D4AFF),
    val fillEnd: Color = Color(0xFF0B78B0),
    val text: Color = Color(0xFFF4F6FF),
) {
    val glowBrush: Brush get() = Brush.linearGradient(listOf(glowStart, glowEnd))
    val glowBrush3: Brush get() = Brush.linearGradient(listOf(glowStart, glowMid, glowEnd))
    val fillBrush: Brush get() = Brush.horizontalGradient(listOf(fillStart, fillEnd))
    val hairline: Brush get() = Brush.linearGradient(
        listOf(glowStart.copy(alpha = if (isDark) 0.55f else 0.45f), Color.White.copy(alpha = if (isDark) 0.06f else 0.5f), glowEnd.copy(alpha = if (isDark) 0.45f else 0.4f)),
    )
}

object Aurora {
    val violet = Color(0xFF7C5CFF)
    val cyan = Color(0xFF22D3EE)
    val magenta = Color(0xFFE879F9)
    val emerald = Color(0xFF34D399)
    val rose = Color(0xFFFB7185)
    val amber = Color(0xFFFBBF24)
    val night = Color(0xFF070A14)
}

val LocalMedxColors = staticCompositionLocalOf {
    MedxColors(
        Color.Transparent, Color(0x12FFFFFF), Color(0x1FFFFFFF), Color(0xFFA9B0C8), Color(0xFF7D84A0),
        Aurora.emerald, Aurora.rose, Aurora.amber, Color(0xFFA996FF), true,
    )
}

/** True when animations should hold still (system "remove animations", or screenshot runs). */
val LocalReduceMotion = compositionLocalOf { false }

object Mx {
    val colors: MedxColors @Composable get() = LocalMedxColors.current
    val reduceMotion: Boolean @Composable get() = LocalReduceMotion.current
}

/** Hues for tiles and subject icons; brighter, neon-leaning versions of the iOS set. */
object Hues {
    val blue = Color(0xFF60A5FA)
    val indigo = Color(0xFF818CF8)
    val purple = Color(0xFFC084FC)
    val pink = Color(0xFFF472B6)
    val red = Color(0xFFFB7185)
    val orange = Color(0xFFFB923C)
    val yellow = Color(0xFFFACC15)
    val green = Color(0xFF34D399)
    val mint = Color(0xFF5EEAD4)
    val teal = Color(0xFF2DD4BF)
    val cyan = Color(0xFF22D3EE)
    val brown = Color(0xFFD6A77A)
    val gray = Color(0xFFA1A1AA)

    private val cycle = listOf(blue, purple, pink, orange, green, teal, indigo, red, mint, cyan, brown, yellow)
    fun forName(name: String): Color = cycle[Math.floorMod(name.hashCode(), cycle.size)]
}

// ---------------------------------------------------------------- Type

val Grotesk = FontFamily(
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_medium, FontWeight.Normal),
    Font(R.font.space_grotesk_bold, FontWeight.SemiBold),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
    Font(R.font.space_grotesk_bold, FontWeight.ExtraBold),
    Font(R.font.space_grotesk_bold, FontWeight.Black),
)
val MonoFigures = FontFamily(Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold))

/** Tabular figures for stats and timers. */
val StatStyle = TextStyle(fontFamily = MonoFigures, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")

private fun medxTypography(): Typography {
    val base = Typography()
    fun TextStyle.display(size: Int, tracking: Double) =
        copy(fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = size.sp, letterSpacing = tracking.sp)
    return base.copy(
        displayLarge = base.displayLarge.display(54, -1.5),
        displayMedium = base.displayMedium.display(44, -1.2),
        displaySmall = base.displaySmall.display(36, -0.9),
        headlineLarge = base.headlineLarge.display(32, -0.8),
        headlineMedium = base.headlineMedium.display(28, -0.6),
        headlineSmall = base.headlineSmall.display(24, -0.4),
        titleLarge = base.titleLarge.display(22, -0.3),
        titleMedium = base.titleMedium.copy(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
        titleSmall = base.titleSmall.copy(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
        labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    )
}

private val medxShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// ---------------------------------------------------------------- Colour helpers

private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

/** Darkens [c] until white text on it reaches 4.6:1. */
internal fun deepenForWhite(c: Color): Color {
    var x = c
    repeat(20) { if (contrast(Color.White, x) >= 4.6f) return x; x = lerp(x, Color.Black, 0.07f) }
    return x
}

/** Lightens (or darkens) [c] until it reads at 4.6:1 on [bg]. */
private fun readableOn(c: Color, bg: Color): Color {
    var x = c
    val toward = if (bg.luminance() < 0.5f) Color.White else Color.Black
    repeat(20) { if (contrast(x, bg) >= 4.6f) return x; x = lerp(x, toward, 0.08f) }
    return x
}

// ---------------------------------------------------------------- Theme

@Composable
fun MedxTheme(
    accent: MedxAccent,
    appearance: MedxAppearance,
    reduceMotion: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val dark = when (appearance) {
        MedxAppearance.SYSTEM -> isSystemInDarkTheme()
        MedxAppearance.LIGHT -> false
        MedxAppearance.DARK -> true
    }
    val context = LocalContext.current
    val motionOff = reduceMotion ?: remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    // The accent feeds the start of every gradient; the cyan end stays unless the accent is itself cyan-green.
    val start = Color(accent.hex)
    val end = if (accent == MedxAccent.TEAL || accent == MedxAccent.GREEN) Aurora.violet else Aurora.cyan
    val medx = if (dark) {
        val base = Aurora.night
        MedxColors(
            groupedBackground = Color.Transparent,
            card = Color.White.copy(alpha = 0.07f),
            separator = Color.White.copy(alpha = 0.09f),
            secondaryText = Color(0xFFA9B0C8),
            tertiaryText = Color(0xFF8189A6),
            correct = Aurora.emerald, wrong = Aurora.rose, flagged = Aurora.amber,
            accent = readableOn(lerp(start, Color.White, 0.25f), Color(0xFF151A2B)),
            isDark = true,
            base = base, raised = Color(0xFF121729), glassStrong = Color.White.copy(alpha = 0.12f),
            glowStart = lerp(start, Color.White, 0.12f), glowEnd = end, glowMid = Aurora.magenta,
            fillStart = deepenForWhite(start), fillEnd = deepenForWhite(end),
            text = Color(0xFFF4F6FF),
        )
    } else {
        val base = Color(0xFFF3F1FF)
        MedxColors(
            groupedBackground = Color.Transparent,
            card = Color.White.copy(alpha = 0.72f),
            separator = Color(0xFF1B1446).copy(alpha = 0.09f),
            secondaryText = Color(0xFF4A4E6A),
            tertiaryText = Color(0xFF6B6F8C),
            correct = Color(0xFF059669), wrong = Color(0xFFE11D48), flagged = Color(0xFFD97706),
            accent = readableOn(start, Color.White),
            isDark = false,
            base = base, raised = Color.White, glassStrong = Color.White.copy(alpha = 0.92f),
            glowStart = deepenForWhite(start), glowEnd = readableOn(end, Color.White), glowMid = Color(0xFFC026D3),
            fillStart = deepenForWhite(start), fillEnd = deepenForWhite(end),
            text = Color(0xFF0E1024),
        )
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = medx.accent, onPrimary = Color(0xFF0B0820), secondary = medx.accent,
            background = Color.Transparent, onBackground = medx.text,
            surface = medx.base, onSurface = medx.text,
            surfaceContainerLowest = medx.base,
            surfaceContainer = medx.raised, surfaceContainerLow = medx.raised,
            surfaceContainerHigh = Color(0xFF171D33), surfaceContainerHighest = Color(0xFF1E2540),
            surfaceVariant = Color(0xFF1E2540), onSurfaceVariant = medx.secondaryText,
            secondaryContainer = start.copy(alpha = 0.30f), onSecondaryContainer = Color.White,
            primaryContainer = start.copy(alpha = 0.30f), onPrimaryContainer = Color.White,
            outline = Color.White.copy(alpha = 0.22f), outlineVariant = medx.separator,
            error = Aurora.rose,
        )
    } else {
        lightColorScheme(
            primary = medx.accent, onPrimary = Color.White, secondary = medx.accent,
            background = Color.Transparent, onBackground = medx.text,
            surface = medx.base, onSurface = medx.text,
            surfaceContainerLowest = Color.White,
            surfaceContainer = Color.White, surfaceContainerLow = Color.White,
            surfaceContainerHigh = Color(0xFFF7F5FF), surfaceContainerHighest = Color(0xFFEDEAFB),
            surfaceVariant = Color(0xFFEDEAFB), onSurfaceVariant = medx.secondaryText,
            secondaryContainer = start.copy(alpha = 0.16f), onSecondaryContainer = Color(0xFF0B0B1F),
            primaryContainer = start.copy(alpha = 0.16f), onPrimaryContainer = Color(0xFF0B0B1F),
            outline = Color(0xFF1B1446).copy(alpha = 0.25f), outlineVariant = medx.separator,
            error = Color(0xFFE11D48),
        )
    }
    CompositionLocalProvider(LocalMedxColors provides medx, LocalReduceMotion provides motionOff) {
        MaterialTheme(colorScheme = scheme, typography = medxTypography(), shapes = medxShapes) {
            CompositionLocalProvider(LocalContentColor provides medx.text) {
                Box(Modifier.fillMaxSize()) {
                    AuroraBackground()
                    content()
                }
            }
        }
    }
}
