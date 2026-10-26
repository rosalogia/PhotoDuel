package dev.rosalogia.photoduel.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rosalogia.photoduel.core.ui.APP_NAME
import dev.rosalogia.photoduel.core.ui.BrandIconButton
import dev.rosalogia.photoduel.core.ui.BrandIcons
import dev.rosalogia.photoduel.core.ui.Ink
import dev.rosalogia.photoduel.core.ui.Readout
import dev.rosalogia.photoduel.core.ui.SecondaryButton
import dev.rosalogia.photoduel.core.ui.SectionLabel
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 32.dp)) {
        Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(40.dp)) {
            Text("Settings", style = MaterialTheme.typography.displaySmall)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("Photo folders")
                Text(
                    "Camera photos (JPEG and HEIC) in these folders and their subfolders. Screenshot folders and " +
                        "the Highlights albums are skipped.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink.Dim,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                folders.forEach { folder ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FolderText(folder, MaterialTheme.typography.bodyMedium, Modifier.weight(1f))
                        BrandIconButton(
                            BrandIcons.Close,
                            "Remove ${folder.path}",
                            onClick = { app.folders.remove(folder) },
                            enabled = folders.size > 1,
                            tint = Ink.Dim,
                        )
                    }
                }
                SecondaryButton("Add a folder…", icon = BrandIcons.Folder, onClick = {
                    scope.launch {
                        val picked = withContext(Dispatchers.IO) { FolderPicker.pick(folders.firstOrNull()) } ?: return@launch
                        app.folders.add(picked)
                    }
                })
                // Not a readout: upper case would misstate a case-sensitive path.
                Text(
                    "Highlight albums go to ${displayPath(app.highlightsRoot)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink.Dim,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            GroupingSettings()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Troubleshooting")
                Text(
                    "If something looks wrong, the logs help figure out why. They stay on this computer unless you share them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink.Dim,
                    modifier = Modifier.padding(top = 4.dp),
                )
                SecondaryButton("Open logs folder", icon = BrandIcons.Open, onClick = { SystemFiles.open(File(AppDirs.data, "logs")) })
            }
            Readout("$APP_NAME $VERSION · on this computer only", small = true, color = Ink.Faint)
        }
    }
}
