package dev.rosalogia.photoduel.feature.highlights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rosalogia.photoduel.core.data.db.HighlightSetSummary
import dev.rosalogia.photoduel.core.data.highlights.HighlightRepository
import dev.rosalogia.photoduel.core.data.highlights.SeriesSuggester
import dev.rosalogia.photoduel.core.data.highlights.SeriesSuggestion
import dev.rosalogia.photoduel.core.ui.AppGraph
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A set about to be created; [candidates] is null while it's being counted. */
data class HighlightDraft(
    val start: LocalDate,
    val end: LocalDate,
    val name: String,
    val candidates: Int? = null,
    val rawPhotos: Int? = null,
    val target: Int = 1,
)

data class HighlightsHomeState(
    val sets: List<HighlightSetSummary> = emptyList(),
    val suggestions: List<SeriesSuggestion> = emptyList(),
    val draft: HighlightDraft? = null,
    val loaded: Boolean = false,
)

class HighlightsHomeViewModel(
    private val repo: HighlightRepository,
) : ViewModel() {
    constructor(graph: AppGraph) : this(graph.data.highlights)
    private val draft = MutableStateFlow<HighlightDraft?>(null)
    private var counting: Job? = null

    private val _created = Channel<Long>(Channel.BUFFERED)
    /** IDs of newly created sets, to open. */
    val created: Flow<Long> = _created.receiveAsFlow()

    val state: StateFlow<HighlightsHomeState> = combine(repo.observeSets(), repo.observeSuggestions(), draft) { sets, suggestions, d ->
        // A suggestion already made into a set isn't worth offering again.
        val zone = ZoneId.systemDefault()
        val made = sets.mapTo(HashSet()) { it.set.startTime to it.set.endTime }
        val fresh = suggestions.filter { SeriesSuggester.range(it.start, it.end, zone) !in made }
        HighlightsHomeState(sets, fresh, d, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HighlightsHomeState())

    fun openDraft(start: LocalDate, end: LocalDate) {
        draft.value = HighlightDraft(start, end, defaultName(start, end))
        counting?.cancel()
        counting = viewModelScope.launch {
            val (from, to) = SeriesSuggester.range(start, end, ZoneId.systemDefault())
            val candidates = repo.candidates(from, to)
            val raw = candidates.sumOf { it.burstSize }
            draft.update { it?.copy(candidates = candidates.size, rawPhotos = raw, target = SeriesSuggester.defaultTarget(candidates.size)) }
        }
    }

    fun setName(name: String) = draft.update { it?.copy(name = name) }

    fun setTarget(target: Int) = draft.update { it?.copy(target = target) }

    fun dismissDraft() {
        counting?.cancel()
        draft.value = null
    }

    fun create() {
        val d = draft.value ?: return
        val n = d.candidates ?: return
        if (n == 0) return
        draft.value = null
        viewModelScope.launch {
            val (from, to) = SeriesSuggester.range(d.start, d.end, ZoneId.systemDefault())
            val id = repo.create(d.name.trim().ifEmpty { defaultName(d.start, d.end) }, from, to, d.target.coerceIn(1, n))
            _created.send(id)
        }
    }

    fun delete(setId: Long) {
        viewModelScope.launch { repo.delete(setId) }
    }
}
