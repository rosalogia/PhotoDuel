package dev.rosalogia.photoduel.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.rosalogia.photoduel.BuildConfig
import dev.rosalogia.photoduel.core.ui.APP_NAME
import dev.rosalogia.photoduel.core.ui.BrandTopBar
import dev.rosalogia.photoduel.core.ui.Ink
import dev.rosalogia.photoduel.core.ui.Readout
import dev.rosalogia.photoduel.core.ui.SecondaryButton
import dev.rosalogia.photoduel.core.ui.SectionLabel
import dev.rosalogia.photoduel.feature.groups.GroupingSettings

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenLogs: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        BrandTopBar(title = "Settings", onBack = onBack, large = true)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(36.dp),
        ) {
            GroupingSettings()

            if (BuildConfig.SHOW_DEBUG_TOOLS) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Troubleshooting")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "If something looks wrong, the logs help figure out why. They stay on this phone unless you share them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink.Dim,
                    )
                    SecondaryButton("View and share logs", onOpenLogs, Modifier.testTag("openLogs"))
                }
            }
            Readout("$APP_NAME ${BuildConfig.VERSION_NAME} · on this phone only", small = true, color = Ink.Faint)
        }
    }
}
