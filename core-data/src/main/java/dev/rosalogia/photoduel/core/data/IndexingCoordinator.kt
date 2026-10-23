package dev.rosalogia.photoduel.core.data

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.rosalogia.photoduel.core.data.media.MediaStoreRepository
import dev.rosalogia.photoduel.core.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs a library sync whenever the app comes to the foreground, and again (debounced) whenever
 * MediaStore reports a change or the grouping settings change while the app is visible.
 */
@Singleton
class IndexingCoordinator @Inject constructor(
    private val indexer: LibraryIndexer,
    private val media: MediaStoreRepository,
    private val settings: SettingsRepository,
) {
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val settingsChanges = settings.settings
                    .distinctUntilChangedBy { it.sessionGapMs to it.similarityThreshold }
                    .drop(1)
                merge(media.changes(), settingsChanges)
                    .debounce(1_500)
                    .onStart { emit(Unit) }
                    .collect { indexer.sync() }
            }
        }
    }

    /** For callers that know access just changed, e.g. right after a permission grant. */
    fun requestSync(scope: CoroutineScope) {
        scope.launch { indexer.sync() }
    }
}
