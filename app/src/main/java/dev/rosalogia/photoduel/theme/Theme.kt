package dev.rosalogia.photoduel.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.rosalogia.photoduel.R
import dev.rosalogia.photoduel.core.ui.BrandFonts
import dev.rosalogia.photoduel.core.ui.BrandTheme

/** Geist and Geist Mono, subset to Latin, from res/font (SIL OFL; see assets/licenses). */
private val AppFonts = BrandFonts(
  sans = FontFamily(
    Font(R.font.geist_light, FontWeight.Light),
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
  ),
  mono = FontFamily(Font(R.font.geist_mono_regular, FontWeight.Normal)),
)

/** The brand theme: dark everywhere, one accent; it doesn't follow wallpaper colors. */
@Composable
fun PhotoDuelTheme(content: @Composable () -> Unit) = BrandTheme(AppFonts, content)
