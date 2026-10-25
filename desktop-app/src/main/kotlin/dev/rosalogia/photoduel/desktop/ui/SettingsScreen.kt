package dev.rosalogia.photoduel.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rosalogia.photoduel.desktop.AppDirs
import dev.rosalogia.photoduel.desktop.DesktopApp
import dev.rosalogia.photoduel.desktop.VERSION
import dev.rosalogia.photoduel.desktop.platform.SystemFiles
import dev.rosalogia.photoduel.feature.groups.GroupingSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SettingsScreen(app: DesktopApp) {
    val folders by app.folders.folders.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Column(Modifier.widthIn(max = 640.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(24.dp))
            Text("Photo folders", style = MaterialTheme.typography.titleMedium)
            Text(
                "Camera photos (JPEG and HEIC) in these folders and their subfolders. Screenshots folders and " +
                    "the Highlights albums are skipped.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            folders.forEach { folder ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(folder.path, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { app.folders.remove(folder) }, enabled = folders.size > 1) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove ${folder.path}")
                    }
                }
            }
            OutlinedButton(onClick = {
                scope.launch {
                    val picked = withContext(Dispatchers.IO) { FolderPicker.pick(folders.firstOrNull()) } ?: return@launch
                    app.folders.add(picked)
                }
            }) { Text("Add a folder…") }
            Spacer(Modifier.height(8.dp))
            Text(
                "Highlight albums are copied to ${app.highlightsRoot.path}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            GroupingSettings()

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { SystemFiles.open(File(AppDirs.data, "logs")) }) { Text("Open logs folder") }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Photo Duel $VERSION · everything stays on this computer",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
