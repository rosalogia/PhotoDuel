package dev.rosalogia.photoduel.feature.duel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.rosalogia.photoduel.core.common.AppLog
import dev.rosalogia.photoduel.core.data.db.GroupStatus
import dev.rosalogia.photoduel.core.ui.BrandIconButton
import dev.rosalogia.photoduel.core.ui.BrandIcons
import dev.rosalogia.photoduel.core.ui.BrandTopBar
import dev.rosalogia.photoduel.core.ui.Ink
import dev.rosalogia.photoduel.core.ui.KeyShortcuts
import dev.rosalogia.photoduel.core.ui.LocalPhotoActions
import dev.rosalogia.photoduel.core.ui.PhotoRef
import dev.rosalogia.photoduel.core.ui.PhotoThumb
import dev.rosalogia.photoduel.core.ui.QuietButton
import dev.rosalogia.photoduel.core.ui.Readout
import dev.rosalogia.photoduel.core.ui.Ring
import dev.rosalogia.photoduel.core.ui.SecondaryButton
import dev.rosalogia.photoduel.core.ui.TrashButton
import dev.rosalogia.photoduel.core.ui.hasShortcuts
import dev.rosalogia.photoduel.core.ui.rememberHaptics
import kotlinx.coroutines.launch

@Composable
fun ResultScreen(
    state: DuelUiState.Result,
    navigation: DuelNavigation?,
    subtitle: String?,
    onUndo: () -> Unit,
    onPickRunnerUp: () -> Unit,
    onDone: () -> Unit,
    onTrashed: (Int) -> Unit,
    onFavorited: () -> Unit,
    onDuelAgain: () -> Unit,
) {
    val actions = LocalPhotoActions.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val trashCount = state.rest.size
    val finished = state.status != GroupStatus.UNRESOLVED
    fun trashRest() {
        scope.launch {
            val ok = actions.trash(state.rest)
            AppLog.i("Result", "Trash request ${if (ok) "confirmed" else "cancelled"}")
            if (ok) {
                haptics.reject()
                onTrashed(trashCount)
            }
        }
    }
    KeyShortcuts { e ->
        if (e.type != KeyEventType.KeyDown) return@KeyShortcuts false
        when {
            (e.key == Key.Z && (e.isCtrlPressed || e.isMetaPressed)) || e.key == Key.Backspace -> true.also { if (state.canUndo) onUndo() }
            e.key == Key.Delete && trashCount > 0 -> true.also { trashRest() }
            e.key == Key.Enter && !finished -> true.also { onDone() }
            else -> false
        }
    }
    val keys = hasShortcuts

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        BrandTopBar(
            title = null,
            onBack = navigation?.onClick,
            backIcon = navigation?.icon ?: BrandIcons.Back,
            backLabel = navigation?.label ?: "Back",
            backTag = "duelNav",
        ) {
            if (state.canUndo) BrandIconButton(BrandIcons.Undo, "Undo", onUndo, Modifier.testTag("undo"))
        }

        AsyncImage(
            model = PhotoRef(state.winner),
            contentDescription = "Best shot",
            contentScale = ContentScale.Fit,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 6.dp).testTag("winnerImage"),
        )

        Column(
            Modifier.align(Alignment.CenterHorizontally).widthIn(max = 600.dp).fillMaxWidth()
                .padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Strip(state, onRingClosed = haptics::tick)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Best shot", style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("resultTitle"))
                val meta = listOfNotNull(subtitle, if (state.runnerUp != null) "Runner-up picked" else null).joinToString(" · ")
                if (meta.isNotEmpty()) Readout(meta, Modifier.testTag("duelSubtitle"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(
                    actions.share.label,
                    onClick = { actions.share(listOf(state.winner)) },
                    icon = actions.share.icon,
                    modifier = Modifier.weight(1f).testTag("share"),
                )
                if (actions.canFavorite) {
                    SecondaryButton(
                        "Favorite",
                        onClick = {
                            scope.launch {
                                val ok = actions.favorite(listOf(state.winner))
                                AppLog.i("Result", "Favorite request ${if (ok) "confirmed" else "cancelled"}")
                                if (ok) onFavorited()
                            }
                        },
                        icon = BrandIcons.Heart,
                        modifier = Modifier.weight(1f).testTag("favorite"),
                    )
                }
            }
            if (trashCount > 0) {
                TrashButton(
                    "Trash the other $trashCount",
                    onClick = ::trashRest,
                    note = actions.trashNote,
                    modifier = Modifier.fillMaxWidth().testTag("trashRest"),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                if (finished) {
                    if (state.groupSize >= 2) QuietButton("Duel again", onDuelAgain, Modifier.testTag("duelAgain"))
                } else {
                    QuietButton("Keep all", onDone, Modifier.testTag("done"))
                }
                if (state.canPickRunnerUp) QuietButton("Pick a runner-up", onPickRunnerUp, Modifier.testTag("pickRunnerUp"))
            }
            if (keys) {
                Readout(
                    listOfNotNull(if (trashCount > 0) "Del trash" else null, if (!finished) "Enter keep all" else null, "Z undo").joinToString(" · "),
                    Modifier.align(Alignment.CenterHorizontally),
                    small = true,
                    color = Ink.Faint,
                )
            }
        }
    }
}

/**
 * The whole stack in a row: the ring closes around the kept frame, a white ring marks a
 * runner-up, and the others are dimmed, so it's clear what "Trash the other N" removes.
 */
@Composable
private fun Strip(state: DuelUiState.Result, onRingClosed: () -> Unit) {
    val ids = listOfNotNull(state.winner, state.runnerUp) + state.rest
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        modifier = Modifier.testTag("others"),
    ) {
        items(ids, key = { it }) { id ->
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                val winner = id == state.winner
                val runnerUp = id == state.runnerUp
                PhotoThumb(id, Modifier.size(34.dp), corner = 4.dp, alpha = if (winner || runnerUp) 1f else 0.35f)
                when {
                    winner -> Ring(Modifier.size(52.dp), stroke = 2.dp, onClosed = onRingClosed)
                    runnerUp -> Ring(Modifier.size(52.dp), color = Ink.Fg.copy(alpha = 0.7f), stroke = 1.dp, animate = false)
                }
            }
        }
    }
}
