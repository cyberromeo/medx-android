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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import quest.srihari.medxelite.R
import quest.srihari.medxelite.data.MedxAccent
import quest.srihari.medxelite.data.MedxAppearance

/**
 * Nothing-style palette: true black (or off-white) ground, flat grey widget surfaces, hairlines,
 * and one accent (Nothing red by default) kept for active states, wrong answers and key numbers.
 * Field names are kept from the earlier iOS-like palette so every screen keeps compiling.
 */
@Immutable
data class MedxColors(
    val groupedBackground: Color,
    val card: Color,
    val separator: Color,
    val secondaryText: Color,
    val tertiaryText: Color,
    /** Correct answers: the ink colour (white on black), shown as an inverted fill. */
    val correct: Color,
    /** Wrong answers and critical states. */
    val wrong: Color,
    /** Bookmarks, low clock, warnings. */
    val flagged: Color,
    val accent: Color,
    val isDark: Boolean,
    /** Primary text / inverted fills. */
    val ink: Color = if (isDark) Color.White else Color.Black,
    /** Text on an [ink] fill. */
    val onInk: Color = if (isDark) Color.Black else Color.White,
    /** A raised surface inside a widget (chips, tracks). */
    val raised: Color = card,
    /** Hairline borders around widgets. */
    val hairline: Color = separator,
    /** Unlit dots in dot-matrix progress. */
    val dotOff: Color = separator,
)

val LocalMedxColors = staticCompositionLocalOf { nothingColors(dark = true, accent = Color(0xFFD71921)) }

/** True when the user turned animations off (Developer options or Accessibility "Remove animations"). */
val LocalReduceMotion = staticCompositionLocalOf { false }

object Mx {
    val colors: MedxColors @Composable get() = LocalMedxColors.current
    val reduceMotion: Boolean @Composable get() = LocalReduceMotion.current
}

/** Type families: Doto (dot-matrix display), Inter (body), Space Mono (labels). All OFL, bundled. */
object NType {
    val dot = FontFamily(
        Font(R.font.doto_bold, FontWeight.Bold),
        Font(R.font.doto_black, FontWeight.Black),
    )
    val sans = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )
    val mono = FontFamily(
        Font(R.font.space_mono_regular, FontWeight.Normal),
        Font(R.font.space_mono_bold, FontWeight.Bold),
    )
}

/**
 * Tile hues. The Nothing look is monochrome, so these are greys; the few places that need to
 * tell two things apart use [Mx.colors] (ink vs accent) instead.
 */
object Hues {
    private val mid = Color(0xFF8E8E8E)
    val blue = mid
    val indigo = mid
    val purple = mid
    val pink = mid
    val red = Color(0xFFD71921)
    val orange = mid
    val yellow = mid
    val green = mid
    val mint = mid
    val teal = mid
    val cyan = mid
    val brown = mid
    val gray = mid

    fun forName(@Suppress("UNUSED_PARAMETER") name: String): Color = mid
}

fun nothingColors(dark: Boolean, accent: Color): MedxColors = if (dark) {
    MedxColors(
        groupedBackground = Color(0xFF000000),
        card = Color(0xFF111111),
        separator = Color(0xFF2A2A2A),
        secondaryText = Color(0xFFA6A6A6),
        tertiaryText = Color(0xFF7C7C7C),
        correct = Color.White,
        wrong = Color(0xFFEB2B34),
        flagged = accent,
        accent = accent,
        isDark = true,
        raised = Color(0xFF1C1C1C),
        hairline = Color(0xFF262626),
        dotOff = Color(0xFF2E2E2E),
    )
} else {
    MedxColors(
        groupedBackground = Color(0xFFEFEFEF),
        card = Color(0xFFFFFFFF),
        separator = Color(0xFFDADADA),
        secondaryText = Color(0xFF5C5C5C),
        tertiaryText = Color(0xFF767676),
        correct = Color.Black,
        wrong = Color(0xFFD71921),
        flagged = accent,
        accent = accent,
        isDark = false,
        raised = Color(0xFFF3F3F3),
        hairline = Color(0xFFDDDDDD),
        dotOff = Color(0xFFD6D6D6),
    )
}

private val NothingTypography: Typography
    get() {
        val sans = NType.sans
        val mono = NType.mono
        return Typography(
            displayLarge = TextStyle(fontFamily = NType.dot, fontWeight = FontWeight.Black, fontSize = 72.sp, lineHeight = 76.sp),
            displayMedium = TextStyle(fontFamily = NType.dot, fontWeight = FontWeight.Black, fontSize = 56.sp, lineHeight = 60.sp),
            displaySmall = TextStyle(fontFamily = NType.dot, fontWeight = FontWeight.Black, fontSize = 44.sp, lineHeight = 48.sp),
            // Expanded large top bars render their title in the dot-matrix face.
            headlineLarge = TextStyle(fontFamily = NType.dot, fontWeight = FontWeight.Black, fontSize = 40.sp, lineHeight = 44.sp),
            headlineMedium = TextStyle(fontFamily = NType.dot, fontWeight = FontWeight.Black, fontSize = 38.sp, lineHeight = 42.sp, letterSpacing = 0.5.sp),
            headlineSmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
            titleLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp),
            titleMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
            titleSmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
            bodyLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
            bodyMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
            bodySmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 17.sp),
            labelLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
            labelMedium = TextStyle(fontFamily = mono, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 1.2.sp),
            labelSmall = TextStyle(fontFamily = mono, fontWeight = FontWeight.Normal, fontSize = 10.5.sp, lineHeight = 14.sp, letterSpacing = 0.8.sp),
        )
    }

private val NothingShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun MedxTheme(accent: MedxAccent, appearance: MedxAppearance, reduceMotion: Boolean? = null, content: @Composable () -> Unit) {
    val dark = when (appearance) {
        MedxAppearance.SYSTEM -> isSystemInDarkTheme()
        MedxAppearance.LIGHT -> false
        MedxAppearance.DARK -> true
    }
    val accentColor = Color(accent.hex)
    val medx = nothingColors(dark, accentColor)
    val context = LocalContext.current
    val noMotion = reduceMotion ?: remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    // Monochrome Material scheme: "primary" is the ink colour, so stock buttons, switches,
    // progress and focus rings come out white-on-black (black-on-white in light), Nothing style.
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color.White, onPrimary = Color.Black,
            secondary = Color.White, onSecondary = Color.Black,
            tertiary = accentColor, onTertiary = Color.White,
            background = medx.groupedBackground, onBackground = Color.White,
            surface = medx.groupedBackground, onSurface = Color.White,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = medx.card, surfaceContainer = Color(0xFF141414),
            surfaceContainerHigh = Color(0xFF161616), surfaceContainerHighest = Color(0xFF222222),
            surfaceVariant = Color(0xFF1C1C1C), onSurfaceVariant = medx.secondaryText,
            secondaryContainer = Color.White, onSecondaryContainer = Color.Black,
            primaryContainer = Color(0xFF1C1C1C), onPrimaryContainer = Color.White,
            outline = Color(0xFF5A5A5A), outlineVariant = medx.hairline,
            error = medx.wrong, onError = Color.White,
            inverseSurface = Color.White, inverseOnSurface = Color.Black,
            surfaceTint = Color.Transparent,
        )
    } else {
        lightColorScheme(
            primary = Color.Black, onPrimary = Color.White,
            secondary = Color.Black, onSecondary = Color.White,
            tertiary = accentColor, onTertiary = Color.White,
            background = medx.groupedBackground, onBackground = Color.Black,
            surface = medx.groupedBackground, onSurface = Color.Black,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = medx.card, surfaceContainer = Color(0xFFF7F7F7),
            surfaceContainerHigh = Color(0xFFFAFAFA), surfaceContainerHighest = Color(0xFFE8E8E8),
            surfaceVariant = Color(0xFFEDEDED), onSurfaceVariant = medx.secondaryText,
            secondaryContainer = Color.Black, onSecondaryContainer = Color.White,
            primaryContainer = Color(0xFFEDEDED), onPrimaryContainer = Color.Black,
            outline = Color(0xFF9A9A9A), outlineVariant = medx.hairline,
            error = medx.wrong, onError = Color.White,
            inverseSurface = Color.Black, inverseOnSurface = Color.White,
            surfaceTint = Color.Transparent,
        )
    }
    CompositionLocalProvider(LocalMedxColors provides medx, LocalReduceMotion provides noMotion) {
        MaterialTheme(colorScheme = scheme, typography = NothingTypography, shapes = NothingShapes, content = content)
    }
}
