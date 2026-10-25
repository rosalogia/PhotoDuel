package dev.rosalogia.photoduel.core.ml

import androidx.compose.ui.graphics.ImageBitmap
import org.opencv.core.CvType
import org.opencv.core.Mat

/** Software pixels for analysis: row-major ARGB, as Android's Bitmap.getPixels returns them. */
class ArgbImage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(pixels.size == width * height)
    }

    /** 8-bit grayscale with the same weights as OpenCV's RGB2GRAY. */
    fun toGrayMat(): Mat {
        OpenCv.ensureLoaded()
        val gray = ByteArray(width * height)
        for (i in pixels.indices) {
            val p = pixels[i]
            gray[i] = ((299 * (p shr 16 and 255) + 587 * (p shr 8 and 255) + 114 * (p and 255) + 500) / 1000).toByte()
        }
        return Mat(height, width, CvType.CV_8UC1).apply { put(0, 0, gray) }
    }
}

/** Decodes photos by media ID. Implemented per platform; orientation is always applied. */
interface PhotoDecoder {
    /** For on-screen display, long edge at most [maxEdge]. May be GPU-backed. */
    suspend fun decodeDisplay(mediaId: Long, maxEdge: Int): ImageBitmap

    /** For analysis, long edge at most [maxEdge]. */
    suspend fun decodeArgb(mediaId: Long, maxEdge: Int): ArgbImage
}

/** A rectangle in normalized image coordinates (0..1 across width and height). */
data class NormRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2
    val centerY: Float get() = (top + bottom) / 2

    fun clampToUnit() = NormRect(left.coerceIn(0f, 1f), top.coerceIn(0f, 1f), right.coerceIn(0f, 1f), bottom.coerceIn(0f, 1f))
}
