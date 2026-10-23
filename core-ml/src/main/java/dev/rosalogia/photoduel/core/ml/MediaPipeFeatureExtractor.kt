package dev.rosalogia.photoduel.core.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.rosalogia.photoduel.core.data.index.PhotoFeatureExtractor
import dev.rosalogia.photoduel.core.data.index.PhotoFeatures
import dev.rosalogia.photoduel.core.data.media.mediaContentUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Embeds each photo with a small bundled image-embedding model (MobileNet-V3 small via MediaPipe)
 * and computes its dHash. Works from MediaStore's cached thumbnail, which is far cheaper than
 * decoding the full photo and plenty for a 224 px model input.
 */
@Singleton
class MediaPipeFeatureExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
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
            PhotoFeatures(embedding = embedding, dhash = DHash.of(argb))
        } finally {
            thumb.recycle()
        }
    }

    private companion object {
        const val MODEL_ASSET = "image_embedder.tflite"
        const val THUMB_PX = 256
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MlModule {
    @Binds
    abstract fun featureExtractor(impl: MediaPipeFeatureExtractor): PhotoFeatureExtractor
}
