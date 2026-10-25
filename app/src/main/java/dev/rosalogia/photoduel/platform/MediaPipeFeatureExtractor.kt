package dev.rosalogia.photoduel.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Size
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import dev.rosalogia.photoduel.core.data.index.PhotoFeatureExtractor
import dev.rosalogia.photoduel.core.data.index.PhotoFeatures
import dev.rosalogia.photoduel.core.ml.DHash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Embeds each photo with a small bundled image-embedding model (MobileNet-V3 small via MediaPipe)
 * and computes its dHash. Works from MediaStore's cached thumbnail, which is far cheaper than
 * decoding the full photo and plenty for a 224 px model input.
 */
class MediaPipeFeatureExtractor(
    private val context: Context,
) : PhotoFeatureExtractor {
    // ImageEmbedder isn't thread-safe; one photo at a time.
    private val mutex = Mutex()
    private var embedder: ImageEmbedder? = null

    private fun embedder(): ImageEmbedder = embedder ?: ImageEmbedder.createFromOptions(
        context,
        ImageEmbedder.ImageEmbedderOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
            .setRunningMode(RunningMode.IMAGE)
            .setL2Normalize(true)
            .setQuantize(false)
            .build(),
    ).also { embedder = it }

    override suspend fun extract(mediaId: Long): PhotoFeatures = withContext(Dispatchers.Default) {
        val thumb = context.contentResolver.loadThumbnail(mediaContentUri(mediaId), Size(THUMB_PX, THUMB_PX), null)
        try {
            val argb = if (thumb.config == Bitmap.Config.ARGB_8888) thumb else thumb.copy(Bitmap.Config.ARGB_8888, false)
            val embedding = mutex.withLock {
                embedder().embed(BitmapImageBuilder(argb).build())
                    .embeddingResult().embeddings().first().floatEmbedding()
            }
            PhotoFeatures(embedding = embedding, dhash = dHash(argb))
        } finally {
            thumb.recycle()
        }
    }

    /** Bilinear-shrinks to 9×8 like the first versions did, so hashes stay comparable with stored ones. */
    private fun dHash(bitmap: Bitmap): Long {
        val small = Bitmap.createScaledBitmap(bitmap, DHash.WIDTH, DHash.HEIGHT, true)
        val pixels = IntArray(DHash.WIDTH * DHash.HEIGHT)
        small.getPixels(pixels, 0, DHash.WIDTH, 0, 0, DHash.WIDTH, DHash.HEIGHT)
        if (small !== bitmap) small.recycle()
        return DHash.fromLuma(IntArray(pixels.size) { i ->
            val p = pixels[i]
            (299 * Color.red(p) + 587 * Color.green(p) + 114 * Color.blue(p)) / 1000
        })
    }

    private companion object {
        const val MODEL_ASSET = "image_embedder.tflite"
        const val THUMB_PX = 256
    }
}
