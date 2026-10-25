package dev.rosalogia.photoduel.core.data

import dev.rosalogia.photoduel.core.data.media.PhotoSource
import dev.rosalogia.photoduel.core.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * Runs a library sync whenever the app comes to the foreground, and again (debounced) whenever
 * the library reports a change or the grouping settings change while the app is visible.
 */
class IndexingCoordinator(
    private val indexer: LibraryIndexer,
    private val media: PhotoSource,
    private val settings: SettingsRepository,
) {
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope, foreground: Flow<Boolean>) {
        scope.launch {
            foreground.distinctUntilChanged().collectLatest { visible ->
                if (!visible) return@collectLatest
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
