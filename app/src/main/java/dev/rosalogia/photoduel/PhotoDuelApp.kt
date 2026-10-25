package dev.rosalogia.photoduel

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.room.Room
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.SingletonImageLoader
import dev.rosalogia.photoduel.core.common.AppLog
import dev.rosalogia.photoduel.core.data.DataGraph
import dev.rosalogia.photoduel.core.data.db.PhotoDuelDatabase
import dev.rosalogia.photoduel.core.ml.FaceAnalyzer
import dev.rosalogia.photoduel.core.ml.OpenCv
import dev.rosalogia.photoduel.core.ui.AppGraph
import dev.rosalogia.photoduel.core.ui.Platform
import dev.rosalogia.photoduel.platform.AndroidPhotoDecoder
import dev.rosalogia.photoduel.platform.IndexWorkerFactory
import dev.rosalogia.photoduel.platform.MediaPipeFeatureExtractor
import dev.rosalogia.photoduel.platform.MediaStoreAlbumCopier
import dev.rosalogia.photoduel.platform.MediaStorePhotoSource
import dev.rosalogia.photoduel.platform.MlKitFaceDetector
import dev.rosalogia.photoduel.platform.PhotoRefMapper
import dev.rosalogia.photoduel.platform.WorkManagerIndexScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.opencv.android.OpenCVLoader
import java.io.File

class PhotoDuelApp : Application(), Configuration.Provider {
    /** The app's singletons, wired in [onCreate]. */
    lateinit var graph: AppGraph
        private set

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(IndexWorkerFactory { graph.data.featureIndexer }).build()

    /** Lives as long as the process; for work that must outlive any one screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AppLog.init(File(filesDir, "logs")) { priority, tag, msg -> Log.println(priority, tag, msg) }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, t ->
            AppLog.recordCrash(thread, t)
            previous?.uncaughtException(thread, t)
        }
        AppLog.i(
            "App",
            "Start ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) on ${Build.MANUFACTURER} ${Build.MODEL}, API ${Build.VERSION.SDK_INT}",
        )
        OpenCv.loader = { OpenCVLoader.initLocal() }
        graph = buildGraph()
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context).components { add(PhotoRefMapper()) }.build()
        }
        graph.data.coordinator.start(appScope, foreground())
    }

    private fun buildGraph(): AppGraph {
        val decoder = AndroidPhotoDecoder(this)
        val faces = FaceAnalyzer(decoder, MlKitFaceDetector())
        val data = DataGraph(
            db = Room.databaseBuilder(this, PhotoDuelDatabase::class.java, "photoduel.db").build(),
            // The same file the earlier preferencesDataStore("settings") delegate used.
            settingsStore = PreferenceDataStoreFactory.create { preferencesDataStoreFile("settings") },
            photos = MediaStorePhotoSource(this),
            albums = MediaStoreAlbumCopier(this),
            scheduler = WorkManagerIndexScheduler(this),
            featureExtractor = MediaPipeFeatureExtractor(this),
            qualityScorer = faces,
        )
        return AppGraph(data, decoder, faces, Platform(isDesktop = false))
    }

    /** True while any activity is started, like ProcessLifecycleOwner's STARTED state. */
    private fun foreground(): MutableStateFlow<Boolean> {
        val visible = MutableStateFlow(false)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                visible.value = true
            }

            override fun onStop(owner: LifecycleOwner) {
                visible.value = false
            }
        })
        return visible
    }
}
