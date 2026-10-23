package dev.rosalogia.photoduel.feature.duel

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.rosalogia.photoduel.core.common.AppLog
import dev.rosalogia.photoduel.core.data.db.GroupStatus
import dev.rosalogia.photoduel.core.data.media.LibraryActions
import dev.rosalogia.photoduel.core.data.media.mediaContentUri

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LibraryActionsEntryPoint {
    fun libraryActions(): LibraryActions
}

@Composable
fun ResultScreen(
    state: DuelUiState.Result,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onPickRunnerUp: () -> Unit,
    onDone: () -> Unit,
    onTrashed: (Int) -> Unit,
    onFavorited: () -> Unit,
    onDuelAgain: () -> Unit,
) {
    val context = LocalContext.current
    val actions = EntryPointAccessors.fromApplication(context, LibraryActionsEntryPoint::class.java).libraryActions()
    val trashCount = state.rest.size
    val trashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        AppLog.i("Result", "Trash request result ${it.resultCode}")
        if (it.resultCode == Activity.RESULT_OK) onTrashed(trashCount)
    }
    val favoriteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        AppLog.i("Result", "Favorite request result ${it.resultCode}")
        if (it.resultCode == Activity.RESULT_OK) onFavorited()
    }
    val finished = state.status != GroupStatus.UNRESOLVED

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(
                if (finished) "Your pick" else "Winner",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).testTag("resultTitle"),
            )
            if (state.canUndo) TextButton(onClick = onUndo, modifier = Modifier.testTag("undo")) { Text("Undo") }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)) {
            AsyncImage(
                model = mediaContentUri(state.winner),
                contentDescription = "Winner",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().testTag("winnerImage"),
            )
            state.runnerUp?.let { id ->
                Column(Modifier.align(Alignment.BottomEnd).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = mediaContentUri(id),
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
                    onClick = { context.startActivity(actions.shareIntent(state.winner)) },
                    modifier = Modifier.weight(1f).testTag("share"),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Share")
                }
                OutlinedButton(
                    onClick = {
                        favoriteLauncher.launch(IntentSenderRequest.Builder(actions.favoriteRequest(listOf(state.winner))).build())
                    },
                    modifier = Modifier.weight(1f).testTag("favorite"),
                ) {
                    Icon(Icons.Filled.Favorite, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Favorite")
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
                        trashLauncher.launch(IntentSenderRequest.Builder(actions.trashRequest(state.rest)).build())
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
