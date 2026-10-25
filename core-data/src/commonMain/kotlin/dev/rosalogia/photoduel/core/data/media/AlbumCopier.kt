package dev.rosalogia.photoduel.core.data.media

/**
 * Copies photos into a "Highlights/<album>" folder that galleries show as an album. Copies are
 * byte-for-byte, so EXIF (time, location) is kept, and they keep the original capture time. They
 * live outside the camera folders, so the app's own indexing ignores them.
 */
interface AlbumCopier {
    /** Where an album's copies go, for display, e.g. "Pictures/Highlights/Yosemite/". */
    fun albumPath(album: String): String

    /** Copies one photo and returns the copy's ID, for [exists]. */
    suspend fun copy(mediaId: Long, album: String): Long

    /** Whether a copy made earlier still exists (the user may have deleted it). */
    suspend fun exists(copyId: Long): Boolean

    companion object {
        /** A folder name that's valid on every platform. */
        fun safeName(album: String): String =
            album.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().trimEnd('.').ifEmpty { "Highlights" }.take(80)
    }
}
