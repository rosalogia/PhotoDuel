package dev.rosalogia.photoduel.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * Routes hardware keys to whichever screens want them. The desktop window feeds it every key that
 * a focused text field didn't use; the most recently registered handler goes first.
 */
class ShortcutDispatcher {
    private val handlers = ArrayList<(KeyEvent) -> Boolean>()
    private val held = HashSet<Key>()

    /** Returns true if a handler used [event]. Auto-repeated key-downs are dropped. */
    fun dispatch(event: KeyEvent): Boolean {
        when (event.type) {
            KeyEventType.KeyDown -> if (!held.add(event.key)) return true
            KeyEventType.KeyUp -> held.remove(event.key)
        }
        return handlers.asReversed().toList().any { it(event) }
    }

    internal fun add(handler: (KeyEvent) -> Boolean) {
        handlers += handler
    }

    internal fun remove(handler: (KeyEvent) -> Boolean) {
        handlers -= handler
    }
}

/** Null where there's no hardware keyboard routing (the phone app). */
val LocalShortcuts = staticCompositionLocalOf<ShortcutDispatcher?> { null }

/** Handles keys while this composable is on screen; return true when a key was used. */
@Composable
fun KeyShortcuts(handler: (KeyEvent) -> Boolean) {
    val dispatcher = LocalShortcuts.current ?: return
    val current by rememberUpdatedState(handler)
    DisposableEffect(dispatcher) {
        val registered: (KeyEvent) -> Boolean = { current(it) }
        dispatcher.add(registered)
        onDispose { dispatcher.remove(registered) }
    }
}

/** True when this platform has keyboard shortcuts to show hints for. */
val hasShortcuts: Boolean @Composable get() = LocalShortcuts.current != null
