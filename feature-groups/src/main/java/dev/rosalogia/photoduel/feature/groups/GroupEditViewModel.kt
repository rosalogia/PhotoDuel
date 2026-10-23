package dev.rosalogia.photoduel.feature.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rosalogia.photoduel.core.data.GroupEditor
import dev.rosalogia.photoduel.core.data.GroupRepository
import dev.rosalogia.photoduel.core.data.PhotoGroup
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GroupEditUiState(
    val group: PhotoGroup? = null,
    val previous: PhotoGroup? = null,
    val next: PhotoGroup? = null,
    val busy: Boolean = false,
    val loaded: Boolean = false,
)

sealed interface GroupEditEvent {
    data class Message(val text: String) : GroupEditEvent
    data object Close : GroupEditEvent
}

@HiltViewModel(assistedFactory = GroupEditViewModel.Factory::class)
class GroupEditViewModel @AssistedInject constructor(
    @Assisted private val groupId: Long,
    private val groups: GroupRepository,
    private val editor: GroupEditor,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(groupId: Long): GroupEditViewModel
    }

    private val neighbors = MutableStateFlow<Pair<PhotoGroup?, PhotoGroup?>>(null to null)
    private val busy = MutableStateFlow(false)
    private val _events = Channel<GroupEditEvent>(Channel.BUFFERED)
    val events: Flow<GroupEditEvent> = _events.receiveAsFlow()
    private var closed = false

    val state: StateFlow<GroupEditUiState> = combine(groups.observeGroup(groupId), neighbors, busy) { g, (prev, next), b ->
        GroupEditUiState(group = g, previous = prev, next = next, busy = b, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupEditUiState())

    init {
        refreshNeighbors()
        viewModelScope.launch {
            // An edit can dissolve the group (fewer than two photos left); leave when that happens.
            var seen = false
            groups.observeGroup(groupId).collect { g ->
                if (g != null) seen = true else if (seen) close()
            }
        }
    }

    private fun refreshNeighbors() = viewModelScope.launch {
        val (p, n) = editor.neighbors(groupId)
        neighbors.value = p?.let { groups.getGroup(it) } to n?.let { groups.getGroup(it) }
    }

    fun remove(mediaId: Long) = edit("Removed from group") { editor.removePhoto(mediaId) }

    fun splitBefore(mediaId: Long) = edit("Split into two groups", close = true) { editor.splitBefore(groupId, mediaId) }

    fun mergeWith(otherGroupId: Long) = edit("Groups merged", close = true) { editor.merge(groupId, otherGroupId) }

    private fun edit(message: String, close: Boolean = false, block: suspend () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            try {
                block()
            } finally {
                busy.value = false
            }
            _events.send(GroupEditEvent.Message(message))
            if (close) close() else refreshNeighbors()
        }
    }

    private suspend fun close() {
        if (closed) return
        closed = true
        _events.send(GroupEditEvent.Close)
    }
}
