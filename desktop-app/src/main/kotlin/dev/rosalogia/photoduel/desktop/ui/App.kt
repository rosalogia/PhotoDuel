package dev.rosalogia.photoduel.desktop.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import dev.rosalogia.photoduel.core.ui.KeyShortcuts
import dev.rosalogia.photoduel.core.ui.LocalAppGraph
import dev.rosalogia.photoduel.core.ui.LocalDarkColors
import dev.rosalogia.photoduel.core.ui.LocalMessages
import dev.rosalogia.photoduel.core.ui.LocalPhotoActions
import dev.rosalogia.photoduel.core.ui.LocalShortcuts
import dev.rosalogia.photoduel.core.ui.ShortcutDispatcher
import dev.rosalogia.photoduel.desktop.DesktopApp
import dev.rosalogia.photoduel.desktop.image.PhotoRefFetcher
import dev.rosalogia.photoduel.feature.duel.DuelNavigation
import dev.rosalogia.photoduel.feature.duel.DuelRoute
import dev.rosalogia.photoduel.feature.groups.GroupEditRoute
import dev.rosalogia.photoduel.feature.groups.GroupsRoute
import dev.rosalogia.photoduel.feature.highlights.HighlightSessionRoute
import dev.rosalogia.photoduel.feature.highlights.HighlightsTab
import kotlinx.coroutines.launch

private val Accent = Color(0xFF8AB4F8)

@Composable
fun App(app: DesktopApp, shortcuts: ShortcutDispatcher) {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context).components { add(PhotoRefFetcher.Factory(app.decoder)) }.build()
    }
    val dark = isSystemInDarkTheme()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val rootStores = remember { ScreenStores() }
    CompositionLocalProvider(
        LocalAppGraph provides app.graph,
        LocalShortcuts provides shortcuts,
        LocalPhotoActions provides app.actions,
        LocalMessages provides { text: String -> scope.launch { snackbar.showSnackbar(text) } },
        LocalDarkColors provides darkColorScheme(primary = Accent),
        LocalViewModelStoreOwner provides remember { rootStores.owner("root") },
    ) {
        MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Accent) else lightColorScheme()) {
            Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    val folders by app.folders.folders.collectAsStateWithLifecycle()
                    if (folders.isEmpty()) Onboarding(app) else Shell(app)
                }
            }
            app.actions.Dialogs()
        }
    }
}

@Composable
private fun Shell(app: DesktopApp) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Sort) }
    // The to-do group shown in Sort; also set when one is picked in the library.
    var sortSelection by remember { mutableStateOf<Long?>(null) }
    val stores = remember { ScreenStores() }
    val saveable = rememberSaveableStateHolder()
    LaunchedEffect(stack.toList()) {
        // Sidebar destinations keep their state; screens opened on top are dropped once closed.
        stores.retain(stack.toSet() + setOf(Screen.Sort, Screen.Library, Screen.Settings))
    }
    var showKeys by remember { mutableStateOf(false) }
    KeyShortcuts { e ->
        if (e.type != KeyEventType.KeyDown) return@KeyShortcuts false
        when {
            e.key == Key.Escape && stack.size > 1 -> true.also { stack.removeAt(stack.lastIndex) }
            e.key == Key.Slash && e.isShiftPressed || e.key == Key.F1 -> true.also { showKeys = true }
            else -> false
        }
    }
    if (showKeys) ShortcutsDialog(onDismiss = { showKeys = false })
    fun go(top: Screen) {
        stack.clear()
        stack.add(top)
    }
    fun pop(screen: Screen) {
        if (stack.size > 1 && stack.last() == screen) stack.removeAt(stack.lastIndex)
    }

    Row(Modifier.fillMaxSize()) {
        val root = stack.first()
        NavigationRail(Modifier.fillMaxHeight()) {
            RailItem("Sort", DesktopIcons.Compare, root == Screen.Sort) { go(Screen.Sort) }
            RailItem("Library", DesktopIcons.PhotoLibrary, root == Screen.Library) { go(Screen.Library) }
            RailItem("Settings", Icons.Filled.Settings, root == Screen.Settings) { go(Screen.Settings) }
            Spacer(Modifier.weight(1f))
            RailItem("Keys", DesktopIcons.Keyboard, false) { showKeys = true }
        }
        VerticalDivider()
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Progress(app)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val screen = stack.last()
                ScreenScope(screen, stores, saveable) {
                    when (screen) {
                        Screen.Sort -> SortScreen(selected = sortSelection, onSelect = { sortSelection = it }, onOpenLibrary = { go(Screen.Library) })
                        Screen.Library -> GroupsRoute(
                            onBack = null,
                            onOpenTodoGroup = {
                                sortSelection = it
                                go(Screen.Sort)
                            },
                            onOpenGroup = { stack.add(Screen.Duel(it)) },
                            onEditGroup = { stack.add(Screen.EditGroup(it)) },
                            highlights = { HighlightsTab(onOpenSet = { stack.add(Screen.Highlight(it)) }) },
                        )
                        Screen.Settings -> SettingsScreen(app)
                        is Screen.Duel -> DuelRoute(
                            groupId = screen.groupId,
                            onFinished = { pop(screen) },
                            navigation = DuelNavigation(onClick = { pop(screen) }),
                        )
                        is Screen.EditGroup -> GroupEditRoute(groupId = screen.groupId, onClose = { pop(screen) })
                        is Screen.Highlight -> HighlightSessionRoute(setId = screen.setId, onClose = { pop(screen) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RailItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

/** A thin bar while folders are scanned or new photos analyzed, with what's happening. */
@Composable
private fun Progress(app: DesktopApp) {
    val scan by app.photos.scan.collectAsStateWithLifecycle()
    val unindexed by remember { app.graph.data.groups.observeUnindexedCount() }.collectAsStateWithLifecycle(0)
    val text = when {
        scan.running && scan.toRead > 0 -> "Reading new photos: ${scan.read} of ${scan.toRead}"
        scan.running -> "Looking for photos… ${scan.found.takeIf { it > 0 } ?: ""}"
        unindexed > 0 -> "Analyzing photos to find similar shots: $unindexed left"
        else -> null
    } ?: return
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
            LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        }
    }
}
