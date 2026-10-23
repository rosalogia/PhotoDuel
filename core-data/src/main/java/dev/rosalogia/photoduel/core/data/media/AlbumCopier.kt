package dev.rosalogia.photoduel.core.data.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.rosalogia.photoduel.core.common.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies photos into Pictures/Highlights/<album>, which gallery apps show as an album. The copies
 * are byte-for-byte, so EXIF (time, location) is kept, and they keep the original capture time.
 * They live outside DCIM, so the app's own indexing ignores them.
 */
@Singleton
class AlbumCopier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Folder path relative to the shared storage root, e.g. "Pictures/Highlights/Yosemite/". */
    fun albumPath(album: String): String = "${Environment.DIRECTORY_PICTURES}/Highlights/${safeName(album)}/"

    /** Copies one photo and returns the copy's media ID. */
    suspend fun copy(mediaId: Long, album: String): Long = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val source = mediaContentUri(mediaId)
        val (name, mime, taken) = resolver.query(
            source,
            arrayOf(MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.MIME_TYPE, MediaStore.Images.Media.DATE_TAKEN),
            null,
            null,
            null,
        )?.use { c ->
            check(c.moveToFirst()) { "Photo $mediaId not found" }
            Triple(c.getString(0) ?: "IMG_$mediaId.jpg", c.getString(1) ?: "image/jpeg", c.getLong(2))
        } ?: error("Photo $mediaId not found")

        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, albumPath(album))
            if (taken > 0) put(MediaStore.Images.Media.DATE_TAKEN, taken)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val target = resolver.insert(collection, values) ?: error("Couldn't create a copy of $mediaId")
        try {
            resolver.openInputStream(source)!!.use { input ->
                resolver.openOutputStream(target)!!.use { output -> input.copyTo(output) }
            }
            resolver.update(target, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(target, null, null)
            throw e
        }
        AppLog.i(TAG, "Copied $mediaId to ${albumPath(album)}$name")
        ContentUris.parseId(target)
    }

    /** Whether a copy made earlier still exists (the user may have deleted it). */
    suspend fun exists(mediaId: Long): Boolean = withContext(Dispatchers.IO) {
        context.contentResolver.query(mediaContentUri(mediaId), arrayOf(MediaStore.Images.Media._ID), null, null, null)
            ?.use { it.moveToFirst() } ?: false
    }

    private fun safeName(album: String): String =
        album.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().ifEmpty { "Highlights" }.take(80)

    private companion object {
        const val TAG = "Album"
    }
}
