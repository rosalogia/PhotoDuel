package dev.rosalogia.photoduel.core.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.staticCompositionLocalOf

/** The dark scheme for photo screens, which stay dark whatever the system theme; dynamic on Android 12+. */
val LocalDarkColors = staticCompositionLocalOf<ColorScheme> { darkColorScheme() }
