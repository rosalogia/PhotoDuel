package dev.rosalogia.photoduel.feature.highlights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rosalogia.photoduel.core.common.AppLog
import dev.rosalogia.photoduel.core.data.db.HighlightCullEntity
import dev.rosalogia.photoduel.core.data.db.HighlightItemEntity
import dev.rosalogia.photoduel.core.data.db.HighlightPhase
import dev.rosalogia.photoduel.core.data.db.HighlightSetEntity
import dev.rosalogia.photoduel.core.data.highlights.HighlightRepository
import dev.rosalogia.photoduel.core.data.media.AlbumCopier
import dev.rosalogia.photoduel.feature.highlights.engine.Comparison
import dev.rosalogia.photoduel.feature.highlights.engine.Elimination
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class HighlightPhoto(val mediaId: Long, val burstSize: Int)

sealed interface HighlightUiState {
    data object Loading : HighlightUiState

    data class Error(val message: String) : HighlightUiState

    /** Keep/skip, one photo at a time. */
    data class Cull(
        val name: String,
        val pass: Int,
        val position: Int,
        val total: Int,
        val kept: Int,
        val target: Int,
        val current: HighlightPhoto,
        val next: HighlightPhoto?,
        val canUndo: Boolean,
    ) : HighlightUiState

    /** A quick pass is finished: narrow down, go again, or use what's kept. */
    data class PassDone(
        val name: String,
        val pass: Int,
        val reviewed: Int,
        val kept: List<HighlightPhoto>,
        val target: Int,
        val estimatedPicks: Int,
    ) : HighlightUiState

    /** Head to head until only the target is left. */
    data class Trim(
        val name: String,
        val first: HighlightPhoto,
        val second: HighlightPhoto,
        val stillIn: Int,
        val target: Int,
        val picksLeft: Int,
        val canUndo: Boolean,
    ) : HighlightUiState

    data class Result(
        val name: String,
        val startTime: Long,
        val endTime: Long,
        val selected: List<HighlightPhoto>,
        val target: Int,
        val copied: Int,
        /** Progress while copying to the album: done so far, or null when idle. */
        val copying: Int?,
        val albumPath: String,
    ) : HighlightUiState
}

sealed interface HighlightEvent {
    data class Message(val text: String) : HighlightEvent
}

@HiltViewModel(assistedFactory = HighlightSessionViewModel.Factory::class)
class HighlightSessionViewModel @AssistedInject constructor(
    @Assisted private val setId: Long,
    private val repo: HighlightRepository,
    private val copier: AlbumCopier,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(setId: Long): HighlightSessionViewModel
    }

    private val _state = MutableStateFlow<HighlightUiState>(HighlightUiState.Loading)
    val state: StateFlow<HighlightUiState> = _state.asStateFlow()

    private val _events = Channel<HighlightEvent>(Channel.BUFFERED)
    val events: Flow<HighlightEvent> = _events.receiveAsFlow()

    private val mutex = Mutex()

    private lateinit var set: HighlightSetEntity
    private var items: List<HighlightItemEntity> = emptyList()
    private val culls = ArrayList<HighlightCullEntity>()
    private val comparisonIds = ArrayList<Long>()
    private var elimination: Elimination? = null
    private var copying: Int? = null

    init {
        act { load() }
    }

    private suspend fun load() {
        val session = repo.load(setId)
        if (session == null) {
            _state.value = HighlightUiState.Error("This set no longer exists.")
            return
        }
        set = session.set
        items = session.items
        culls.clear()
        culls += session.culls
        comparisonIds.clear()
        elimination = null
        if (set.phase == HighlightPhase.TRIM || (set.phase == HighlightPhase.DONE && session.comparisons.isNotEmpty())) {
            val (e, used) = Elimination.replay(keptIn(set.passCount).map { it.mediaId }, set.targetCount, session.comparisons.map { Comparison(it.winnerMediaId, it.loserMediaId) })
            elimination = e
            comparisonIds += session.comparisons.take(used).map { it.id }
            val stale = session.comparisons.drop(used).map { it.id }
            if (stale.isNotEmpty()) {
                AppLog.i(TAG, "Set $setId: dropping ${stale.size} comparisons that no longer fit")
                repo.deleteComparisons(stale)
            }
            if (set.phase == HighlightPhase.TRIM && e.isFinished) finish(e.active)
        }
        AppLog.i(TAG, "Set $setId: ${items.size} photos, phase ${set.phase}, pass ${set.passCount}, ${culls.size} swipes, ${comparisonIds.size} picks")
        render()
    }

    /** Photos reviewed in [pass]: everything for the first, then what the previous pass kept. */
    private fun reviewedIn(pass: Int): List<HighlightItemEntity> = if (pass <= 1) items else keptIn(pass - 1)

    private fun keptIn(pass: Int): List<HighlightItemEntity> {
        val kept = culls.filter { it.pass == pass && it.keep }.map { it.mediaId }.toSet()
        return reviewedIn(pass).filter { it.mediaId in kept }
    }

    private fun HighlightItemEntity.photo() = HighlightPhoto(mediaId, burstSize)

    private fun render() {
        val name = set.name
        when (set.phase) {
            HighlightPhase.DONE -> {
                val selected = items.filter { it.selected }
                _state.value = HighlightUiState.Result(
                    name = name,
                    startTime = set.startTime,
                    endTime = set.endTime,
                    selected = selected.map { it.photo() },
                    target = set.targetCount,
                    copied = selected.count { it.albumMediaId != null },
                    copying = copying,
                    albumPath = copier.albumPath(name),
                )
            }
            HighlightPhase.TRIM -> {
                val e = elimination ?: return
                val (a, b) = e.next() ?: return
                val byId = items.associateBy { it.mediaId }
                _state.value = HighlightUiState.Trim(
                    name = name,
                    first = byId.getValue(a).photo(),
                    second = byId.getValue(b).photo(),
                    stillIn = e.active.size,
                    target = set.targetCount,
                    picksLeft = e.remainingEstimate(),
                    canUndo = true,
                )
            }
            HighlightPhase.CULL -> {
                val pass = set.passCount
                val review = reviewedIn(pass)
                val decided = culls.filter { it.pass == pass }.map { it.mediaId }.toSet()
                val remaining = review.filter { it.mediaId !in decided }
                if (remaining.isEmpty()) {
                    val kept = keptIn(pass)
                    _state.value = HighlightUiState.PassDone(
                        name = name,
                        pass = pass,
                        reviewed = review.size,
                        kept = kept.map { it.photo() },
                        target = set.targetCount,
                        estimatedPicks = Elimination(kept.map { it.mediaId }, set.targetCount).remainingEstimate(),
                    )
                } else {
                    _state.value = HighlightUiState.Cull(
                        name = name,
                        pass = pass,
                        position = review.size - remaining.size,
                        total = review.size,
                        kept = culls.count { it.pass == pass && it.keep },
                        target = set.targetCount,
                        current = remaining[0].photo(),
                        next = remaining.getOrNull(1)?.photo(),
                        canUndo = decided.isNotEmpty() || pass > 1,
                    )
                }
            }
        }
    }

    fun cull(keep: Boolean) = act {
        val s = _state.value as? HighlightUiState.Cull ?: return@act
        val id = repo.recordCull(setId, s.current.mediaId, s.pass, keep)
        culls += HighlightCullEntity(id, setId, s.current.mediaId, s.pass, keep, System.currentTimeMillis())
        render()
    }

    fun anotherPass() = act {
        val s = _state.value as? HighlightUiState.PassDone ?: return@act
        if (s.kept.isEmpty()) return@act
        progress(HighlightPhase.CULL, set.passCount + 1)
        render()
    }

    fun startTrim() = act {
        val s = _state.value as? HighlightUiState.PassDone ?: return@act
        elimination = Elimination(s.kept.map { it.mediaId }, set.targetCount)
        comparisonIds.clear()
        if (elimination!!.isFinished) {
            finish(s.kept.map { it.mediaId })
        } else {
            progress(HighlightPhase.TRIM, set.passCount)
        }
        render()
    }

    /** Keeps everything the last pass kept, even if that's more than the target. */
    fun useKept() = act {
        val s = _state.value as? HighlightUiState.PassDone ?: return@act
        if (s.kept.isEmpty()) return@act
        elimination = null
        finish(s.kept.map { it.mediaId })
        render()
    }

    fun pick(winner: Long) = act {
        val s = _state.value as? HighlightUiState.Trim ?: return@act
        val e = elimination ?: return@act
        val loser = if (winner == s.first.mediaId) s.second.mediaId else s.first.mediaId
        e.record(winner, loser)
        comparisonIds += repo.recordComparison(setId, winner, loser)
        if (e.isFinished) finish(e.active)
        render()
    }

    fun undo() = act {
        when (set.phase) {
            HighlightPhase.DONE -> {
                val back = if (elimination != null) HighlightPhase.TRIM else HighlightPhase.CULL
                repo.setResult(setId, emptyList(), back, set.passCount)
                set = set.copy(phase = back)
                items = items.map { it.copy(selected = false) }
                // Reopening a finished trim takes back its last pick too, so there's a pair to show.
                if (back == HighlightPhase.TRIM) undoComparison()
            }
            HighlightPhase.TRIM -> if (comparisonIds.isNotEmpty()) undoComparison() else {
                elimination = null
                progress(HighlightPhase.CULL, set.passCount)
            }
            HighlightPhase.CULL -> {
                val last = culls.lastOrNull { it.pass == set.passCount }
                when {
                    last != null -> {
                        repo.deleteCull(last.id)
                        culls.remove(last)
                    }
                    set.passCount > 1 -> progress(HighlightPhase.CULL, set.passCount - 1)
                    else -> return@act
                }
            }
        }
        render()
    }

    private suspend fun undoComparison() {
        val e = elimination ?: return
        if (comparisonIds.isEmpty()) {
            elimination = null
            progress(HighlightPhase.CULL, set.passCount)
            return
        }
        repo.deleteComparisons(listOf(comparisonIds.removeAt(comparisonIds.lastIndex)))
        elimination = Elimination.replay(e.items, e.target, e.history.dropLast(1)).first
    }

    fun startOver() = act {
        repo.reset(setId)
        load()
    }

    fun copyToAlbum() = act {
        val s = _state.value as? HighlightUiState.Result ?: return@act
        if (copying != null) return@act
        viewModelScope.launch { copyAll(s) }
    }

    private suspend fun copyAll(s: HighlightUiState.Result) {
        var done = 0
        var made = 0
        var failed = 0
        for (photo in s.selected) {
            mutex.withLock {
                copying = done
                render()
            }
            val item = items.first { it.mediaId == photo.mediaId }
            val existing = item.albumMediaId
            if (existing == null || !copier.exists(existing)) {
                try {
                    val copy = copier.copy(photo.mediaId, set.name)
                    repo.setAlbumCopy(setId, photo.mediaId, copy)
                    mutex.withLock { items = items.map { if (it.mediaId == photo.mediaId) it.copy(albumMediaId = copy) else it } }
                    made++
                } catch (e: Exception) {
                    AppLog.e(TAG, "Copy of ${photo.mediaId} failed", e)
                    failed++
                }
            }
            done++
        }
        mutex.withLock {
            copying = null
            render()
        }
        val where = copier.albumPath(set.name).trimEnd('/')
        _events.send(
            HighlightEvent.Message(
                when {
                    failed > 0 -> "Copied $made, but $failed couldn't be copied."
                    made == 0 -> "Already in $where"
                    else -> "Copied $made to $where"
                },
            ),
        )
    }

    fun rename(name: String) = act {
        val clean = name.trim()
        if (clean.isEmpty() || clean == set.name) return@act
        repo.rename(setId, clean)
        set = set.copy(name = clean)
        render()
    }

    fun onFavorited() = act { _events.send(HighlightEvent.Message("Added to favorites")) }

    private suspend fun finish(selected: List<Long>) {
        repo.setResult(setId, selected, HighlightPhase.DONE, set.passCount)
        set = set.copy(phase = HighlightPhase.DONE)
        val chosen = selected.toSet()
        items = items.map { it.copy(selected = it.mediaId in chosen) }
        AppLog.i(TAG, "Set $setId done: ${selected.size} highlights")
    }

    private suspend fun progress(phase: HighlightPhase, passCount: Int) {
        repo.setProgress(setId, phase, passCount)
        set = set.copy(phase = phase, passCount = passCount)
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch { mutex.withLock { block() } }
    }

    private companion object {
        const val TAG = "Highlights"
    }
}
