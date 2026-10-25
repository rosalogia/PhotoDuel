package dev.rosalogia.photoduel.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.rosalogia.photoduel.core.ml.ArgbImage
import dev.rosalogia.photoduel.core.ml.PhotoDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** Decodes through ImageDecoder, which applies EXIF orientation and downsamples while decoding. */
class AndroidPhotoDecoder(private val context: Context) : PhotoDecoder {
    override suspend fun decodeDisplay(mediaId: Long, maxEdge: Int): ImageBitmap = withContext(Dispatchers.IO) {
        // Hardware bitmaps live in GPU memory, so a duel's full-size photos don't crowd the heap.
        decode(mediaId, maxEdge, ImageDecoder.ALLOCATOR_HARDWARE).asImageBitmap()
    }

    override suspend fun decodeArgb(mediaId: Long, maxEdge: Int): ArgbImage = withContext(Dispatchers.IO) {
        val decoded = decode(mediaId, maxEdge, ImageDecoder.ALLOCATOR_SOFTWARE)
        val bitmap = if (decoded.config == Bitmap.Config.ARGB_8888) decoded else decoded.copy(Bitmap.Config.ARGB_8888, false)
        try {
            bitmap.toArgbImage()
        } finally {
            bitmap.recycle()
            if (decoded !== bitmap) decoded.recycle()
        }
    }

    private fun decode(mediaId: Long, maxEdge: Int, allocator: Int): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, mediaContentUri(mediaId))
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longEdge = max(info.size.width, info.size.height)
            if (longEdge > maxEdge) {
                val f = maxEdge.toFloat() / longEdge
                decoder.setTargetSize((info.size.width * f).roundToInt(), (info.size.height * f).roundToInt())
            }
            decoder.allocator = allocator
        }
    }
}

fun Bitmap.toArgbImage(): ArgbImage {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    return ArgbImage(width, height, pixels)
}

fun ArgbImage.toBitmap(): Bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
