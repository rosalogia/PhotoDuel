package dev.rosalogia.photoduel.desktop

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.rosalogia.photoduel.core.data.DataGraph
import dev.rosalogia.photoduel.core.data.db.PhotoDuelDatabase
import dev.rosalogia.photoduel.core.ml.FaceAnalyzer
import dev.rosalogia.photoduel.core.ml.OpenCv
import dev.rosalogia.photoduel.core.ui.AppGraph
import dev.rosalogia.photoduel.core.ui.Platform
import dev.rosalogia.photoduel.desktop.catalog.CatalogDatabase
import dev.rosalogia.photoduel.desktop.catalog.CatalogDatabase_Impl
import dev.rosalogia.photoduel.desktop.catalog.FolderPhotoSource
import dev.rosalogia.photoduel.desktop.image.DesktopPhotoDecoder
import dev.rosalogia.photoduel.desktop.ml.DesktopOpenCv
import dev.rosalogia.photoduel.desktop.ml.OnnxFeatureExtractor
import dev.rosalogia.photoduel.desktop.ml.YuNetFaceDetector
import dev.rosalogia.photoduel.desktop.platform.CoroutineIndexScheduler
import dev.rosalogia.photoduel.desktop.platform.DesktopPhotoActions
import dev.rosalogia.photoduel.desktop.platform.FileAlbumCopier
import dev.rosalogia.photoduel.desktop.platform.FolderPrefs
import dev.rosalogia.photoduel.core.common.AppLog
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath
import java.io.File

/** The desktop app's singletons: the shared graph plus what only the desktop has (folders, scanning). */
class DesktopApp private constructor(
    val graph: AppGraph,
    val folders: FolderPrefs,
    val photos: FolderPhotoSource,
    val actions: DesktopPhotoActions,
    val decoder: DesktopPhotoDecoder,
    val highlightsRoot: File,
    val scope: CoroutineScope,
) {
    /** Starts syncing the library; it then follows folder changes for as long as the app runs. */
    fun start() {
        graph.data.coordinator.start(scope, MutableStateFlow(true))
    }

    fun requestSync() = graph.data.coordinator.requestSync(scope)

    companion object {
        fun create(): DesktopApp {
            OpenCv.loader = DesktopOpenCv::load
            val scope = CoroutineScope(
                SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> AppLog.e("App", "Background task failed", e) },
            )
            val catalog = Room.databaseBuilder(File(AppDirs.data, "catalog.db").path) { CatalogDatabase_Impl() }
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
            val folders = FolderPrefs(File(AppDirs.data, "folders.txt"))
            val highlightsRoot = File(AppDirs.pictures, "Highlights")
            val photos = FolderPhotoSource(catalog, folders.folders) { listOf(highlightsRoot) }
            val pathOf: suspend (Long) -> File? = photos::path
            val decoder = DesktopPhotoDecoder(pathOf, File(AppDirs.cache, "thumbs"))
            val faces = FaceAnalyzer(decoder, YuNetFaceDetector(File(AppDirs.cache, "models")))
            lateinit var data: DataGraph
            val scheduler = CoroutineIndexScheduler(scope) { isStopped -> data.featureIndexer.run(isStopped) }
            data = DataGraph(
                db = Room.databaseBuilder<PhotoDuelDatabase>(File(AppDirs.data, "photoduel.db").path)
                    .setDriver(BundledSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .build(),
                settingsStore = PreferenceDataStoreFactory.createWithPath {
                    File(AppDirs.data, "settings.preferences_pb").absolutePath.toPath()
                },
                photos = photos,
                albums = FileAlbumCopier(highlightsRoot, catalog.dao(), pathOf),
                scheduler = scheduler,
                featureExtractor = OnnxFeatureExtractor(decoder),
                qualityScorer = faces,
                indexParallelism = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 8),
            )
            lateinit var app: DesktopApp
            val actions = DesktopPhotoActions(pathOf, onLibraryChanged = { app.requestSync() })
            app = DesktopApp(AppGraph(data, decoder, faces, Platform(isDesktop = true)), folders, photos, actions, decoder, highlightsRoot, scope)
            return app
        }
    }
}
