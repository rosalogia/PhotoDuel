package dev.rosalogia.photoduel.permissions

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.rosalogia.photoduel.core.ui.APP_NAME
import dev.rosalogia.photoduel.core.ui.BrandIcons
import dev.rosalogia.photoduel.core.ui.Ink
import dev.rosalogia.photoduel.core.ui.Moment
import dev.rosalogia.photoduel.core.ui.PrimaryButton
import dev.rosalogia.photoduel.core.ui.QuietButton

/** The permissions to ask for on this Android version. Asking for both on 14+ lets the user pick partial access. */
val mediaPermissions: Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

@Composable
fun rememberMediaPermissionLauncher(onResult: () -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onResult() }
    return { launcher.launch(mediaPermissions) }
}

@Composable
fun PermissionScreen(onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.CenterStart) {
        Moment(
            title = "Keep the best one.",
            body = "$APP_NAME finds groups of near-identical photos in your camera roll. Compare them two at a time and keep the best.",
            mark = true,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                Promise("You choose. Nothing is picked for you.")
                Promise("Everything stays on this phone.")
                Promise("Nothing is deleted without asking.")
            }
            PrimaryButton("Allow photo access", onRequest, Modifier.testTag("grantAccess"))
        }
    }
}

@Composable
private fun Promise(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(BrandIcons.Check, contentDescription = null, tint = Ink.Kept, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Shown above the stacks when the user only granted access to selected photos (Android 14+). */
@Composable
fun PartialAccessBanner(onGrantMore: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Ink.Surface).padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$APP_NAME can only see the photos you selected.",
            style = MaterialTheme.typography.bodySmall,
            color = Ink.Dim,
            modifier = Modifier.weight(1f),
        )
        QuietButton("Allow all", onGrantMore, color = Ink.Kept)
    }
}
