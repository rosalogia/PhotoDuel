package dev.rosalogia.photoduel.core.data.index

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.rosalogia.photoduel.core.common.AppLog
import dev.rosalogia.photoduel.core.data.LibraryIndexer
import dev.rosalogia.photoduel.core.data.PreScoreRepository
import dev.rosalogia.photoduel.core.data.db.PhotoDuelDatabase
import dev.rosalogia.photoduel.core.data.grouping.toBytes
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IndexScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Starts feature extraction unless it's already queued or running. */
    fun schedule() {
        val request = OneTimeWorkRequestBuilder<FeatureIndexWorker>()
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        const val WORK_NAME = "feature-index"
    }
}

/**
 * Computes embeddings and hashes for photos that don't have them, newest first, and regroups as it
 * goes so recent groups improve quickly. Resumable: all progress is in the database, so a stopped
 * worker just picks up where it left off next time.
 */
@HiltWorker
class FeatureIndexWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val db: PhotoDuelDatabase,
    private val extractor: PhotoFeatureExtractor,
    private val indexer: LibraryIndexer,
    private val preScores: PreScoreRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        index()
    } catch (e: Throwable) {
        // Errors (e.g. a native library failing to initialize) end the run without marking photos
        // as failed, so they stay time-grouped and get retried next time. Log so it reaches shared logs.
        if (e is CancellationException) throw e
        AppLog.e(TAG, "Indexing run failed", e)
        Result.failure()
    }

    private suspend fun index(): Result {
        val dao = db.photoDao()
        var done = 0
        var failed = 0
        var lastRegroup = System.currentTimeMillis()
        val started = lastRegroup
        AppLog.i(TAG, "Indexing ${dao.unindexedCount()} photos")
        while (!isStopped) {
            val batch = dao.unindexedIds(BATCH)
            if (batch.isEmpty()) break
            for (id in batch) {
                if (isStopped) break
                val features = try {
                    extractor.extract(id)
                } catch (e: Exception) {
                    AppLog.w(TAG, "Feature extraction failed for $id", e)
                    failed++
                    null
                }
                // Failures are marked indexed with no features so they aren't retried forever.
                dao.setFeatures(id, features?.embedding?.toBytes(), features?.dhash, System.currentTimeMillis())
                done++
            }
            setProgress(workDataOf(PROGRESS_DONE to done))
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
        while (!isStopped) {
            val batch = dao.unscoredGroupMembers(BATCH)
            if (batch.isEmpty()) break
            for (id in batch) {
                if (isStopped) break
                preScores.compute(id)
                scored++
            }
        }
        if (scored > 0) AppLog.i(TAG, "Pre-scored $scored photos in ${System.currentTimeMillis() - scoreStart}ms")
        return Result.success()
    }

    private companion object {
        const val TAG = "IndexWorker"
        const val BATCH = 32
        const val REGROUP_EVERY_MS = 4_000L
        const val PROGRESS_DONE = "done"
    }
}
