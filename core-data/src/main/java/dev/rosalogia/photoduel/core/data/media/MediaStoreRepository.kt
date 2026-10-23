package dev.rosalogia.photoduel.core.data.media

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.rosalogia.photoduel.core.common.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** A photo as MediaStore describes it right now. */
data class MediaPhoto(
    val id: Long,
    /** Capture time in ms, falling back to DATE_ADDED. */
    val dateTaken: Long,
    /** Seconds, as MediaStore stores it. Used to detect edits. */
    val dateModified: Long,
    val width: Int,
    val height: Int,
    val relativePath: String?,
)

enum class MediaAccess { NONE, PARTIAL, FULL }

fun mediaContentUri(mediaId: Long): Uri =
    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId)

@Singleton
class MediaStoreRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val resolver: ContentResolver get() = context.contentResolver

    fun currentAccess(): MediaAccess {
        fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= 33 && granted(Manifest.permission.READ_MEDIA_IMAGES) -> MediaAccess.FULL
            Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> MediaAccess.PARTIAL
            Build.VERSION.SDK_INT < 33 && granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> MediaAccess.FULL
            else -> MediaAccess.NONE
        }
    }

    /**
     * Camera photos only: images under DCIM/, excluding screenshot folders some OEMs put there.
     */
    suspend fun queryCameraPhotos(): List<MediaPhoto> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.ORIENTATION,
            MediaStore.Images.Media.RELATIVE_PATH,
        )
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND " +
            "${MediaStore.Images.Media.RELATIVE_PATH} NOT LIKE ?"
        val args = arrayOf("DCIM/%", "%Screenshots%")
        val result = ArrayList<MediaPhoto>()
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val takenCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val modCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val wCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val hCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val oCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.ORIENTATION)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            while (c.moveToNext()) {
                val taken = if (c.isNull(takenCol) || c.getLong(takenCol) <= 0) c.getLong(addedCol) * 1000 else c.getLong(takenCol)
                val rotated = c.getInt(oCol) % 180 != 0
                val w = c.getInt(wCol)
                val h = c.getInt(hCol)
                result += MediaPhoto(
                    id = c.getLong(idCol),
                    dateTaken = taken,
                    dateModified = c.getLong(modCol),
                    width = if (rotated) h else w,
                    height = if (rotated) w else h,
                    relativePath = c.getString(pathCol),
                )
            }
        }
        AppLog.d(TAG, "MediaStore returned ${result.size} camera photos")
        result
    }

    /** Emits whenever the image collection changes. Only active while collected. */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    private companion object {
        const val TAG = "MediaStore"
    }
}
