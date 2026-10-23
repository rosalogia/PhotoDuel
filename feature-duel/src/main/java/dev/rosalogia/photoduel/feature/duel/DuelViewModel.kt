package dev.rosalogia.photoduel.feature.duel

import android.graphics.RectF
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rosalogia.photoduel.core.common.AppLog
import dev.rosalogia.photoduel.core.data.DuelRepository
import dev.rosalogia.photoduel.core.data.GroupRepository
import dev.rosalogia.photoduel.core.data.PreScoreRepository
import dev.rosalogia.photoduel.core.data.PhotoGroup
import dev.rosalogia.photoduel.core.data.db.GroupStatus
import dev.rosalogia.photoduel.core.ml.FaceAnalyzer
import dev.rosalogia.photoduel.feature.duel.align.Aligner
import dev.rosalogia.photoduel.feature.duel.align.Alignment
import dev.rosalogia.photoduel.feature.duel.engine.Decision
import dev.rosalogia.photoduel.feature.duel.engine.KingOfTheHill
import dev.rosalogia.photoduel.feature.duel.engine.Matchup
import dev.rosalogia.photoduel.feature.duel.image.FullResLoader
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class Round { MAIN, RUNNER_UP }

sealed interface DuelUiState {
    data object Loading : DuelUiState

    data class Error(val message: String) : DuelUiState

    data class Dueling(
        val round: Round,
        val matchup: Matchup,
        val king: ImageBitmap?,
        val challenger: ImageBitmap?,
        /** Null when alignment failed or was rejected: the challenger is then shown unaligned. */
        val alignment: Alignment?,
        val alignmentKnown: Boolean,
        /** Largest face in the king, normalized; double-tap zooms to it. */
        val kingFace: RectF?,
        val canUndo: Boolean,
    ) : DuelUiState {
        /** Both photos decoded and registered, so the first flip is already instant and steady. */
        val ready: Boolean get() = king != null && challenger != null && alignmentKnown
    }

    data class Result(
        val winner: Long,
        val runnerUp: Long?,
        /** Every other photo in the group: what "Trash the rest" would remove. */
        val rest: List<Long>,
        val canPickRunnerUp: Boolean,
        val status: GroupStatus,
        val canUndo: Boolean,
        val groupSize: Int,
    ) : DuelUiState
}

sealed interface DuelEvent {
    data class Message(val text: String) : DuelEvent
    data object Close : DuelEvent
}

@HiltViewModel(assistedFactory = DuelViewModel.Factory::class)
class DuelViewModel @AssistedInject constructor(
    @Assisted private val groupId: Long,
    private val groups: GroupRepository,
    private val duels: DuelRepository,
    private val loader: FullResLoader,
    private val aligner: Aligner,
    private val preScores: PreScoreRepository,
    private val faces: FaceAnalyzer,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(groupId: Long): DuelViewModel
    }

    private val _state = MutableStateFlow<DuelUiState>(DuelUiState.Loading)
    val state: StateFlow<DuelUiState> = _state.asStateFlow()

    private val _events = Channel<DuelEvent>(Channel.BUFFERED)
    val events: Flow<DuelEvent> = _events.receiveAsFlow()

    /** Serializes picks/undos so stored decision IDs stay in step with the engine. */
    private val mutex = Mutex()

    private var group: PhotoGroup? = null
    private var main: KingOfTheHill? = null
    private var runnerUpDuel: KingOfTheHill? = null
    /** Runner-up chosen without a duel, when only one photo lost directly to the winner. */
    private var runnerUpSingle: Long? = null
    /** IDs of stored decisions: main round first, then runner-up round. */
    private val decisionIds = ArrayList<Long>()
    private var stored: PhotoGroup? = null

    /** Alignment results per (king, challenger); a present key with a null value means "show unaligned". */
    private val alignments = HashMap<Pair<Long, Long>, Alignment?>()
    private val loading = HashSet<Matchup>()
    private val largestFace = HashMap<Long, RectF?>()

    init {
        viewModelScope.launch { mutex.withLock { load() } }
    }

    private suspend fun load() {
        val g = groups.getGroup(groupId)
        if (g == null) {
            _state.value = DuelUiState.Error("This group no longer exists.")
            return
        }
        group = g
        decisionIds.clear()
        main = null
        runnerUpDuel = null
        runnerUpSingle = null

        if (g.status != GroupStatus.UNRESOLVED && g.winnerMediaId != null && g.winnerMediaId in g.mediaIds) {
            stored = g
            render()
            return
        }
        stored = null
        if (g.mediaIds.size < 2) {
            _state.value = DuelUiState.Error("This group has fewer than two photos left.")
            return
        }

        // Strongest candidates first, so they meet early. Scores are persisted, so this order is
        // the same every time the group is opened, which replaying stored decisions relies on.
        val scores = preScores.ensure(g.mediaIds)
        val order = g.mediaIds.withIndex()
            .sortedWith(compareByDescending<IndexedValue<Long>> { scores.getValue(it.value) }.thenBy { it.index })
            .map { it.value }
        AppLog.d(TAG, "Group $groupId order: " + order.joinToString { "$it(${"%.2f".format(scores.getValue(it))})" })
        val history = duels.active(groupId)
        val (mainDuel, usedMain) = KingOfTheHill.replay(order, history.map { Decision(it.winner, it.loser) })
        main = mainDuel
        decisionIds += history.take(usedMain).map { it.id }
        var rest = history.drop(usedMain)
        if (mainDuel.isFinished && rest.isNotEmpty()) {
            val candidates = mainDuel.runnerUpCandidates()
            if (candidates.size >= 2) {
                val (ru, usedRu) = KingOfTheHill.replay(candidates, rest.map { Decision(it.winner, it.loser) })
                if (usedRu > 0) {
                    runnerUpDuel = ru
                    decisionIds += rest.take(usedRu).map { it.id }
                    rest = rest.drop(usedRu)
                }
            }
        }
        if (rest.isNotEmpty()) {
            AppLog.i(TAG, "Group $groupId: discarding ${rest.size} stored decisions that no longer fit")
            duels.markUndone(rest.map { it.id })
        }
        AppLog.i(TAG, "Group $groupId: ${order.size} photos, resumed with ${decisionIds.size} decisions")
        render()
    }

    private fun render() {
        val g = group ?: return
        stored?.let { s ->
            val winner = s.winnerMediaId!!
            val runnerUp = s.runnerUpMediaId?.takeIf { it in s.mediaIds }
            _state.value = DuelUiState.Result(
                winner = winner,
                runnerUp = runnerUp,
                rest = s.mediaIds.filter { it != winner && it != runnerUp },
                canPickRunnerUp = false,
                status = s.status,
                canUndo = false,
                groupSize = s.mediaIds.size,
            )
            return
        }
        val m = main ?: return
        val ru = runnerUpDuel
        val active = when {
            ru != null && !ru.isFinished -> ru to Round.RUNNER_UP
            !m.isFinished -> m to Round.MAIN
            else -> null
        }
        if (active != null) {
            val (duel, round) = active
            val matchup = duel.current!!
            val king = loader.peek(matchup.king)
            val challenger = loader.peek(matchup.challenger)
            val key = matchup.king to matchup.challenger
            _state.value = DuelUiState.Dueling(
                round = round,
                matchup = matchup,
                king = king,
                challenger = challenger,
                alignment = alignments[key],
                alignmentKnown = key in alignments,
                kingFace = largestFace[matchup.king],
                canUndo = duel.canUndo || round == Round.RUNNER_UP,
            )
            if (king == null || challenger == null || key !in alignments) loadImages(matchup, duel.nextChallenger)
            duel.nextChallenger?.let(loader::prefetch)
            return
        }
        val winner = m.winner!!
        val runnerUp = ru?.winner ?: runnerUpSingle
        _state.value = DuelUiState.Result(
            winner = winner,
            runnerUp = runnerUp,
            rest = g.mediaIds.filter { it != winner && it != runnerUp },
            canPickRunnerUp = runnerUp == null && g.mediaIds.size > 2,
            status = GroupStatus.UNRESOLVED,
            canUndo = true,
            groupSize = g.mediaIds.size,
        )
    }

    private fun loadImages(matchup: Matchup, next: Long?) {
        if (!loading.add(matchup)) return
        viewModelScope.launch {
            val alignment = try {
                loader.load(matchup.king)
                loader.load(matchup.challenger)
                aligner.align(matchup.king, matchup.challenger)
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to load photos for $matchup", e)
                _state.value = DuelUiState.Error("Couldn't open one of these photos. It may have been deleted.")
                return@launch
            } finally {
                loading.remove(matchup)
            }
            mutex.withLock {
                alignments[matchup.king to matchup.challenger] = alignment
                val s = _state.value
                if (s is DuelUiState.Dueling && s.matchup == matchup) render()
            }
            if (matchup.king !in largestFace) {
                val face = runCatching { faces.analyze(matchup.king).largestFace?.box }.getOrNull()
                mutex.withLock {
                    largestFace[matchup.king] = face
                    val s = _state.value
                    if (s is DuelUiState.Dueling && s.matchup.king == matchup.king) render()
                }
            }
            // Whoever wins this step faces [next]; register both possibilities ahead of time.
            if (next != null) {
                for (king in listOf(matchup.king, matchup.challenger)) {
                    val a = aligner.align(king, next)
                    mutex.withLock { alignments[king to next] = a }
                }
            }
        }
    }

    fun pick(keepChallenger: Boolean, flipCount: Int) = act {
        val s = _state.value as? DuelUiState.Dueling ?: return@act
        if (!s.ready) return@act
        val m = s.matchup
        val (winner, loser) = if (keepChallenger) m.challenger to m.king else m.king to m.challenger
        when (s.round) {
            Round.MAIN -> main = main!!.pick(keepChallenger)
            Round.RUNNER_UP -> runnerUpDuel = runnerUpDuel!!.pick(keepChallenger)
        }
        render()
        decisionIds += duels.record(groupId, winner, loser, flipCount)
        AppLog.d(TAG, "Group $groupId ${s.round} step ${m.step + 1}/${m.totalSteps}: $winner beat $loser ($flipCount flips)")
    }

    fun undo() = act {
        val ru = runnerUpDuel
        when {
            stored != null -> return@act
            ru != null && ru.canUndo -> {
                runnerUpDuel = ru.undo()
                duels.markUndone(decisionIds.removeAt(decisionIds.lastIndex))
            }
            ru != null -> runnerUpDuel = null
            runnerUpSingle != null -> runnerUpSingle = null
            main?.canUndo == true -> {
                main = main!!.undo()
                duels.markUndone(decisionIds.removeAt(decisionIds.lastIndex))
            }
            else -> return@act
        }
        render()
    }

    fun pickRunnerUp() = act {
        val m = main ?: return@act
        if (!m.isFinished || runnerUpDuel != null || runnerUpSingle != null) return@act
        val candidates = m.runnerUpCandidates()
        if (candidates.size == 1) runnerUpSingle = candidates.single() else runnerUpDuel = KingOfTheHill.start(candidates)
        render()
    }

    fun markDone() = act {
        finish()
        _events.send(DuelEvent.Close)
    }

    /** Called after the system trash dialog was confirmed. */
    fun onTrashed(count: Int) = act {
        finish()
        _events.send(DuelEvent.Message("Moved $count ${if (count == 1) "photo" else "photos"} to trash"))
        _events.send(DuelEvent.Close)
    }

    fun onFavorited() = act {
        _events.send(DuelEvent.Message("Added to favorites"))
    }

    /** Reopens a finished group and starts its duel from scratch. */
    fun duelAgain() = act {
        duels.reset(groupId)
        groups.setResult(groupId, GroupStatus.UNRESOLVED, null, null)
        load()
    }

    private suspend fun finish() {
        val s = _state.value as? DuelUiState.Result ?: return
        if (s.status != GroupStatus.UNRESOLVED) return
        groups.setResult(groupId, GroupStatus.DONE, s.winner, s.runnerUp)
        AppLog.i(TAG, "Group $groupId done: winner ${s.winner}, runner-up ${s.runnerUp}")
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch { mutex.withLock { block() } }
    }

    private companion object {
        const val TAG = "Duel"
    }
}
