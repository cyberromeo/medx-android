package quest.srihari.medxelite.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import quest.srihari.medxelite.data.MedxAccent
import quest.srihari.medxelite.data.MedxAppearance

/** iOS system-ish palette the SwiftUI app leans on (grouped backgrounds, semantic outcome colours). */
@Immutable
data class MedxColors(
    val groupedBackground: Color,
    val card: Color,
    val separator: Color,
    val secondaryText: Color,
    val tertiaryText: Color,
    val correct: Color,
    val wrong: Color,
    val flagged: Color,
    val accent: Color,
    val isDark: Boolean,
)

val LocalMedxColors = staticCompositionLocalOf {
    MedxColors(
        Color(0xFFF2F2F7), Color.White, Color(0x33000000), Color(0xFF6C6C70), Color(0xFFAEAEB2),
        Color(0xFF34C759), Color(0xFFFF3B30), Color(0xFFFF9500), Color(0xFF0A84FF), false,
    )
}

object Mx {
    val colors: MedxColors @Composable get() = LocalMedxColors.current
}

/** Hues for tiles and subject icons (MedxFeature.color on iOS). */
object Hues {
    val blue = Color(0xFF0A84FF)
    val indigo = Color(0xFF5E5CE6)
    val purple = Color(0xFFBF5AF2)
    val pink = Color(0xFFFF375F)
    val red = Color(0xFFFF453A)
    val orange = Color(0xFFFF9F0A)
    val yellow = Color(0xFFFFD60A)
    val green = Color(0xFF30D158)
    val mint = Color(0xFF63E6E2)
    val teal = Color(0xFF40C8E0)
    val cyan = Color(0xFF64D2FF)
    val brown = Color(0xFFAC8E68)
    val gray = Color(0xFF98989D)

    private val cycle = listOf(blue, purple, pink, orange, green, teal, indigo, red, mint, cyan, brown, yellow)
    fun forName(name: String): Color = cycle[Math.floorMod(name.hashCode(), cycle.size)]
}

@Composable
fun MedxTheme(accent: MedxAccent, appearance: MedxAppearance, content: @Composable () -> Unit) {
    val dark = when (appearance) {
        MedxAppearance.SYSTEM -> isSystemInDarkTheme()
        MedxAppearance.LIGHT -> false
        MedxAppearance.DARK -> true
    }
    val accentColor = Color(accent.hex)
    val medx = if (dark) {
        MedxColors(
            groupedBackground = Color(0xFF000000), card = Color(0xFF1C1C1E), separator = Color(0x40FFFFFF),
            secondaryText = Color(0xFF98989F), tertiaryText = Color(0xFF636366),
            correct = Color(0xFF30D158), wrong = Color(0xFFFF453A), flagged = Color(0xFFFF9F0A),
            accent = accentColor, isDark = true,
        )
    } else {
        MedxColors(
            groupedBackground = Color(0xFFF2F2F7), card = Color.White, separator = Color(0x29000000),
            secondaryText = Color(0xFF6C6C70), tertiaryText = Color(0xFFAEAEB2),
            correct = Color(0xFF34C759), wrong = Color(0xFFFF3B30), flagged = Color(0xFFFF9500),
            accent = accentColor, isDark = false,
        )
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = accentColor, onPrimary = Color.White, secondary = accentColor,
            background = medx.groupedBackground, surface = medx.groupedBackground,
            surfaceContainer = Color(0xFF121214), surfaceContainerLow = medx.card,
            surfaceContainerHigh = Color(0xFF2C2C2E), surfaceContainerHighest = Color(0xFF3A3A3C),
            surfaceVariant = Color(0xFF2C2C2E), onSurfaceVariant = medx.secondaryText,
            secondaryContainer = accentColor.copy(alpha = 0.28f), onSecondaryContainer = Color.White,
            primaryContainer = accentColor.copy(alpha = 0.28f), onPrimaryContainer = Color.White,
            outlineVariant = medx.separator,
        )
    } else {
        lightColorScheme(
            primary = accentColor, onPrimary = Color.White, secondary = accentColor,
            background = medx.groupedBackground, surface = medx.groupedBackground,
            surfaceContainer = Color(0xFFF9F9FB), surfaceContainerLow = medx.card,
            surfaceContainerHigh = Color(0xFFE9E9EE), surfaceContainerHighest = Color(0xFFE0E0E6),
            surfaceVariant = Color(0xFFE9E9EE), onSurfaceVariant = medx.secondaryText,
            secondaryContainer = accentColor.copy(alpha = 0.16f), onSecondaryContainer = Color(0xFF0B0B0F),
            primaryContainer = accentColor.copy(alpha = 0.16f), onPrimaryContainer = Color(0xFF0B0B0F),
            outlineVariant = medx.separator,
        )
    }
    CompositionLocalProvider(LocalMedxColors provides medx) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
