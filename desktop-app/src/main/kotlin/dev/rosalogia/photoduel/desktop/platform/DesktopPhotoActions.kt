package dev.rosalogia.photoduel.desktop.platform

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.rosalogia.photoduel.core.common.AppLog
import dev.rosalogia.photoduel.core.ui.PhotoActions
import dev.rosalogia.photoduel.core.ui.ShareAction
import dev.rosalogia.photoduel.desktop.ui.DesktopIcons
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Desktop [PhotoActions]: files go to the system trash after an in-app confirmation (there's no
 * system dialog like Android's), and "share" shows the photo in the file manager.
 */
class DesktopPhotoActions(
    private val pathOf: suspend (Long) -> File?,
    private val onLibraryChanged: () -> Unit,
) : PhotoActions {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private class Confirm(val count: Int, val answer: CompletableDeferred<Boolean>)
    private var confirm by mutableStateOf<Confirm?>(null)

    override suspend fun trash(ids: List<Long>): Boolean {
        val ask = Confirm(ids.size, CompletableDeferred())
        confirm = ask
        if (!ask.answer.await()) return false
        val moved = withContext(Dispatchers.IO) {
            ids.count { id ->
                val file = pathOf(id) ?: return@count false
                runCatching { SystemFiles.trash(file) }
                    .onFailure { AppLog.e("Actions", "Couldn't trash $file", it) }
                    .isSuccess
            }
        }
        AppLog.i("Actions", "Moved $moved of ${ids.size} photos to the trash")
        onLibraryChanged()
        return moved > 0
    }

    override val canFavorite = false

    override suspend fun favorite(ids: List<Long>) = false

    override val share = ShareAction("Show in folder", DesktopIcons.Folder)

    override fun share(ids: List<Long>) = act(ids.firstOrNull()) { SystemFiles.reveal(it) }

    override fun open(id: Long) = act(id) { SystemFiles.open(it) }

    private fun act(id: Long?, block: (File) -> Unit) {
        id ?: return
        scope.launch {
            runCatching { pathOf(id)?.let(block) }.onFailure { AppLog.w("Actions", "Action on $id failed", it) }
        }
    }

    /** Shows the trash confirmation when one is pending; call once near the window's root. */
    @Composable
    fun Dialogs() {
        val ask = confirm ?: return
        fun answer(yes: Boolean) {
            confirm = null
            ask.answer.complete(yes)
        }
        val what = if (ask.count == 1) "this photo" else "these ${ask.count} photos"
        AlertDialog(
            onDismissRequest = { answer(false) },
            title = { Text("Move $what to the trash?") },
            text = { Text("You can restore them from the ${if (System.getProperty("os.name").startsWith("Windows")) "Recycle Bin" else "Trash"}.") },
            confirmButton = { TextButton(onClick = { answer(true) }) { Text("Move to trash") } },
            dismissButton = { TextButton(onClick = { answer(false) }) { Text("Cancel") } },
        )
    }
}
