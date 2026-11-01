package quest.srihari.medxelite.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import quest.srihari.medxelite.R
import quest.srihari.medxelite.data.MedxAccent
import quest.srihari.medxelite.data.MedxAppearance

/*
 * MedX "Clarity" design system (design v3). Warm paper canvas, white cards with soft elevation,
 * one confident brand colour, clear semantic colours. See design-v3-rationale.md.
 */

@Immutable
data class MedxColors(
    /** Page canvas (kept under its old name so every screen picks it up). */
    val groupedBackground: Color,
    /** Card / sheet surface. */
    val card: Color,
    /** Hairline between rows. Use sparingly: space and shade separate groups. */
    val separator: Color,
    val secondaryText: Color,
    val tertiaryText: Color,
    val correct: Color,
    val wrong: Color,
    val flagged: Color,
    val accent: Color,
    val isDark: Boolean,
    // ---- v3 additions
    val ink: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val sunken: Color,
    val raised: Color,
    val correctContainer: Color,
    val wrongContainer: Color,
    val flaggedContainer: Color,
    val streak: Color,
    val shadow: Color,
)

private val LightBase = MedxColors(
    groupedBackground = Color(0xFFF7F6F3), card = Color.White, separator = Color(0xFFE7E5E0),
    secondaryText = Color(0xFF5E616A), tertiaryText = Color(0xFF6F727A),
    correct = Color(0xFF13814D), wrong = Color(0xFFC9303A), flagged = Color(0xFF8F5600),
    accent = Color(0xFF3156E3), isDark = false,
    ink = Color(0xFF17181C), onAccent = Color.White,
    accentContainer = Color(0xFFE9EDFF), onAccentContainer = Color(0xFF1B2E8F),
    sunken = Color(0xFFEFEEEA), raised = Color.White,
    correctContainer = Color(0xFFE3F5EC), wrongContainer = Color(0xFFFDECEC), flaggedContainer = Color(0xFFFFF3DC),
    streak = Color(0xFFF2683C), shadow = Color(0xFF1C1A16),
)

private val DarkBase = MedxColors(
    groupedBackground = Color(0xFF101114), card = Color(0xFF1A1B1F), separator = Color(0xFF2C2D33),
    secondaryText = Color(0xFFA5A8B1), tertiaryText = Color(0xFF8A8D96),
    correct = Color(0xFF56D394), wrong = Color(0xFFFF7A7A), flagged = Color(0xFFF5B547),
    accent = Color(0xFF8FA5FF), isDark = true,
    ink = Color(0xFFF2F2F4), onAccent = Color(0xFF0E1A57),
    accentContainer = Color(0xFF243161), onAccentContainer = Color(0xFFDDE3FF),
    sunken = Color(0xFF0B0C0E), raised = Color(0xFF24252A),
    correctContainer = Color(0xFF173828), wrongContainer = Color(0xFF3D1C1F), flaggedContainer = Color(0xFF3A2C12),
    streak = Color(0xFFFF8A5C), shadow = Color.Black,
)

val LocalMedxColors = staticCompositionLocalOf { LightBase }

/** True when the system "Remove animations" setting is on (or a test pins motion off). */
val LocalReduceMotion = staticCompositionLocalOf { false }

object Mx {
    val colors: MedxColors @Composable get() = LocalMedxColors.current
    val reduceMotion: Boolean @Composable get() = LocalReduceMotion.current
}

/** Brand tones per accent: (light primary, light container, dark primary, dark container). */
private fun tones(accent: MedxAccent): List<Long> = when (accent) {
    MedxAccent.BLUE -> listOf(0xFF3156E3, 0xFFE9EDFF, 0xFF8FA5FF, 0xFF243161)
    MedxAccent.INDIGO -> listOf(0xFF5146D9, 0xFFECEAFF, 0xFFA9A2FF, 0xFF2D2A5E)
    MedxAccent.PURPLE -> listOf(0xFF8A3FD6, 0xFFF4EAFF, 0xFFD0A6FF, 0xFF3A2556)
    MedxAccent.PINK -> listOf(0xFFCC2F6A, 0xFFFFE8F0, 0xFFFF8FB5, 0xFF4A1F31)
    MedxAccent.TEAL -> listOf(0xFF0B7F86, 0xFFDFF5F6, 0xFF5FD3DA, 0xFF163B3E)
    MedxAccent.GREEN -> listOf(0xFF1C8550, 0xFFE2F4E9, 0xFF6FD99E, 0xFF183828)
    MedxAccent.ORANGE -> listOf(0xFFC2560A, 0xFFFFEDDF, 0xFFFFAA66, 0xFF452A15)
    MedxAccent.GRAPHITE -> listOf(0xFF555A66, 0xFFEBECEF, 0xFFB9BCC6, 0xFF2E3036)
}

/** Subject / feature hues, tuned to read on both canvases (glyph colour, used on a tint). */
object Hues {
    val blue = Color(0xFF3B6EF0)
    val indigo = Color(0xFF6157E8)
    val purple = Color(0xFF9550E0)
    val pink = Color(0xFFE0457E)
    val red = Color(0xFFE5484D)
    val orange = Color(0xFFF07A2E)
    val yellow = Color(0xFFE0A100)
    val green = Color(0xFF22A06B)
    val mint = Color(0xFF14A89A)
    val teal = Color(0xFF0E9CB0)
    val cyan = Color(0xFF2A9BD8)
    val brown = Color(0xFFA9774D)
    val gray = Color(0xFF7A7F8C)

    private val cycle = listOf(blue, purple, pink, orange, green, teal, indigo, red, mint, cyan, brown, yellow)
    fun forName(name: String): Color = cycle[Math.floorMod(name.hashCode(), cycle.size)]
}

// ---------------------------------------------------------------- Type

val Jakarta = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
)

private val trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun display(size: Int, line: Int, weight: FontWeight = FontWeight.Bold, track: Double = -0.01) =
    TextStyle(fontFamily = Jakarta, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = track.em, lineHeightStyle = trim)

private fun body(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = 0.sp, lineHeightStyle = trim)

val MedxType = Typography(
    displayLarge = display(56, 60, FontWeight.ExtraBold, -0.03),
    displayMedium = display(44, 50, FontWeight.ExtraBold, -0.025),
    displaySmall = display(36, 42, FontWeight.ExtraBold, -0.02),
    headlineLarge = display(32, 38, FontWeight.Bold, -0.02),
    headlineMedium = display(28, 34, FontWeight.Bold, -0.015),
    headlineSmall = display(24, 30, FontWeight.Bold, -0.01),
    titleLarge = display(20, 26, FontWeight.Bold, -0.005),
    titleMedium = display(17, 24, FontWeight.SemiBold, 0.0),
    titleSmall = display(15, 20, FontWeight.SemiBold, 0.0),
    bodyLarge = body(17, 26),
    bodyMedium = body(15, 22),
    bodySmall = body(13, 18),
    labelLarge = display(15, 20, FontWeight.SemiBold, 0.0),
    labelMedium = display(13, 18, FontWeight.SemiBold, 0.005),
    labelSmall = display(12, 16, FontWeight.SemiBold, 0.01),
)

val MedxShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

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
    val noMotion = reduceMotion ?: remember(context) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    val t = tones(accent)
    val base = if (dark) DarkBase else LightBase
    val medx = if (dark) {
        base.copy(
            accent = Color(t[2]), accentContainer = Color(t[3]),
            onAccent = Color(0xFF0B0F24), onAccentContainer = Color(0xFFF0F2FF),
        )
    } else {
        base.copy(accent = Color(t[0]), accentContainer = Color(t[1]), onAccentContainer = Color(t[0]).darken(0.35f))
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = medx.accent, onPrimary = medx.onAccent,
            primaryContainer = medx.accentContainer, onPrimaryContainer = medx.onAccentContainer,
            secondary = medx.accent, onSecondary = medx.onAccent,
            secondaryContainer = medx.accentContainer, onSecondaryContainer = medx.onAccentContainer,
            tertiary = medx.streak,
            background = medx.groupedBackground, onBackground = medx.ink,
            surface = medx.groupedBackground, onSurface = medx.ink,
            surfaceVariant = medx.raised, onSurfaceVariant = medx.secondaryText,
            surfaceContainerLowest = medx.sunken, surfaceContainerLow = medx.card,
            surfaceContainer = medx.card, surfaceContainerHigh = medx.raised, surfaceContainerHighest = Color(0xFF2E2F35),
            surfaceBright = medx.raised, surfaceDim = medx.groupedBackground,
            outline = Color(0xFF5A5C64), outlineVariant = medx.separator,
            error = medx.wrong, onError = Color(0xFF3B0A0E), errorContainer = medx.wrongContainer, onErrorContainer = Color(0xFFFFD9D9),
            inverseSurface = medx.ink, inverseOnSurface = medx.groupedBackground, inversePrimary = Color(t[0]),
            scrim = Color.Black,
        )
    } else {
        lightColorScheme(
            primary = medx.accent, onPrimary = medx.onAccent,
            primaryContainer = medx.accentContainer, onPrimaryContainer = medx.onAccentContainer,
            secondary = medx.accent, onSecondary = medx.onAccent,
            secondaryContainer = medx.accentContainer, onSecondaryContainer = medx.onAccentContainer,
            tertiary = medx.streak,
            background = medx.groupedBackground, onBackground = medx.ink,
            surface = medx.groupedBackground, onSurface = medx.ink,
            surfaceVariant = medx.sunken, onSurfaceVariant = medx.secondaryText,
            surfaceContainerLowest = Color.White, surfaceContainerLow = medx.card,
            surfaceContainer = medx.card, surfaceContainerHigh = Color.White, surfaceContainerHighest = medx.sunken,
            surfaceBright = Color.White, surfaceDim = medx.sunken,
            outline = Color(0xFF8A8D94), outlineVariant = medx.separator,
            error = medx.wrong, onError = Color.White, errorContainer = medx.wrongContainer, onErrorContainer = Color(0xFF7A1A21),
            inverseSurface = Color(0xFF2A2B30), inverseOnSurface = Color(0xFFF2F2F4), inversePrimary = Color(t[2]),
            scrim = Color.Black,
        )
    }
    CompositionLocalProvider(LocalMedxColors provides medx, LocalReduceMotion provides noMotion) {
        MaterialTheme(colorScheme = scheme, typography = MedxType, shapes = MedxShapes, content = content)
    }
}

fun Color.darken(f: Float) = Color(red * (1 - f), green * (1 - f), blue * (1 - f), alpha)

/** A soft tint of [hue] for icon tiles and chips, readable on the current canvas. */
@Composable
fun tint(hue: Color): Color = hue.copy(alpha = if (Mx.colors.isDark) 0.20f else 0.12f)

/** The glyph colour that sits on [tint] of [hue]: brighter in dark, deeper in light. */
@Composable
fun onTint(hue: Color): Color = if (Mx.colors.isDark) lerpTo(hue, Color.White, 0.30f) else hue.darken(0.12f)

private fun lerpTo(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f,
)
