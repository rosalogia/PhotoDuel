package dev.rosalogia.photoduel.feature.duel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rosalogia.photoduel.core.data.GroupRepository
import dev.rosalogia.photoduel.core.data.IndexState
import dev.rosalogia.photoduel.core.data.LibraryIndexer
import dev.rosalogia.photoduel.core.data.PhotoGroup
import dev.rosalogia.photoduel.core.data.db.GroupStatus
import dev.rosalogia.photoduel.core.data.settings.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class FeedUiState(
    /** To-do groups, newest first: the same scope and order as the list's To do tab. */
    val groups: List<PhotoGroup> = emptyList(),
    val showAllTime: Boolean = false,
    val indexing: Boolean = false,
    val analyzing: Int = 0,
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FeedViewModel @Inject constructor(
    groups: GroupRepository,
    private val settings: SettingsRepository,
    indexer: LibraryIndexer,
) : ViewModel() {
    private val todo = settings.settings.map { it.showAllTime }.distinctUntilChanged().flatMapLatest { allTime ->
        val since = if (allTime) 0L else System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
        groups.observeGroups(GroupStatus.UNRESOLVED, since).map { allTime to it }
    }

    val state: StateFlow<FeedUiState> = combine(todo, indexer.state, groups.observeUnindexedCount()) { (allTime, list), index, unindexed ->
        FeedUiState(
            groups = list,
            showAllTime = allTime,
            indexing = index == IndexState.Running,
            analyzing = unindexed,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedUiState())

    fun showAllTime() {
        viewModelScope.launch { settings.setShowAllTime(true) }
    }
}
