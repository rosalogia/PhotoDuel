package dev.rosalogia.photoduel.feature.groups

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
import kotlinx.coroutines.flow.MutableStateFlow
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

enum class GroupsTab { TODO, DONE, HIGHLIGHTS }

data class GroupsUiState(
    val tab: GroupsTab = GroupsTab.TODO,
    val showAllTime: Boolean = false,
    val todo: List<PhotoGroup> = emptyList(),
    val done: List<PhotoGroup> = emptyList(),
    val indexing: Boolean = false,
    /** Photos not yet analyzed for visual grouping. */
    val analyzing: Int = 0,
    val loaded: Boolean = false,
) {
    val visible: List<PhotoGroup> get() = when (tab) {
        GroupsTab.TODO -> todo
        GroupsTab.DONE -> done
        GroupsTab.HIGHLIGHTS -> emptyList()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val groups: GroupRepository,
    private val settings: SettingsRepository,
    indexer: LibraryIndexer,
) : ViewModel() {
    private val tab = MutableStateFlow(GroupsTab.TODO)

    private val showAllTime = settings.settings.map { it.showAllTime }.distinctUntilChanged()

    private val lists = showAllTime.flatMapLatest { allTime ->
        val since = if (allTime) 0L else System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
        combine(
            groups.observeGroups(GroupStatus.UNRESOLVED, since),
            groups.observeGroups(GroupStatus.DONE, since),
        ) { todo, done -> Triple(allTime, todo, done) }
    }

    val state: StateFlow<GroupsUiState> = combine(
        tab,
        lists,
        indexer.state,
        groups.observeUnindexedCount(),
    ) { tab, (allTime, todo, done), index, unindexed ->
        GroupsUiState(
            tab = tab,
            showAllTime = allTime,
            todo = todo,
            done = done,
            indexing = index == IndexState.Running,
            analyzing = unindexed,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupsUiState())

    fun selectTab(t: GroupsTab) {
        tab.value = t
    }

    fun setShowAllTime(value: Boolean) {
        viewModelScope.launch { settings.setShowAllTime(value) }
    }
}
