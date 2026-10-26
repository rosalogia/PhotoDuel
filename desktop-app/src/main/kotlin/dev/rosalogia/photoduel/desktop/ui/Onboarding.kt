package dev.rosalogia.photoduel.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.rosalogia.photoduel.core.ui.APP_NAME
import dev.rosalogia.photoduel.core.ui.BrandIcons
import dev.rosalogia.photoduel.core.ui.Ink
import dev.rosalogia.photoduel.core.ui.Moment
import dev.rosalogia.photoduel.core.ui.PrimaryButton
import dev.rosalogia.photoduel.core.ui.SecondaryButton
import dev.rosalogia.photoduel.core.ui.SectionLabel
import dev.rosalogia.photoduel.desktop.AppDirs
import dev.rosalogia.photoduel.desktop.DesktopApp
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
        Moment(
            title = "Keep the best one.",
            body = "$APP_NAME finds groups of near-identical photos and lets you compare them two at a time. " +
                "Your photos never leave this computer, and nothing is deleted without asking.",
            mark = true,
        ) {
            SectionLabel("Where are your camera photos?", Modifier.padding(top = 8.dp))
            Column {
                all.forEach { folder ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = folder in chosen,
                            onCheckedChange = { if (it) chosen += folder else chosen -= folder },
                            colors = CheckboxDefaults.colors(checkedColor = Ink.Kept, checkmarkColor = Ink.OnKept, uncheckedColor = Ink.Faint),
                        )
                        FolderText(folder, MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Continue", onClick = { chosen.forEach(app.folders::add) }, enabled = chosen.isNotEmpty())
                SecondaryButton("Add a folder…", icon = BrandIcons.Folder, onClick = {
                    scope.launch {
                        val picked = withContext(Dispatchers.IO) { FolderPicker.pick(AppDirs.pictures) } ?: return@launch
                        if (picked !in all) all += picked
                        if (picked !in chosen) chosen += picked
                    }
                })
            }
        }
    }
}
