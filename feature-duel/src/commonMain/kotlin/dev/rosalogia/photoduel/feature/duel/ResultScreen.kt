package dev.rosalogia.photoduel.feature.duel

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.rosalogia.photoduel.core.common.AppLog
import dev.rosalogia.photoduel.core.data.db.GroupStatus
import dev.rosalogia.photoduel.core.ui.LocalPhotoActions
import dev.rosalogia.photoduel.core.ui.PhotoRef
import kotlinx.coroutines.launch

@Composable
fun ResultScreen(
    state: DuelUiState.Result,
    navigation: DuelNavigation,
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
    val trashCount = state.rest.size
    val finished = state.status != GroupStatus.UNRESOLVED

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        DuelTopBar(
            title = if (finished) "Your pick" else "Winner",
            subtitle = subtitle,
            navigation = navigation,
            titleTag = "resultTitle",
        ) {
            if (state.canUndo) TextButton(onClick = onUndo, modifier = Modifier.testTag("undo")) { Text("Undo") }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)) {
            AsyncImage(
                model = PhotoRef(state.winner),
                contentDescription = "Winner",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().testTag("winnerImage"),
            )
            state.runnerUp?.let { id ->
                Column(Modifier.align(Alignment.BottomEnd).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = PhotoRef(id),
                        contentDescription = "Runner-up",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(96.dp).clip(RoundedCornerShape(8.dp))
                            .border(2.dp, Color.White, RoundedCornerShape(8.dp)),
                    )
                    Text("Runner-up", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { actions.share(listOf(state.winner)) },
                    modifier = Modifier.weight(1f).testTag("share"),
                ) {
                    Icon(actions.share.icon, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(actions.share.label)
                }
                if (actions.canFavorite) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val ok = actions.favorite(listOf(state.winner))
                                AppLog.i("Result", "Favorite request ${if (ok) "confirmed" else "cancelled"}")
                                if (ok) onFavorited()
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("favorite"),
                    ) {
                        Icon(Icons.Filled.Favorite, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Favorite")
                    }
                }
            }
            if (state.canPickRunnerUp) {
                OutlinedButton(onClick = onPickRunnerUp, modifier = Modifier.fillMaxWidth().testTag("pickRunnerUp")) {
                    Text("Pick a runner-up too")
                }
            }
            if (trashCount > 0) {
                Button(
                    onClick = {
                        scope.launch {
                            val ok = actions.trash(state.rest)
                            AppLog.i("Result", "Trash request ${if (ok) "confirmed" else "cancelled"}")
                            if (ok) onTrashed(trashCount)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().testTag("trashRest"),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Trash the other $trashCount")
                }
            }
            if (finished) {
                if (state.groupSize >= 2) TextButton(onClick = onDuelAgain, modifier = Modifier.fillMaxWidth().testTag("duelAgain")) { Text("Duel again") }
            } else {
                TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth().testTag("done")) { Text("Keep all and mark done") }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
