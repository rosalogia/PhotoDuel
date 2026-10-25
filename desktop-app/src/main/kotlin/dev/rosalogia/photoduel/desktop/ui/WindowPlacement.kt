package dev.rosalogia.photoduel.desktop.ui

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import java.io.File

/** Remembers the window's size, position and maximized state between runs, in a small text file. */
class SavedWindow(private val file: File) {
    fun restore(): WindowState {
        val v = runCatching { file.readText().trim().split(" ") }.getOrNull()
        val state = WindowState(size = DpSize(1280.dp, 860.dp))
        if (v == null || v.size != 5) return state
        runCatching {
            state.size = DpSize(v[0].toFloat().dp, v[1].toFloat().dp)
            state.position = WindowPosition(v[2].toFloat().dp, v[3].toFloat().dp)
            if (v[4] == "max") state.placement = WindowPlacement.Maximized
        }
        return state
    }

    fun save(state: WindowState) {
        val pos = state.position as? WindowPosition.Absolute ?: return
        runCatching {
            file.writeText(
                "${state.size.width.value} ${state.size.height.value} ${pos.x.value} ${pos.y.value} " +
                    if (state.placement == WindowPlacement.Maximized) "max" else "normal",
            )
        }
    }
}
