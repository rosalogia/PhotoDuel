package dev.rosalogia.photoduel.feature.highlights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rosalogia.photoduel.core.data.db.HighlightPhase
import dev.rosalogia.photoduel.core.data.db.HighlightSetSummary
import dev.rosalogia.photoduel.core.data.highlights.SeriesKind
import dev.rosalogia.photoduel.core.data.highlights.SeriesSuggestion
import dev.rosalogia.photoduel.core.ui.BrandCard
import dev.rosalogia.photoduel.core.ui.BrandIconButton
import dev.rosalogia.photoduel.core.ui.BrandIcons
import dev.rosalogia.photoduel.core.ui.Ink
import dev.rosalogia.photoduel.core.ui.PhotoThumb
import dev.rosalogia.photoduel.core.ui.Readout
import dev.rosalogia.photoduel.core.ui.SecondaryButton
import dev.rosalogia.photoduel.core.ui.SectionLabel
import dev.rosalogia.photoduel.core.ui.graphViewModel
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** The Highlights tab: sets in progress or done, suggested days and trips, and a date picker. */
@Composable
fun HighlightsTab(onOpenSet: (Long) -> Unit, viewModel: HighlightsHomeViewModel = graphViewModel { HighlightsHomeViewModel(it) }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val open by rememberUpdatedState(onOpenSet)
    LaunchedEffect(viewModel) { viewModel.created.collect { open(it) } }
    var pickingDates by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<HighlightSetSummary?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.widthIn(max = 760.dp).fillMaxSize().testTag("highlightsTab"),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "The best shots of a day or a trip. Bursts count once, and nothing is deleted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.Dim,
                )
                SecondaryButton(
                    "Choose dates",
                    onClick = { pickingDates = true },
                    icon = BrandIcons.Calendar,
                    modifier = Modifier.fillMaxWidth().testTag("chooseDates"),
                )
            }
        }
        if (state.sets.isNotEmpty()) {
            item { SectionLabel("Your highlights", Modifier.padding(top = 20.dp, bottom = 4.dp)) }
            items(state.sets, key = { "set-${it.set.setId}" }) { s ->
                SetCard(s, onClick = { onOpenSet(s.set.setId) }, onDelete = { deleting = s })
            }
        }
        item { SectionLabel("Suggestions", Modifier.padding(top = 20.dp, bottom = 4.dp)) }
        if (state.loaded && state.suggestions.isEmpty()) {
            item {
                Text(
                    "No busy days found yet. Choose dates to start one yourself.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.Dim,
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
            containerColor = Ink.Raised,
            title = { Text("Delete “${s.set.name}”?") },
            text = { Text("Your picks for this set are forgotten. Photos and album copies stay where they are.", color = Ink.Dim) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(s.set.setId)
                        deleting = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Ink.Trash),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }, colors = ButtonDefaults.textButtonColors(contentColor = Ink.Fg)) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SetCard(s: HighlightSetSummary, onClick: () -> Unit, onDelete: () -> Unit) {
    BrandCard(Modifier.fillMaxWidth().testTag("highlightSet"), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(s.set.name, style = MaterialTheme.typography.titleMedium)
                Readout(
                    when (s.set.phase) {
                        HighlightPhase.DONE -> "${s.selectedCount} highlights · from ${s.itemCount} photos"
                        HighlightPhase.TRIM -> "Narrowing ${s.itemCount} down to ${s.set.targetCount}"
                        HighlightPhase.CULL -> "In progress · ${s.itemCount} photos · aiming for ${s.set.targetCount}"
                    },
                    small = true,
                    color = if (s.set.phase == HighlightPhase.DONE) Ink.Kept else Ink.Dim,
                )
            }
            BrandIconButton(BrandIcons.Trash, "Delete ${s.set.name}", onDelete, tint = Ink.Dim)
        }
    }
}

@Composable
private fun SuggestionCard(s: SeriesSuggestion, onClick: () -> Unit) {
    val days = ChronoUnit.DAYS.between(s.start, s.end) + 1
    BrandCard(Modifier.fillMaxWidth().testTag("suggestion"), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(formatDays(s.start, s.end), style = MaterialTheme.typography.titleMedium)
                Readout((if (s.kind == SeriesKind.TRIP) "Trip · $days days · " else "") + "${s.photoCount} photos", small = true)
            }
            // A glimpse of the day, so it's recognizable without remembering the date.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                s.preview.forEach { id -> PhotoThumb(id, Modifier.size(44.dp), corner = 6.dp) }
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
    val colors = DatePickerDefaults.colors(
        containerColor = Ink.Raised,
        dayInSelectionRangeContainerColor = Ink.Kept.copy(alpha = 0.18f),
        dayInSelectionRangeContentColor = Ink.Fg,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = colors,
        confirmButton = {
            val start = picker.selectedStartDateMillis
            TextButton(
                onClick = { if (start != null) onPicked(day(start), day(picker.selectedEndDateMillis ?: start)) },
                enabled = start != null,
                modifier = Modifier.testTag("confirmDates"),
            ) { Text("Next") }
        },
        dismissButton = { TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = Ink.Fg)) { Text("Cancel") } },
    ) {
        DateRangePicker(state = picker, colors = colors, modifier = Modifier.weight(1f))
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
        containerColor = Ink.Raised,
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
                    n == null -> Readout("Counting photos…")
                    n == 0 -> Text("No camera photos on these days.", color = Ink.Dim)
                    else -> {
                        val bursts = (d.rawPhotos ?: n) - n
                        Readout("$n photos" + if (bursts > 0) " · ${d.rawPhotos} shots, bursts counted once" else "", maxLines = 2)
                        Text("Your top ${d.target}", style = MaterialTheme.typography.headlineSmall, color = Ink.Fg, modifier = Modifier.testTag("draftTarget"))
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
        dismissButton = { TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = Ink.Fg)) { Text("Cancel") } },
    )
}
