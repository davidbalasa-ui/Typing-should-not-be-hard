package dev.patrickgold.florisboard.app.apptheme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * TypeBetter's own settings colours, taken from the app icon (navy T, red typo squiggle, green fixed
 * line, cream ground). Used while the settings accent is left at its default; a user-picked accent or
 * Material You still drives the old generated scheme (docs/decisions/2026-11-26-personal-settings-page.md).
 */
object TypeBetterColors {
    val Navy = Color(0xFF1E2A4A)
    val Paper = Color(0xFFFFFBF2)
    val Cream = Color(0xFFFFF3DE)
    val Line = Color(0xFFDED8C9)
    val Muted = Color(0xFF5A5E58)
    val Green = Color(0xFF13694F)
    val GreenSoft = Color(0xFFE1F0E8)
    val TypoRed = Color(0xFFB3261E)
    val Amber = Color(0xFF6B4700)
    val AmberSoft = Color(0xFFF6E6C4)

    /** The settings accent's stock value: while it is unchanged, the TypeBetter palette is used. */
    val StockAccent = Color(0xFF30B7E6)

    val light: ColorScheme = lightColorScheme(
        primary = Navy,
        onPrimary = Paper,
        primaryContainer = Cream,
        onPrimaryContainer = Navy,
        inversePrimary = Color(0xFFB9C6EA),
        secondary = Green,
        onSecondary = Color.White,
        secondaryContainer = GreenSoft,
        onSecondaryContainer = Color(0xFF0F3D2E),
        tertiary = TypoRed,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFDE7E4),
        onTertiaryContainer = Color(0xFF6B1410),
        background = Paper,
        onBackground = Navy,
        surface = Paper,
        onSurface = Navy,
        surfaceVariant = Color(0xFFF3EBDA),
        onSurfaceVariant = Muted,
        surfaceTint = Navy,
        inverseSurface = Navy,
        inverseOnSurface = Paper,
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = Color(0xFF8A8D86),
        outlineVariant = Line,
        scrim = Color.Black,
        surfaceBright = Paper,
        surfaceDim = Color(0xFFE8E0CF),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFFFF8EB),
        surfaceContainer = Color(0xFFF8F1E2),
        surfaceContainerHigh = Color(0xFFF3EBDA),
        surfaceContainerHighest = Color(0xFFEDE4D0),
    )

    val dark: ColorScheme = darkColorScheme(
        primary = Color(0xFFF5E9CF),
        onPrimary = Navy,
        primaryContainer = Color(0xFF2A3554),
        onPrimaryContainer = Color(0xFFF5E9CF),
        inversePrimary = Navy,
        secondary = Color(0xFF8FD3B4),
        onSecondary = Color(0xFF00382A),
        secondaryContainer = Color(0xFF1D4A3C),
        onSecondaryContainer = Color(0xFFBFE6D3),
        tertiary = Color(0xFFFFB4A8),
        onTertiary = Color(0xFF690005),
        tertiaryContainer = Color(0xFF7A231B),
        onTertiaryContainer = Color(0xFFFFDAD4),
        background = Color(0xFF12151F),
        onBackground = Color(0xFFEDE6D6),
        surface = Color(0xFF12151F),
        onSurface = Color(0xFFEDE6D6),
        surfaceVariant = Color(0xFF2A3044),
        onSurfaceVariant = Color(0xFFC4C2BA),
        surfaceTint = Color(0xFFF5E9CF),
        inverseSurface = Color(0xFFEDE6D6),
        inverseOnSurface = Navy,
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF8C8E96),
        outlineVariant = Color(0xFF3A4055),
        scrim = Color.Black,
        surfaceBright = Color(0xFF353B4F),
        surfaceDim = Color(0xFF12151F),
        surfaceContainerLowest = Color(0xFF0D1018),
        surfaceContainerLow = Color(0xFF181C28),
        surfaceContainer = Color(0xFF1C2130),
        surfaceContainerHigh = Color(0xFF252B3C),
        surfaceContainerHighest = Color(0xFF2F3547),
    )
}
