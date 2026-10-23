package dev.rosalogia.photoduel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
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
import dagger.hilt.android.AndroidEntryPoint
import dev.rosalogia.photoduel.core.common.AppLog
import dev.rosalogia.photoduel.core.data.IndexingCoordinator
import dev.rosalogia.photoduel.core.data.media.MediaAccess
import dev.rosalogia.photoduel.core.data.media.MediaStoreRepository
import dev.rosalogia.photoduel.debug.CrashReportPrompt
import dev.rosalogia.photoduel.permissions.PermissionScreen
import dev.rosalogia.photoduel.permissions.rememberMediaPermissionLauncher
import dev.rosalogia.photoduel.theme.PhotoDuelTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var media: MediaStoreRepository
    @Inject lateinit var indexing: IndexingCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhotoDuelTheme {
                // Lets UI automation find composables by testTag.
                Surface(modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }, color = MaterialTheme.colorScheme.background) {
                    var access by remember { mutableStateOf(media.currentAccess()) }
                    fun refreshAccess() {
                        val now = media.currentAccess()
                        if (now != access) {
                            AppLog.i("Permissions", "Media access $access -> $now")
                            access = now
                            if (now != MediaAccess.NONE) indexing.requestSync((application as PhotoDuelApp).appScope)
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
