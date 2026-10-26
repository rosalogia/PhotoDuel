package dev.rosalogia.photoduel.core.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The palette: black, white and one accent. Photos supply all the color, so the interface stays
 * neutral; [Kept] means "what you keep" (your pick, the ring, progress) and nothing else, and
 * [Trash] appears on the one button that moves photos to the trash.
 */
object Ink {
    val Black = Color(0xFF000000)
    /** Cards and sheets. */
    val Surface = Color(0xFF0E0E10)
    /** Buttons and anything raised above a card. */
    val Raised = Color(0xFF17171A)
    val Raised2 = Color(0xFF222226)
    /** Hairlines and dividers. */
    val Hair = Color(0x17FFFFFF)
    val Fg = Color(0xFFF2F2F3)
    val Dim = Color(0xFF8B8B93)
    val Faint = Color(0xFF55555C)
    val Kept = Color(0xFFFFD23F)
    val OnKept = Color(0xFF1A1500)
    val Trash = Color(0xFFFF5A4F)
    /** The challenger in a duel: plain white, so there's only ever one color to look for. */
    val Challenger = Color.White
}

/** The one scheme for every screen; the app is dark everywhere and doesn't follow wallpaper colors. */
object BrandColors {
    val scheme: ColorScheme = darkColorScheme(
        primary = Ink.Kept,
        onPrimary = Ink.OnKept,
        primaryContainer = Color(0xFF2A2408),
        onPrimaryContainer = Ink.Kept,
        inversePrimary = Color(0xFF6B5500),
        secondary = Ink.Fg,
        onSecondary = Ink.Black,
        secondaryContainer = Ink.Raised2,
        onSecondaryContainer = Ink.Fg,
        tertiary = Ink.Fg,
        onTertiary = Ink.Black,
        tertiaryContainer = Ink.Raised2,
        onTertiaryContainer = Ink.Fg,
        background = Ink.Black,
        onBackground = Ink.Fg,
        surface = Ink.Black,
        onSurface = Ink.Fg,
        surfaceVariant = Ink.Raised,
        onSurfaceVariant = Ink.Dim,
        surfaceTint = Color.Transparent,
        inverseSurface = Ink.Fg,
        inverseOnSurface = Ink.Black,
        error = Ink.Trash,
        onError = Color(0xFF1F0503),
        errorContainer = Color(0xFF2A0F0D),
        onErrorContainer = Ink.Trash,
        outline = Ink.Faint,
        outlineVariant = Color(0xFF26262A),
        scrim = Ink.Black,
        surfaceBright = Ink.Raised2,
        surfaceDim = Ink.Black,
        surfaceContainerLowest = Ink.Black,
        surfaceContainerLow = Ink.Surface,
        surfaceContainer = Ink.Surface,
        surfaceContainerHigh = Ink.Raised,
        surfaceContainerHighest = Ink.Raised2,
    )
}
