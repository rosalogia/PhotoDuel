package dev.rosalogia.photoduel.platform

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.rosalogia.photoduel.core.common.AppLog
import dev.rosalogia.photoduel.core.data.index.FeatureIndexer
import dev.rosalogia.photoduel.core.data.index.IndexScheduler
import kotlinx.coroutines.CancellationException

class WorkManagerIndexScheduler(private val context: Context) : IndexScheduler {
    override fun schedule() {
        val request = OneTimeWorkRequestBuilder<FeatureIndexWorker>()
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        const val WORK_NAME = "feature-index"
    }
}

/** Runs [FeatureIndexer] under WorkManager, so indexing survives the app going to the background. */
class FeatureIndexWorker(
    context: Context,
    params: WorkerParameters,
    private val indexer: FeatureIndexer,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        indexer.run(isStopped = { isStopped }, onProgress = { setProgress(workDataOf(PROGRESS_DONE to it)) })
        Result.success()
    } catch (e: Throwable) {
        // Errors (e.g. a native library failing to initialize) end the run without marking photos
        // as failed, so they stay time-grouped and get retried next time. Log so it reaches shared logs.
        if (e is CancellationException) throw e
        AppLog.e(TAG, "Indexing run failed", e)
        Result.failure()
    }

    private companion object {
        const val TAG = "IndexWorker"
        const val PROGRESS_DONE = "done"
    }
}

/** Creates [FeatureIndexWorker]s with the app's indexer; WorkManager falls back to reflection otherwise. */
class IndexWorkerFactory(private val indexer: () -> FeatureIndexer) : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        if (workerClassName == FeatureIndexWorker::class.java.name) FeatureIndexWorker(appContext, workerParameters, indexer()) else null
}
