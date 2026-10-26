package dev.rosalogia.photoduel.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** The app's name, wherever the UI shows it. */
const val APP_NAME = "Photo Duel"

/**
 * Geist for words and Geist Mono for readouts (counts, dates, labels). Each app loads the font
 * files its own way and passes them in.
 */
class BrandFonts(val sans: FontFamily, val mono: FontFamily)

val LocalBrandFonts = staticCompositionLocalOf { BrandFonts(FontFamily.SansSerif, FontFamily.Monospace) }

/**
 * Light and large for moments ("All caught up."), medium for titles and controls, mono for
 * readouts. labelMedium and labelSmall are the readout styles; use [Readout] for them.
 */
fun brandTypography(f: BrandFonts): Typography {
    fun sans(weight: FontWeight, size: Int, line: Int, tracking: Double = 0.0) =
        TextStyle(fontFamily = f.sans, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.em)
    fun mono(size: Int, line: Int) =
        TextStyle(fontFamily = f.mono, fontWeight = FontWeight.Normal, fontSize = size.sp, lineHeight = line.sp, letterSpacing = 0.08.em)
    return Typography(
        displayLarge = sans(FontWeight.Light, 56, 58, -0.05),
        displayMedium = sans(FontWeight.Light, 44, 46, -0.05),
        displaySmall = sans(FontWeight.Light, 36, 40, -0.045),
        headlineLarge = sans(FontWeight.Light, 32, 36, -0.04),
        headlineMedium = sans(FontWeight.Light, 28, 32, -0.035),
        headlineSmall = sans(FontWeight.Light, 24, 28, -0.03),
        titleLarge = sans(FontWeight.Medium, 20, 26, -0.02),
        titleMedium = sans(FontWeight.Medium, 16, 22, -0.01),
        titleSmall = sans(FontWeight.Medium, 14, 20, -0.005),
        bodyLarge = sans(FontWeight.Normal, 16, 24),
        bodyMedium = sans(FontWeight.Normal, 14, 20),
        bodySmall = sans(FontWeight.Normal, 12, 17),
        labelLarge = sans(FontWeight.Medium, 14, 18, -0.005),
        labelMedium = mono(11, 14),
        labelSmall = mono(10, 13),
    )
}

private val BrandShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Wraps every screen of both apps. */
@Composable
fun BrandTheme(fonts: BrandFonts, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBrandFonts provides fonts) {
        MaterialTheme(colorScheme = BrandColors.scheme, typography = brandTypography(fonts), shapes = BrandShapes, content = content)
    }
}
