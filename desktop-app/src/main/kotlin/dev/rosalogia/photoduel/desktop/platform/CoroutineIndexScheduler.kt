package dev.rosalogia.photoduel.desktop.platform

import dev.rosalogia.photoduel.core.common.AppLog
import dev.rosalogia.photoduel.core.data.index.IndexScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Runs indexing as a coroutine for as long as the app is open; there's no OS job scheduler to hand it to. */
class CoroutineIndexScheduler(
    private val scope: CoroutineScope,
    private val index: suspend (isStopped: () -> Boolean) -> Unit,
) : IndexScheduler {
    private var job: Job? = null

    @Synchronized
    override fun schedule() {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                index { !isActive }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                AppLog.e("IndexWorker", "Indexing run failed", e)
            }
        }
    }
}
