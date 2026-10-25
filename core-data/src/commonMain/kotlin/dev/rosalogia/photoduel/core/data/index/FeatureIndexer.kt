package dev.rosalogia.photoduel.core.data.index

import dev.rosalogia.photoduel.core.common.AppLog
import dev.rosalogia.photoduel.core.data.LibraryIndexer
import dev.rosalogia.photoduel.core.data.PreScoreRepository
import dev.rosalogia.photoduel.core.data.db.PhotoDuelDatabase
import dev.rosalogia.photoduel.core.data.grouping.toBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Starts [FeatureIndexer.run] in the background: WorkManager on Android, a coroutine on desktop. */
interface IndexScheduler {
    /** Starts feature extraction unless it's already queued or running. */
    fun schedule()
}

/**
 * Computes embeddings and hashes for photos that don't have them, newest first, and regroups as it
 * goes so recent groups improve quickly. Resumable: all progress is in the database, so a stopped
 * run just picks up where it left off next time.
 */
class FeatureIndexer(
    private val db: PhotoDuelDatabase,
    private val extractor: PhotoFeatureExtractor,
    private val indexer: LibraryIndexer,
    private val preScores: PreScoreRepository,
    /** Photos processed at once: 1 on phones, more on desktops where decoding dominates. */
    private val parallelism: Int = 1,
) {
    /** [isStopped] is polled between photos; [onProgress] gets the number of photos indexed so far. */
    suspend fun run(isStopped: () -> Boolean, onProgress: suspend (Int) -> Unit = {}) {
        val dao = db.photoDao()
        var done = 0
        var failed = 0
        var lastRegroup = System.currentTimeMillis()
        val started = lastRegroup
        AppLog.i(TAG, "Indexing ${dao.unindexedCount()} photos")
        while (!isStopped()) {
            val batch = dao.unindexedIds(BATCH)
            if (batch.isEmpty()) break
            val permits = Semaphore(parallelism)
            val results = coroutineScope {
                batch.map { id ->
                    async {
                        permits.withPermit {
                            if (isStopped()) return@async null
                            id to try {
                                extractor.extract(id)
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                AppLog.w(TAG, "Feature extraction failed for $id", e)
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            for ((id, features) in results) {
                // Failures are marked indexed with no features so they aren't retried forever.
                dao.setFeatures(id, features?.embedding?.toBytes(), features?.dhash, System.currentTimeMillis())
                if (features == null) failed++
                done++
            }
            onProgress(done)
            if (System.currentTimeMillis() - lastRegroup > REGROUP_EVERY_MS) {
                indexer.regroup()
                lastRegroup = System.currentTimeMillis()
            }
        }
        indexer.regroup(logStats = done > 0)
        val secs = (System.currentTimeMillis() - started) / 1000.0
        AppLog.i(TAG, "Indexed $done photos ($failed failed) in ${"%.1f".format(secs)}s" +
            if (secs > 0) ", ${"%.1f".format(done / secs)}/s" else "")

        // Then pre-score photos in to-do groups, so opening a duel doesn't have to wait for it.
        var scored = 0
        val scoreStart = System.currentTimeMillis()
        while (!isStopped()) {
            val batch = dao.unscoredGroupMembers(BATCH)
            if (batch.isEmpty()) break
            for (id in batch) {
                if (isStopped()) break
                preScores.compute(id)
                scored++
            }
        }
        if (scored > 0) AppLog.i(TAG, "Pre-scored $scored photos in ${System.currentTimeMillis() - scoreStart}ms")
    }

    private companion object {
        const val TAG = "IndexWorker"
        const val BATCH = 32
        const val REGROUP_EVERY_MS = 4_000L
    }
}
