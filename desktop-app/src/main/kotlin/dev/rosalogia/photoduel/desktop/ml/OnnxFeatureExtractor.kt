package dev.rosalogia.photoduel.desktop.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import dev.rosalogia.photoduel.core.data.index.PhotoFeatureExtractor
import dev.rosalogia.photoduel.core.data.index.PhotoFeatures
import dev.rosalogia.photoduel.core.ml.ArgbImage
import dev.rosalogia.photoduel.core.ml.DHash
import dev.rosalogia.photoduel.desktop.image.Decoding
import dev.rosalogia.photoduel.desktop.image.DesktopPhotoDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import java.nio.FloatBuffer
import kotlin.math.sqrt

/**
 * The phone app's embedding model (MobileNet-V3 small, converted from TFLite to ONNX) run with
 * ONNX Runtime, preprocessed the way MediaPipe does it: the whole frame stretched to 224×224, RGB
 * in 0..1. On test photos this matches MediaPipe's embeddings to a cosine similarity of 0.997.
 */
class OnnxFeatureExtractor(private val decoder: DesktopPhotoDecoder) : PhotoFeatureExtractor {
    // Lazy, so a missing native library stops indexing (an Error ends the run) instead of the app.
    private val env by lazy { OrtEnvironment.getEnvironment() }
    private val session: OrtSession by lazy {
        val model = javaClass.getResourceAsStream("/models/image_embedder.onnx")!!.use { it.readBytes() }
        env.createSession(model, OrtSession.SessionOptions().apply { setIntraOpNumThreads(1) })
    }

    override suspend fun extract(mediaId: Long): PhotoFeatures = withContext(Dispatchers.Default) {
        // Like the phone's MediaStore thumbnail: the photo fit within 256 px.
        val thumb = Decoding.scaleDown(decoder.thumbnail(mediaId), THUMB_PX)
        try {
            val dhash = DHash.of(ArgbImage(thumb.width, thumb.height, Decoding.argb(thumb)))
            PhotoFeatures(embedding = embed(thumb), dhash = dhash)
        } finally {
            thumb.close()
        }
    }

    internal fun embed(thumb: Bitmap): FloatArray {
        val input = stretch(thumb)
        val pixels = Decoding.argb(input).also { input.close() }
        val data = FloatBuffer.allocate(SIZE * SIZE * 3)
        for (p in pixels) {
            data.put((p shr 16 and 255) / 255f)
            data.put((p shr 8 and 255) / 255f)
            data.put((p and 255) / 255f)
        }
        data.rewind()
        OnnxTensor.createTensor(env, data, longArrayOf(1, SIZE.toLong(), SIZE.toLong(), 3)).use { tensor ->
            session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val out = (result[0].value as Array<FloatArray>)[0]
                val norm = sqrt(out.sumOf { (it * it).toDouble() }).toFloat()
                return FloatArray(out.size) { out[it] / norm }
            }
        }
    }

    private fun stretch(src: Bitmap): Bitmap {
        val out = Bitmap().apply { allocPixels(ImageInfo(SIZE, SIZE, ColorType.N32, ColorAlphaType.PREMUL)) }
        Canvas(out).use { c ->
            Image.makeFromBitmap(src).use { image ->
                c.drawImageRect(
                    image,
                    Rect.makeWH(src.width.toFloat(), src.height.toFloat()),
                    Rect.makeWH(SIZE.toFloat(), SIZE.toFloat()),
                    FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE),
                    null,
                    true,
                )
            }
        }
        return out
    }

    private companion object {
        const val SIZE = 224
        const val THUMB_PX = 256
    }
}
