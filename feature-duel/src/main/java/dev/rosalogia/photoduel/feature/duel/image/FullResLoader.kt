package dev.rosalogia.photoduel.feature.duel.image

import android.content.Context
import android.graphics.ImageDecoder
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.rosalogia.photoduel.core.common.AppLog
import dev.rosalogia.photoduel.core.data.media.mediaContentUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

/**
 * Decodes photos at (near) full resolution for the duel screen, so flipping never decodes.
 *
 * Bitmaps are hardware-backed (GPU memory) and capped at [MAX_EDGE] px on the long side, which is
 * still well beyond screen resolution for zooming but keeps 50 MP shots within texture limits.
 */
@Singleton
class FullResLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = LruCache<Long, ImageBitmap>(CACHE_ENTRIES)
    private val inFlight = HashMap<Long, Deferred<ImageBitmap>>()
    private val lock = Mutex()

    fun peek(mediaId: Long): ImageBitmap? = cache.get(mediaId)

    suspend fun load(mediaId: Long): ImageBitmap {
        cache.get(mediaId)?.let { return it }
        val job = lock.withLock {
            cache.get(mediaId)?.let { return it }
            inFlight.getOrPut(mediaId) {
                scope.async {
                    try {
                        decode(mediaId).also { cache.put(mediaId, it) }
                    } finally {
                        lock.withLock { inFlight.remove(mediaId) }
                    }
                }
            }
        }
        return job.await()
    }

    /** Starts decoding in the background; failures are logged and surface on the next [load]. */
    fun prefetch(mediaId: Long) {
        if (cache.get(mediaId) != null) return
        scope.async { runCatching { load(mediaId) } }
    }

    private fun decode(mediaId: Long): ImageBitmap {
        lateinit var bitmap: android.graphics.Bitmap
        var original = Size(0, 0)
        val ms = measureTimeMillis {
            val source = ImageDecoder.createSource(context.contentResolver, mediaContentUri(mediaId))
            bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                original = info.size
                val longEdge = max(info.size.width, info.size.height)
                if (longEdge > MAX_EDGE) {
                    val f = MAX_EDGE.toFloat() / longEdge
                    decoder.setTargetSize((info.size.width * f).roundToInt(), (info.size.height * f).roundToInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_HARDWARE
            }
        }
        AppLog.d(TAG, "Decoded $mediaId ${original.width}x${original.height} -> ${bitmap.width}x${bitmap.height} in ${ms}ms")
        return bitmap.asImageBitmap()
    }

    private companion object {
        const val TAG = "FullRes"
        const val MAX_EDGE = 4096
        /** King, challenger, the prefetched next challenger, and the previous one for undo. */
        const val CACHE_ENTRIES = 4
    }
}
