package dev.rosalogia.photoduel.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val sections = listOf(
    "Sorting a group" to listOf(
        "← / →" to "Keep the current pick / keep the challenger",
        "Hold Space" to "Show the challenger",
        "Scroll, + / −, 0" to "Zoom in and out, back to fit",
        "B" to "Keep both (they aren't duplicates)",
        "Ctrl+Z or Backspace" to "Undo",
        "↑ / ↓" to "Previous / next group",
    ),
    "When a group is done" to listOf(
        "Delete" to "Trash the others",
        "Enter" to "Keep all and mark done",
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
        title = { Text("Keyboard shortcuts") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                sections.forEach { (title, keys) ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, style = MaterialTheme.typography.titleSmall)
                        keys.forEach { (key, what) ->
                            Row {
                                Text(key, Modifier.width(170.dp), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text(what, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
