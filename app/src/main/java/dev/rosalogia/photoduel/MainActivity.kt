package dev.rosalogia.photoduel

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import dev.rosalogia.photoduel.core.ui.LocalAppGraph
import dev.rosalogia.photoduel.core.ui.LocalDarkColors
import dev.rosalogia.photoduel.core.ui.LocalMessages
import dev.rosalogia.photoduel.core.ui.LocalPhotoActions
import dev.rosalogia.photoduel.platform.rememberAndroidPhotoActions
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.rosalogia.photoduel.core.common.AppLog
import dev.rosalogia.photoduel.core.data.media.MediaAccess
import dev.rosalogia.photoduel.debug.CrashReportPrompt
import dev.rosalogia.photoduel.permissions.PermissionScreen
import dev.rosalogia.photoduel.permissions.rememberMediaPermissionLauncher
import dev.rosalogia.photoduel.core.ui.BrandColors
import dev.rosalogia.photoduel.theme.PhotoDuelTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as PhotoDuelApp
        val media = app.graph.data.photos
        val indexing = app.graph.data.coordinator
        setContent {
            val context = LocalContext.current
            val dark = if (Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(context) else BrandColors.dark
            CompositionLocalProvider(
                LocalAppGraph provides app.graph,
                LocalPhotoActions provides rememberAndroidPhotoActions(),
                LocalMessages provides { text: String -> Toast.makeText(context, text, Toast.LENGTH_SHORT).show() },
                LocalDarkColors provides dark,
            ) {
            PhotoDuelTheme {
                // Lets UI automation find composables by testTag.
                Surface(modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }, color = MaterialTheme.colorScheme.background) {
                    var access by remember { mutableStateOf(media.currentAccess()) }
                    fun refreshAccess() {
                        val now = media.currentAccess()
                        if (now != access) {
                            AppLog.i("Permissions", "Media access $access -> $now")
                            access = now
                            if (now != MediaAccess.NONE) indexing.requestSync(app.appScope)
                        }
                    }
                    // The user can change access from system settings while we're in the background.
                    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshAccess() }
                    val requestAccess = rememberMediaPermissionLauncher(onResult = ::refreshAccess)

                    if (access == MediaAccess.NONE) {
                        PermissionScreen(onRequest = requestAccess)
                    } else {
                        MainNavigation(partialAccess = access == MediaAccess.PARTIAL, onRequestMoreAccess = requestAccess)
                    }
                    if (BuildConfig.SHOW_DEBUG_TOOLS) CrashReportPrompt()
                }
            }
            }
        }
    }
}
