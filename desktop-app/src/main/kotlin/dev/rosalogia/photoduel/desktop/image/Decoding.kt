package dev.rosalogia.photoduel.desktop.image

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Codec
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedOrigin
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** File formats decoded here: Skia handles JPEG; HEIC goes through libheif. */
object Decoding {
    fun isHeif(file: File) = file.extension.lowercase() in setOf("heic", "heif")

    /** Decodes [file] upright, scaled so its long edge is at most [maxEdge]. */
    fun decode(file: File, maxEdge: Int): Bitmap {
        val full = if (isHeif(file)) fromRgba(LibHeif.decode(file.path)) else decodeWithSkia(file)
        return scaleDown(full, maxEdge)
    }

    private fun decodeWithSkia(file: File): Bitmap {
        val codec = Codec.makeFromData(Data.makeFromBytes(file.readBytes()))
        try {
            val raw = Bitmap().apply {
                allocPixels(ImageInfo(codec.width, codec.height, ColorType.N32, ColorAlphaType.PREMUL))
            }
            codec.readPixels(raw)
            return orient(raw, codec.encodedOrigin)
        } finally {
            codec.close()
        }
    }

    private fun fromRgba(p: RgbaPixels): Bitmap = Bitmap().apply {
        check(installPixels(ImageInfo(p.width, p.height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL), p.bytes, p.width * 4))
    }

    /** Applies EXIF orientation by drawing through the matching transform. */
    private fun orient(src: Bitmap, origin: EncodedOrigin): Bitmap {
        if (origin == EncodedOrigin.TOP_LEFT) return src
        val swap = origin in setOf(EncodedOrigin.LEFT_TOP, EncodedOrigin.RIGHT_TOP, EncodedOrigin.RIGHT_BOTTOM, EncodedOrigin.LEFT_BOTTOM)
        val w = if (swap) src.height else src.width
        val h = if (swap) src.width else src.height
        val out = Bitmap().apply { allocPixels(ImageInfo(w, h, ColorType.N32, ColorAlphaType.PREMUL)) }
        Canvas(out).use { c ->
            c.concat(origin.toMatrix(w, h))
            Image.makeFromBitmap(src).use { c.drawImage(it, 0f, 0f) }
        }
        src.close()
        return out
    }

    fun scaleDown(src: Bitmap, maxEdge: Int): Bitmap {
        val longEdge = max(src.width, src.height)
        if (longEdge <= maxEdge) return src
        val f = maxEdge.toFloat() / longEdge
        val w = (src.width * f).roundToInt().coerceAtLeast(1)
        val h = (src.height * f).roundToInt().coerceAtLeast(1)
        val out = Bitmap().apply { allocPixels(ImageInfo(w, h, ColorType.N32, ColorAlphaType.PREMUL)) }
        Canvas(out).use { c ->
            Image.makeFromBitmap(src).use { image ->
                c.drawImageRect(
                    image,
                    Rect.makeWH(src.width.toFloat(), src.height.toFloat()),
                    Rect.makeWH(w.toFloat(), h.toFloat()),
                    FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR),
                    null,
                    true,
                )
            }
        }
        src.close()
        return out
    }

    /** ARGB ints (as Android's getPixels returns them), unpremultiplied. */
    fun argb(bitmap: Bitmap): IntArray {
        val info = ImageInfo(bitmap.width, bitmap.height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
        val bytes = bitmap.readPixels(info, bitmap.width * 4, 0, 0) ?: error("Couldn't read pixels")
        return IntArray(bitmap.width * bitmap.height) { i ->
            val o = i * 4
            ((bytes[o + 3].toInt() and 255) shl 24) or ((bytes[o + 2].toInt() and 255) shl 16) or
                ((bytes[o + 1].toInt() and 255) shl 8) or (bytes[o].toInt() and 255)
        }
    }
}
