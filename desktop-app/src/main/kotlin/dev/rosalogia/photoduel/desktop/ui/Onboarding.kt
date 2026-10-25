package dev.rosalogia.photoduel.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.rosalogia.photoduel.desktop.DesktopApp
import dev.rosalogia.photoduel.desktop.AppDirs
import dev.rosalogia.photoduel.desktop.platform.FolderPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** First run: choose which folders hold camera photos. */
@Composable
fun Onboarding(app: DesktopApp) {
    val scope = rememberCoroutineScope()
    val suggestions = remember { FolderPrefs.suggestions(AppDirs.pictures) }
    val chosen = remember { mutableStateListOf<File>().apply { suggestions.firstOrNull()?.let(::add) } }
    val all = remember { mutableStateListOf<File>().apply { addAll(suggestions) } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 560.dp).padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Photo Duel", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Find groups of similar shots and keep the best one. Which folders hold your camera photos? " +
                    "Subfolders are included. Photos never leave this computer.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(4.dp))
            all.forEach { folder ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = folder in chosen, onCheckedChange = { if (it) chosen += folder else chosen -= folder })
                    FolderText(folder, MaterialTheme.typography.bodyMedium)
                }
            }
            OutlinedButton(onClick = {
                scope.launch {
                    val picked = withContext(Dispatchers.IO) { FolderPicker.pick(AppDirs.pictures) } ?: return@launch
                    if (picked !in all) all += picked
                    if (picked !in chosen) chosen += picked
                }
            }) { Text("Add a folder…") }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { chosen.forEach(app.folders::add) },
                enabled = chosen.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Continue") }
        }
    }
}
