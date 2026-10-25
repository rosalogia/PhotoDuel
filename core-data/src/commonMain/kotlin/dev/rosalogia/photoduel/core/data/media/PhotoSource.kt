package dev.rosalogia.photoduel.core.data.media

import kotlinx.coroutines.flow.Flow

/** A photo as the platform's library describes it right now. */
data class MediaPhoto(
    val id: Long,
    /** Capture time in ms, falling back to when the file was added. */
    val dateTaken: Long,
    /** Seconds. Used to detect edits. */
    val dateModified: Long,
    /** As displayed, i.e. after any EXIF rotation. */
    val width: Int,
    val height: Int,
    /** Folder, for display and debugging, e.g. "DCIM/Camera/". */
    val relativePath: String?,
)

enum class MediaAccess { NONE, PARTIAL, FULL }

/**
 * Where camera photos come from: MediaStore on Android, the folders the user chose on desktop.
 * IDs are stable for as long as the photo exists and are never reused for another photo.
 */
interface PhotoSource {
    fun currentAccess(): MediaAccess

    /** Camera photos, newest first. */
    suspend fun queryCameraPhotos(): List<MediaPhoto>

    /** Emits whenever the library may have changed. Only active while collected. */
    fun changes(): Flow<Unit>
}
