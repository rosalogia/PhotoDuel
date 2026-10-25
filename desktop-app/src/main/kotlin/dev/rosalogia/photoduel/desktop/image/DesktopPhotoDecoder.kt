package dev.rosalogia.photoduel.desktop.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import dev.rosalogia.photoduel.core.common.AppLog
import dev.rosalogia.photoduel.core.ml.ArgbImage
import dev.rosalogia.photoduel.core.ml.PhotoDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.math.max

/**
 * Decodes photos by media ID from their files, and keeps small JPEG thumbnails on disk so grids
 * and indexing don't decode whole 12 MP photos again.
 */
class DesktopPhotoDecoder(
    private val pathOf: suspend (Long) -> File?,
    private val thumbDir: File,
) : PhotoDecoder {
    /** Full decodes hold a lot of memory; this caps how many run at once. */
    private val decodes = Semaphore(maxOf(2, Runtime.getRuntime().availableProcessors() / 2))

    override suspend fun decodeDisplay(mediaId: Long, maxEdge: Int): ImageBitmap =
        decodeBitmap(mediaId, maxEdge).asComposeImageBitmap()

    override suspend fun decodeArgb(mediaId: Long, maxEdge: Int): ArgbImage {
        // Analysis sizes fit within a thumbnail, which is much cheaper than the original.
        val bitmap = if (maxEdge <= THUMB_EDGE) Decoding.scaleDown(thumbnail(mediaId), maxEdge) else decodeBitmap(mediaId, maxEdge)
        try {
            return ArgbImage(bitmap.width, bitmap.height, Decoding.argb(bitmap))
        } finally {
            bitmap.close()
        }
    }

    suspend fun decodeBitmap(mediaId: Long, maxEdge: Int): Bitmap = withContext(Dispatchers.IO) {
        val file = pathOf(mediaId) ?: error("Photo $mediaId isn't in the catalog")
        decodes.withPermit { Decoding.decode(file, maxEdge) }
    }

    /**
     * A [THUMB_EDGE] px version of the photo, from the disk cache or made now. Keyed by the file's
     * size and time, so an edited photo gets a new one.
     */
    suspend fun thumbnail(mediaId: Long): Bitmap = withContext(Dispatchers.IO) {
        val file = pathOf(mediaId) ?: error("Photo $mediaId isn't in the catalog")
        val cached = File(thumbDir, "$mediaId-${file.length()}-${file.lastModified()}.jpg")
        if (cached.exists()) {
            runCatching { return@withContext Decoding.decode(cached, THUMB_EDGE) }
                .onFailure { AppLog.w(TAG, "Bad thumbnail $cached; remaking it", it) }
        }
        val bitmap = decodes.withPermit { Decoding.decode(file, THUMB_EDGE) }
        runCatching {
            thumbDir.mkdirs()
            thumbDir.listFiles { f -> f.name.startsWith("$mediaId-") }?.forEach { it.delete() }
            val data = Image.makeFromBitmap(bitmap).use { it.encodeToData(EncodedImageFormat.JPEG, 85) }
            if (data != null) {
                val tmp = File(thumbDir, cached.name + ".tmp")
                tmp.writeBytes(data.bytes)
                tmp.renameTo(cached)
            }
        }.onFailure { AppLog.w(TAG, "Couldn't save thumbnail for $mediaId", it) }
        bitmap
    }

    companion object {
        private const val TAG = "Decoder"
        /** Big enough for a sharp grid cell on a high-DPI screen. */
        const val THUMB_EDGE = 640

        fun longEdge(b: Bitmap) = max(b.width, b.height)
    }
}
