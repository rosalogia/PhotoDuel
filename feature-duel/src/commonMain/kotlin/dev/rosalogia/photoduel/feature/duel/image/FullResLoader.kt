package dev.rosalogia.photoduel.feature.duel.image

import androidx.compose.ui.graphics.ImageBitmap
import dev.rosalogia.photoduel.core.common.AppLog
import dev.rosalogia.photoduel.core.common.LruCache
import dev.rosalogia.photoduel.core.ml.PhotoDecoder
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Decodes photos at (near) full resolution for the duel screen, so flipping never decodes.
 *
 * Images are capped at [MAX_EDGE] px on the long side (and GPU-backed where the platform allows),
 * which is still well beyond screen resolution for zooming but keeps 50 MP shots within texture limits.
 */
class FullResLoader(
    private val decoder: PhotoDecoder,
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

    private suspend fun decode(mediaId: Long): ImageBitmap {
        val image: ImageBitmap
        val ms = measureTimeMillis { image = decoder.decodeDisplay(mediaId, MAX_EDGE) }
        AppLog.d(TAG, "Decoded $mediaId at ${image.width}x${image.height} in ${ms}ms")
        return image
    }

    private companion object {
        const val TAG = "FullRes"
        const val MAX_EDGE = 4096
        /** King, challenger, the prefetched next challenger, and the previous one for undo. */
        const val CACHE_ENTRIES = 4
    }
}
