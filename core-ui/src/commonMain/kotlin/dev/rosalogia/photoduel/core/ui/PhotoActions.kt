package dev.rosalogia.photoduel.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Changes to the user's library and handing photos to other apps. On Android these go through
 * system dialogs (MediaStore requests, the share sheet); on desktop they act on files directly.
 */
interface PhotoActions {
    /** Moves [ids] to the system trash; false if the user cancelled. Restorable from the trash. */
    suspend fun trash(ids: List<Long>): Boolean

    /** A few words on getting trashed photos back, shown under the trash button. */
    val trashNote: String

    /** False where the platform has no notion of favorites; then the UI hides the action. */
    val canFavorite: Boolean

    /** Marks [ids] as favorites; false if the user cancelled. */
    suspend fun favorite(ids: List<Long>): Boolean

    /** The platform's way to hand photos on: the share sheet, or showing them in the file manager. */
    val share: ShareAction

    fun share(ids: List<Long>)

    /** Opens one photo in the platform's viewer. */
    fun open(id: Long)
}

data class ShareAction(val label: String, val icon: ImageVector)

val LocalPhotoActions = staticCompositionLocalOf<PhotoActions> { error("No PhotoActions provided") }

/** Shows a short message: a toast on Android, a snackbar on desktop. */
val LocalMessages = staticCompositionLocalOf<(String) -> Unit> { { } }

/** Coil model for a photo by media ID; each app teaches its image loader how to load one. */
data class PhotoRef(val mediaId: Long)
