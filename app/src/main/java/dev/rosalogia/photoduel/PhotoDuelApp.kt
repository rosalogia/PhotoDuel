package dev.rosalogia.photoduel

import android.app.Application
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.rosalogia.photoduel.core.common.AppLog
import dev.rosalogia.photoduel.core.data.IndexingCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

@HiltAndroidApp
class PhotoDuelApp : Application(), Configuration.Provider {
    @Inject lateinit var indexing: IndexingCoordinator
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    /** Lives as long as the process; for work that must outlive any one screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, t ->
            AppLog.recordCrash(thread, t)
            previous?.uncaughtException(thread, t)
        }
        AppLog.i(
            "App",
            "Start ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) on ${Build.MANUFACTURER} ${Build.MODEL}, API ${Build.VERSION.SDK_INT}",
        )
        indexing.start(appScope)
    }
}
