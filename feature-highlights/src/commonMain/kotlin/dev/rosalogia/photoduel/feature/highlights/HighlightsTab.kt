package dev.rosalogia.photoduel.feature.highlights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.rosalogia.photoduel.core.data.db.HighlightPhase
import dev.rosalogia.photoduel.core.data.db.HighlightSetSummary
import dev.rosalogia.photoduel.core.data.highlights.SeriesKind
import dev.rosalogia.photoduel.core.data.highlights.SeriesSuggestion
import dev.rosalogia.photoduel.core.ui.PhotoRef
import dev.rosalogia.photoduel.core.ui.graphViewModel
import kotlin.math.roundToInt
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** The Highlights tab: sets in progress or done, suggested days and trips, and a date picker. */
@Composable
fun HighlightsTab(onOpenSet: (Long) -> Unit, viewModel: HighlightsHomeViewModel = graphViewModel { HighlightsHomeViewModel(it) }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val open by rememberUpdatedState(onOpenSet)
    LaunchedEffect(viewModel) { viewModel.created.collect { open(it) } }
    var pickingDates by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<HighlightSetSummary?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize().testTag("highlightsTab"),
    ) {
        item {
            Text(
                "Pick the best shots of a day or a trip. Bursts count once, and nothing gets deleted.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = { pickingDates = true }, modifier = Modifier.fillMaxWidth().testTag("chooseDates")) {
                Icon(Icons.Filled.DateRange, contentDescription = null)
                Text("  Choose dates")
            }
        }
        if (state.sets.isNotEmpty()) {
            item { SectionTitle("Your highlights") }
            items(state.sets, key = { "set-${it.set.setId}" }) { s ->
                SetCard(s, onClick = { onOpenSet(s.set.setId) }, onDelete = { deleting = s })
            }
        }
        item { SectionTitle("Suggestions") }
        if (state.loaded && state.suggestions.isEmpty()) {
            item {
                Text(
                    "No busy days found yet. Choose dates to start one yourself.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.suggestions, key = { "sug-${it.kind}-${it.start}-${it.end}" }) { s ->
            SuggestionCard(s, onClick = { viewModel.openDraft(s.start, s.end) })
        }
    }

    if (pickingDates) {
        DateRangeDialog(
            onDismiss = { pickingDates = false },
            onPicked = { start, end ->
                pickingDates = false
                viewModel.openDraft(start, end)
            },
        )
    }
    state.draft?.let { d ->
        DraftDialog(d, onName = viewModel::setName, onTarget = viewModel::setTarget, onDismiss = viewModel::dismissDraft, onCreate = viewModel::create)
    }
    deleting?.let { s ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${s.set.name}\"?") },
            text = { Text("Your picks for this set are forgotten. Photos and album copies stay where they are.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(s.set.setId)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
private fun SetCard(s: HighlightSetSummary, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("highlightSet")) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.set.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    when (s.set.phase) {
                        HighlightPhase.DONE -> "${s.selectedCount} highlights from ${s.itemCount} photos"
                        HighlightPhase.TRIM -> "Narrowing ${s.itemCount} photos down to ${s.set.targetCount}"
                        HighlightPhase.CULL -> "In progress · ${s.itemCount} photos, aiming for ${s.set.targetCount}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete ${s.set.name}") }
        }
    }
}

@Composable
private fun SuggestionCard(s: SeriesSuggestion, onClick: () -> Unit) {
    val days = ChronoUnit.DAYS.between(s.start, s.end) + 1
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("suggestion"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(formatDays(s.start, s.end), style = MaterialTheme.typography.titleMedium)
                Text(
                    (if (s.kind == SeriesKind.TRIP) "Trip · $days days · " else "") + "${s.photoCount} photos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // A glimpse of the day, so it's recognizable without remembering the date.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                s.preview.forEach { id ->
                    AsyncImage(
                        model = PhotoRef(id),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeDialog(onDismiss: () -> Unit, onPicked: (java.time.LocalDate, java.time.LocalDate) -> Unit) {
    val picker = rememberDateRangePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis()
        },
    )
    // The picker works in UTC midnights.
    fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            val start = picker.selectedStartDateMillis
            TextButton(
                onClick = { if (start != null) onPicked(day(start), day(picker.selectedEndDateMillis ?: start)) },
                enabled = start != null,
                modifier = Modifier.testTag("confirmDates"),
            ) { Text("Next") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DateRangePicker(state = picker, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DraftDialog(
    d: HighlightDraft,
    onName: (String) -> Unit,
    onTarget: (Int) -> Unit,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
) {
    val n = d.candidates
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New highlights") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = d.name,
                    onValueChange = onName,
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("draftName"),
                )
                when {
                    n == null -> Text("Counting photos…")
                    n == 0 -> Text("No camera photos on these days.")
                    else -> {
                        val bursts = (d.rawPhotos ?: n) - n
                        Text(
                            "$n photos" + if (bursts > 0) " (${d.rawPhotos} shots, with each burst counted once)" else "",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text("Pick your top ${d.target}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("draftTarget"))
                        val max = minOf(n, 100)
                        if (max > 1) {
                            Slider(
                                value = d.target.toFloat(),
                                onValueChange = { onTarget(it.roundToInt()) },
                                valueRange = 1f..max.toFloat(),
                                modifier = Modifier.testTag("targetSlider"),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCreate, enabled = (n ?: 0) > 0, modifier = Modifier.testTag("createSet")) { Text("Start") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
