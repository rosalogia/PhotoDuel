package dev.rosalogia.photoduel.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
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
import dev.rosalogia.photoduel.core.ui.BrandFonts
import dev.rosalogia.photoduel.core.ui.BrandIcons
import dev.rosalogia.photoduel.core.ui.BrandMark
import dev.rosalogia.photoduel.core.ui.BrandTheme
import dev.rosalogia.photoduel.core.ui.Ink
import dev.rosalogia.photoduel.core.ui.KeyShortcuts
import dev.rosalogia.photoduel.core.ui.Readout
import dev.rosalogia.photoduel.core.ui.LocalAppGraph
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

@Composable
fun App(app: DesktopApp, shortcuts: ShortcutDispatcher) {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context).components { add(PhotoRefFetcher.Factory(app.decoder)) }.build()
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val rootStores = remember { ScreenStores() }
    CompositionLocalProvider(
        LocalAppGraph provides app.graph,
        LocalShortcuts provides shortcuts,
        LocalPhotoActions provides app.actions,
        LocalMessages provides { text: String -> scope.launch { snackbar.showSnackbar(text) } },
        LocalViewModelStoreOwner provides remember { rootStores.owner("root") },
    ) {
        BrandTheme(DesktopFonts) {
            // Raised clear of the duel and highlight buttons along the bottom.
            Scaffold(
                containerColor = Ink.Black,
                snackbarHost = {
                    SnackbarHost(snackbar, Modifier.padding(bottom = 104.dp)) {
                        Snackbar(it, containerColor = Ink.Raised2, contentColor = Ink.Fg, shape = RoundedCornerShape(14.dp))
                    }
                },
            ) { padding ->
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

    Surface(Modifier.fillMaxSize(), color = Ink.Black, contentColor = Ink.Fg) {
        Row(Modifier.fillMaxSize()) {
            val root = stack.first()
            Sidebar(Modifier.fillMaxHeight()) {
                SidebarItem("Sort", BrandIcons.Pair, root == Screen.Sort) { go(Screen.Sort) }
                SidebarItem("Library", BrandIcons.Library, root == Screen.Library) { go(Screen.Library) }
                SidebarItem("Settings", BrandIcons.Settings, root == Screen.Settings) { go(Screen.Settings) }
                Spacer(Modifier.weight(1f))
                SidebarItem("Keys", BrandIcons.Keyboard, false) { showKeys = true }
            }
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
}

/** The window's left edge: the mark, then one item per destination. */
@Composable
private fun Sidebar(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Row(modifier) {
        Column(
            Modifier.width(76.dp).fillMaxHeight().padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BrandMark(Modifier.size(30.dp))
            Spacer(Modifier.height(18.dp))
            content()
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Ink.Hair))
    }
}

@Composable
private fun SidebarItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val color = if (selected || hovered) Ink.Fg else Ink.Dim
    Column(
        Modifier.width(64.dp).clip(RoundedCornerShape(12.dp))
            .background(if (hovered && !selected) Ink.Surface else Color.Transparent)
            .hoverable(hover)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(22.dp))
        Readout(label, small = true, color = color)
        Box(Modifier.size(width = 14.dp, height = 1.5.dp).background(if (selected) Ink.Kept else Color.Transparent))
    }
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
    Column(Modifier.fillMaxWidth()) {
        Readout(text, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), small = true)
        LinearProgressIndicator(Modifier.fillMaxWidth().height(1.dp), color = Ink.Kept, trackColor = Ink.Hair)
    }
}

/** Geist and Geist Mono, subset to Latin, from resources/fonts (SIL OFL). */
private val DesktopFonts = BrandFonts(
    sans = FontFamily(
        Font("fonts/Geist-Light.ttf", FontWeight.Light),
        Font("fonts/Geist-Regular.ttf", FontWeight.Normal),
        Font("fonts/Geist-Medium.ttf", FontWeight.Medium),
    ),
    mono = FontFamily(Font("fonts/GeistMono-Regular.ttf", FontWeight.Normal)),
)
