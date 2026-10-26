package dev.rosalogia.photoduel.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.rosalogia.photoduel.core.ui.Ink
import dev.rosalogia.photoduel.core.ui.Readout
import dev.rosalogia.photoduel.core.ui.SectionLabel
import androidx.compose.ui.unit.dp

private val sections = listOf(
    "Sorting a stack" to listOf(
        "← / →" to "Keep yours / keep the challenger",
        "Hold Space" to "Show the challenger",
        "Scroll, + / −, 0" to "Zoom in and out, back to fit",
        "B" to "Keep both (they aren't duplicates)",
        "Ctrl+Z or Backspace" to "Undo",
        "↑ / ↓" to "Previous / next stack",
    ),
    "When a stack is done" to listOf(
        "Delete" to "Trash the others",
        "Enter" to "Keep all",
    ),
    "Highlights" to listOf(
        "→ / ←" to "Keep / skip, in the quick pass",
        "← / →" to "Pick the left / right photo, head to head",
    ),
    "Anywhere" to listOf(
        "Esc" to "Close what's open",
        "?" to "This list",
    ),
)

@Composable
fun ShortcutsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Raised,
        title = { Text("Keyboard shortcuts") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                sections.forEach { (title, keys) ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionLabel(title)
                        keys.forEach { (key, what) ->
                            Row {
                                Readout(key, Modifier.width(170.dp), color = Ink.Kept)
                                Text(what, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = Ink.Fg)) { Text("Close") } },
    )
}
