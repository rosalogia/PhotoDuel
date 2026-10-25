package dev.rosalogia.photoduel.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.rosalogia.photoduel.BuildConfig
import dev.rosalogia.photoduel.core.data.settings.Settings
import dev.rosalogia.photoduel.core.data.settings.SettingsRepository
import dev.rosalogia.photoduel.core.ui.graphViewModel
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenLogs: () -> Unit, viewModel: SettingsViewModel = graphViewModel { SettingsViewModel(it.data.settings) }) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        val s = settings ?: return@Scaffold
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Grouping", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            // Sliders keep a local value while dragging and save on release, so regrouping runs once.
            var gap by remember { mutableFloatStateOf(s.sessionGapMs / 1000f) }
            LaunchedEffect(s.sessionGapMs) { gap = s.sessionGapMs / 1000f }
            Text("Start a new group after a gap of ${roundGap(gap)} s", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Shots further apart than this are never grouped.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = gap,
                onValueChange = { gap = it },
                onValueChangeFinished = { viewModel.setGapSeconds(gap) },
                valueRange = 10f..300f,
                modifier = Modifier.testTag("gapSlider"),
            )
            Spacer(Modifier.height(16.dp))

            var similarity by remember { mutableFloatStateOf(s.similarityThreshold) }
            LaunchedEffect(s.similarityThreshold) { similarity = s.similarityThreshold }
            Text("Similarity needed: ${"%.2f".format(similarity)}", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Lower groups more loosely; raise it if different scenes end up together.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = similarity,
                onValueChange = { similarity = it },
                onValueChangeFinished = { viewModel.setSimilarity(similarity) },
                valueRange = 0.5f..0.95f,
                steps = 8,
                modifier = Modifier.testTag("similaritySlider"),
            )
            TextButton(onClick = viewModel::resetGrouping) { Text("Reset to defaults") }
            Text(
                "Manual edits (removed photos, merges and splits) are kept when grouping changes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (BuildConfig.SHOW_DEBUG_TOOLS) {
                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text("Testing", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenLogs, modifier = Modifier.testTag("openLogs")) { Text("View and share logs") }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Photo Duel ${BuildConfig.VERSION_NAME} · everything stays on this phone",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
