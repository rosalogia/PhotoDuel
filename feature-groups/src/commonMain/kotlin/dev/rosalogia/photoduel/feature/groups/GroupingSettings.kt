package dev.rosalogia.photoduel.feature.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.rosalogia.photoduel.core.data.settings.Settings
import dev.rosalogia.photoduel.core.data.settings.SettingsRepository
import dev.rosalogia.photoduel.core.ui.Ink
import dev.rosalogia.photoduel.core.ui.QuietButton
import dev.rosalogia.photoduel.core.ui.Readout
import dev.rosalogia.photoduel.core.ui.SectionLabel
import dev.rosalogia.photoduel.core.ui.graphViewModel
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The grouping knobs, shared by the phone and desktop settings screens. */
class SettingsViewModel(
    private val repo: SettingsRepository,
) : ViewModel() {
    val settings: StateFlow<Settings?> = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setGapSeconds(seconds: Float) = viewModelScope.launch { repo.setSessionGapMs(roundGap(seconds) * 1000) }

    fun setSimilarity(value: Float) = viewModelScope.launch { repo.setSimilarityThreshold(value) }

    fun resetGrouping() = viewModelScope.launch {
        val d = Settings()
        repo.setSessionGapMs(d.sessionGapMs)
        repo.setSimilarityThreshold(d.similarityThreshold)
    }
}

private fun roundGap(seconds: Float): Long = (seconds / 5).roundToLong() * 5

/** Session gap and similarity sliders, plus reset. Sits inside a settings screen's scrolling column. */
@Composable
fun GroupingSettings(viewModel: SettingsViewModel = graphViewModel { SettingsViewModel(it.data.settings) }) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val sliderColors = SliderDefaults.colors(
        thumbColor = Ink.Kept,
        activeTrackColor = Ink.Kept,
        inactiveTrackColor = Ink.Raised2,
        activeTickColor = Color.Transparent,
        inactiveTickColor = Color.Transparent,
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel("Stacking")
        Spacer(Modifier.height(8.dp))

        // Sliders keep a local value while dragging and save on release, so restacking runs once.
        var gap by remember { mutableFloatStateOf(s.sessionGapMs / 1000f) }
        LaunchedEffect(s.sessionGapMs) { gap = s.sessionGapMs / 1000f }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Time between shots", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Readout("${roundGap(gap)} s", color = Ink.Kept)
        }
        Text("Shots taken further apart than this never stack together.", style = MaterialTheme.typography.bodySmall, color = Ink.Dim)
        Slider(
            value = gap,
            onValueChange = { gap = it },
            onValueChangeFinished = { viewModel.setGapSeconds(gap) },
            valueRange = 10f..300f,
            colors = sliderColors,
            modifier = Modifier.testTag("gapSlider"),
        )
        Spacer(Modifier.height(10.dp))

        var similarity by remember { mutableFloatStateOf(s.similarityThreshold) }
        LaunchedEffect(s.similarityThreshold) { similarity = s.similarityThreshold }
        Text("How alike photos must be", style = MaterialTheme.typography.bodyLarge)
        Text(
            "Looser makes bigger stacks. Stricter keeps different scenes apart.",
            style = MaterialTheme.typography.bodySmall,
            color = Ink.Dim,
        )
        Slider(
            value = similarity,
            onValueChange = { similarity = it },
            onValueChangeFinished = { viewModel.setSimilarity(similarity) },
            valueRange = 0.5f..0.95f,
            steps = 8,
            colors = sliderColors,
            modifier = Modifier.testTag("similaritySlider"),
        )
        Row {
            Readout("Looser", small = true, modifier = Modifier.weight(1f))
            Readout("Stricter", small = true)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Your own edits (photos taken out, merges and splits) stay when these change.",
            style = MaterialTheme.typography.bodySmall,
            color = Ink.Dim,
        )
        QuietButton("Reset to defaults", viewModel::resetGrouping, color = Ink.Fg, modifier = Modifier.offset(x = (-12).dp))
    }
}
