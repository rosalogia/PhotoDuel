package dev.rosalogia.photoduel.platform

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.provider.MediaStore

/**
 * Changes to the user's library. Trash and favorite go through MediaStore requests, so Android
 * shows its own confirmation and the app never touches files directly.
 */
class LibraryActions(
    private val context: Context,
) {
    /** One system dialog for all [mediaIds]; trashed items stay restorable from the gallery's trash. */
    fun trashRequest(mediaIds: Collection<Long>): IntentSender =
        MediaStore.createTrashRequest(context.contentResolver, mediaIds.map(::mediaContentUri), true).intentSender

    fun favoriteRequest(mediaIds: Collection<Long>): IntentSender =
        MediaStore.createFavoriteRequest(context.contentResolver, mediaIds.map(::mediaContentUri), true).intentSender

    fun shareIntent(mediaId: Long): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, mediaContentUri(mediaId))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, null)
    }

    fun shareIntent(mediaIds: List<Long>): Intent {
        if (mediaIds.size == 1) return shareIntent(mediaIds.single())
        val uris = ArrayList(mediaIds.map(::mediaContentUri))
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            // Grants on EXTRA_STREAM lists only reach the target through ClipData.
            clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, null)
    }

    fun viewIntent(mediaId: Long): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(mediaContentUri(mediaId), "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
}
