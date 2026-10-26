package dev.rosalogia.photoduel.feature.highlights

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.rosalogia.photoduel.core.common.AppLog
import dev.rosalogia.photoduel.core.ui.BrandIconButton
import dev.rosalogia.photoduel.core.ui.BrandIcons
import dev.rosalogia.photoduel.core.ui.BrandTopBar
import dev.rosalogia.photoduel.core.ui.FloatingBar
import dev.rosalogia.photoduel.core.ui.Ink
import dev.rosalogia.photoduel.core.ui.KeyShortcuts
import dev.rosalogia.photoduel.core.ui.Moment
import dev.rosalogia.photoduel.core.ui.PhotoStage
import dev.rosalogia.photoduel.core.ui.PhotoTag
import dev.rosalogia.photoduel.core.ui.PrimaryButton
import dev.rosalogia.photoduel.core.ui.QuietButton
import dev.rosalogia.photoduel.core.ui.Readout
import dev.rosalogia.photoduel.core.ui.SecondaryButton
import dev.rosalogia.photoduel.core.ui.rememberHaptics
import dev.rosalogia.photoduel.core.ui.LocalMessages
import dev.rosalogia.photoduel.core.ui.LocalPhotoActions
import dev.rosalogia.photoduel.core.ui.PhotoRef
import dev.rosalogia.photoduel.core.ui.graphViewModel
import dev.rosalogia.photoduel.core.ui.hasShortcuts
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.coroutines.launch

/** Keeping is the accent; skipping is quiet, since nothing is deleted. */
private val KeepColor = Ink.Kept
private val SkipColor = Ink.Fg

@Composable
fun HighlightSessionRoute(setId: Long, onClose: () -> Unit) {
    val viewModel = graphViewModel(key = "highlights-$setId") { HighlightSessionViewModel(setId, it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val showMessage = LocalMessages.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            when (e) {
                is HighlightEvent.Message -> showMessage(e.text)
            }
        }
    }
    PhotoStage {
        when (val s = state) {
            HighlightUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), color = Ink.Dim, strokeWidth = 1.5.dp)
            }
            is HighlightUiState.Error -> Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
                Moment("Can't open these highlights.", body = s.message) { QuietButton("Back", onClose, color = Ink.Fg) }
            }
            is HighlightUiState.Cull -> CullScreen(s, onClose, onDecide = viewModel::cull, onUndo = viewModel::undo)
            is HighlightUiState.PassDone -> PassDoneScreen(
                s,
                onClose = onClose,
                onUndo = viewModel::undo,
                onTrim = viewModel::startTrim,
                onAnotherPass = viewModel::anotherPass,
                onUseKept = viewModel::useKept,
                onStartOver = viewModel::startOver,
            )
            is HighlightUiState.Trim -> TrimScreen(s, onClose, onPick = viewModel::pick, onUndo = viewModel::undo)
            is HighlightUiState.Result -> ResultScreen(
                s,
                onClose = onClose,
                onUndo = viewModel::undo,
                onCopy = viewModel::copyToAlbum,
                onFavorited = viewModel::onFavorited,
                onRename = viewModel::rename,
            )
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    subtitle: String?,
    onClose: () -> Unit,
    undo: (() -> Unit)?,
    titleTag: String = "highlightTitle",
    onTitleClick: (() -> Unit)? = null,
) {
    BrandTopBar(
        title = title,
        subtitle = subtitle,
        onBack = onClose,
        titleTag = titleTag,
        subtitleTag = "highlightSubtitle",
        onTitleClick = onTitleClick,
    ) {
        if (undo != null) BrandIconButton(BrandIcons.Undo, "Undo", undo, Modifier.testTag("undo"))
    }
}

@Composable
private fun Photo(photo: HighlightPhoto, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit) {
    Box(modifier) {
        AsyncImage(
            model = PhotoRef(photo.mediaId),
            contentDescription = null,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
        )
        if (photo.burstSize > 1) {
            PhotoTag("Best of ${photo.burstSize}", Ink.Fg, Modifier.align(Alignment.BottomStart).padding(10.dp))
        }
    }
}

private fun picks(n: Int) = if (n == 1) "1 pick" else "$n picks"

@Composable
private fun CullScreen(state: HighlightUiState.Cull, onClose: () -> Unit, onDecide: (Boolean) -> Unit, onUndo: () -> Unit) {
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    // Reset per photo, so the next card starts centered.
    val offset = remember(state.current.mediaId) { Animatable(0f) }
    var width by remember { mutableStateOf(1000f) }
    val fling = with(LocalDensity.current) { 800.dp.toPx() }
    // One decision per photo, even if a button is tapped again while the card flies off.
    var deciding by remember(state.current.mediaId) { mutableStateOf(false) }

    fun decide(keep: Boolean) {
        if (deciding) return
        deciding = true
        if (keep) haptics.confirm() else haptics.tick()
        scope.launch {
            offset.animateTo(if (keep) width * 1.2f else -width * 1.2f)
            onDecide(keep)
        }
    }
    KeyShortcuts { e ->
        if (e.type != KeyEventType.KeyDown) return@KeyShortcuts false
        when {
            e.key == Key.DirectionRight -> true.also { decide(true) }
            e.key == Key.DirectionLeft -> true.also { decide(false) }
            e.isUndo() -> true.also { if (state.canUndo) onUndo() }
            else -> false
        }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TopBar(
            title = if (state.pass == 1) "Quick pass" else "Quick pass ${state.pass}",
            subtitle = "${state.position + 1} / ${state.total} · ${state.kept} kept · aiming for ${state.target}" +
                if (state.current.burstSize > 1) " · best of ${state.current.burstSize}" else "",
            onClose = onClose,
            undo = if (state.canUndo) onUndo else null,
        )
        Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
            state.next?.let { Photo(it.copy(burstSize = 1), Modifier.fillMaxSize().graphicsLayer { alpha = 0.35f }) }
            Photo(
                // Its burst size is in the title bar, which stays put while the photo moves.
                state.current.copy(burstSize = 1),
                Modifier.fillMaxSize().testTag("cullPhoto")
                    .graphicsLayer {
                        width = size.width
                        translationX = offset.value
                        rotationZ = offset.value / size.width * 12f
                    }
                    .background(Color.Black)
                    .pointerInput(state.current.mediaId) {
                        // Measured from where the finger went down to where it lifts, so a quick
                        // flick counts in full even when few move events arrive in between.
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                            val start = offset.value
                            var horizontal: Boolean? = null
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                velocity.addPosition(change.uptimeMillis, change.position)
                                val dx = change.position.x - down.position.x
                                val dy = change.position.y - down.position.y
                                if (horizontal == null && hypot(dx, dy) > viewConfiguration.touchSlop) horizontal = abs(dx) > abs(dy)
                                if (horizontal == true) {
                                    change.consume()
                                    scope.launch { offset.snapTo(start + dx) }
                                }
                                if (!change.pressed) {
                                    if (horizontal == true) {
                                        val v = velocity.calculateVelocity().x
                                        val flung = abs(v) > fling && abs(dx) > threshold * 0.25f && (v > 0) == (dx > 0)
                                        if (abs(dx) > threshold || flung) decide(dx > 0)
                                    }
                                    break
                                }
                            }
                            // Not far or fast enough, or cancelled: spring back.
                            if (horizontal == true && !deciding) scope.launch { offset.animateTo(0f) }
                        }
                    },
            )
            if (abs(offset.value) > threshold * 0.3f) {
                val keep = offset.value > 0
                PhotoTag(
                    if (keep) "Keep" else "Skip",
                    if (keep) KeepColor else SkipColor,
                    Modifier.align(if (keep) Alignment.TopStart else Alignment.TopEnd).padding(16.dp)
                        .graphicsLayer { alpha = (abs(offset.value) / threshold).coerceIn(0.3f, 1f) },
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
            FloatingBar {
                BarAction("← Skip", SkipColor, Modifier.testTag("skip")) { decide(false) }
                Box(Modifier.width(1.dp).height(28.dp).background(Ink.Hair))
                BarAction("Keep →", KeepColor, Modifier.testTag("keep")) { decide(true) }
            }
        }
    }
}

@Composable
private fun PassDoneScreen(
    state: HighlightUiState.PassDone,
    onClose: () -> Unit,
    onUndo: () -> Unit,
    onTrim: () -> Unit,
    onAnotherPass: () -> Unit,
    onUseKept: () -> Unit,
    onStartOver: () -> Unit,
) {
    val kept = state.kept.size
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TopBar(title = state.name, subtitle = null, onClose = onClose, undo = onUndo)
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("You kept $kept of ${state.reviewed}.", style = MaterialTheme.typography.displaySmall, modifier = Modifier.testTag("passSummary"))
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    kept == 0 -> "Nothing kept this time."
                    kept > state.target -> "You're aiming for ${state.target}, so ${kept - state.target} more need to go."
                    kept < state.target -> "That's fewer than the ${state.target} you were aiming for, which is fine."
                    else -> "Exactly the ${state.target} you were aiming for."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.Dim,
            )
        }
        FittingGrid(state.kept, key = { it.mediaId }, modifier = Modifier.weight(1f).fillMaxWidth()) { photo, tile ->
            Photo(photo.copy(burstSize = 1), tile.clip(RoundedCornerShape(4.dp)), ContentScale.Crop)
        }
        Column(
            Modifier.align(Alignment.CenterHorizontally).widthIn(max = 600.dp).fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                kept == 0 -> PrimaryButton("Start over", onStartOver, Modifier.fillMaxWidth().testTag("startOver"))
                kept > state.target -> {
                    PrimaryButton("Duel down to ${state.target} · about ${picks(state.estimatedPicks)}", onTrim, Modifier.fillMaxWidth().testTag("trim"))
                    SecondaryButton("Another quick pass through these $kept", onAnotherPass, Modifier.fillMaxWidth().testTag("anotherPass"))
                    QuietButton("Keep all $kept", onUseKept, Modifier.fillMaxWidth().testTag("useKept"))
                }
                else -> PrimaryButton(if (kept == 1) "Use this one" else "Use these $kept", onUseKept, Modifier.fillMaxWidth().testTag("useKept"))
            }
        }
    }
}

@Composable
private fun TrimScreen(state: HighlightUiState.Trim, onClose: () -> Unit, onPick: (Long) -> Unit, onUndo: () -> Unit) {
    var peek by remember { mutableStateOf<HighlightPhoto?>(null) }
    KeyShortcuts { e ->
        if (e.type != KeyEventType.KeyDown) return@KeyShortcuts false
        when {
            e.key == Key.DirectionLeft || e.key == Key.DirectionUp -> true.also { onPick(state.first.mediaId) }
            e.key == Key.DirectionRight || e.key == Key.DirectionDown -> true.also { onPick(state.second.mediaId) }
            e.isUndo() -> true.also { if (state.canUndo) onUndo() }
            else -> false
        }
    }
    val keys = hasShortcuts
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TopBar(
                title = "Which is better?",
                subtitle = "${state.stillIn} still in · keeping ${state.target} · about ${picks(state.picksLeft)} left",
                onClose = onClose,
                undo = if (state.canUndo) onUndo else null,
            )
            // Stacked on a phone; side by side in a wide window, where photos get more room that way.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val sideBySide = maxWidth > maxHeight
                val photos: @Composable (Modifier) -> Unit = { slot ->
                    for ((i, photo) in listOf(state.first, state.second).withIndex()) {
                        Photo(
                            photo,
                            slot.padding(horizontal = 8.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Ink.Surface)
                                .testTag(if (i == 0) "pickFirst" else "pickSecond")
                                .pointerInput(photo.mediaId) {
                                    detectTapGestures(
                                        onTap = { onPick(photo.mediaId) },
                                        // Hold to see it full screen until release; a long press never picks.
                                        onPress = {
                                            tryAwaitRelease()
                                            peek = null
                                        },
                                        onLongPress = { peek = photo },
                                    )
                                },
                        )
                    }
                }
                if (sideBySide) {
                    Row(Modifier.fillMaxSize()) { photos(Modifier.weight(1f).fillMaxHeight()) }
                } else {
                    Column(Modifier.fillMaxSize()) { photos(Modifier.weight(1f).fillMaxWidth()) }
                }
            }
            Readout(
                if (keys) "Click the better one · ← → · hold to see it full size" else "Tap the better one · hold to see it full screen",
                Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp),
                small = true,
                color = Ink.Faint,
            )
        }
        peek?.let { Photo(it, Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) }
    }
}

@Composable
private fun ResultScreen(
    state: HighlightUiState.Result,
    onClose: () -> Unit,
    onUndo: () -> Unit,
    onCopy: () -> Unit,
    onFavorited: () -> Unit,
    onRename: (String) -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    if (renaming) {
        var text by remember { mutableStateOf(state.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            containerColor = Ink.Raised,
            title = { Text("Rename") },
            text = {
                Column {
                    OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text(
                        "Also the album's folder name for photos copied from now on.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink.Dim,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(text)
                    renaming = false
                }, enabled = text.isNotBlank()) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }, colors = ButtonDefaults.textButtonColors(contentColor = Ink.Fg)) { Text("Cancel") } },
        )
    }
    val actions = LocalPhotoActions.current
    val scope = rememberCoroutineScope()
    val ids = state.selected.map { it.mediaId }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TopBar(
            title = state.name,
            subtitle = "${ids.size} highlights" + formatRange(state.startTime, state.endTime).let { if (it == state.name) "" else " · $it" },
            onClose = onClose,
            undo = onUndo,
            titleTag = "resultTitle",
            onTitleClick = { renaming = true },
        )
        FittingGrid(
            state.selected,
            key = { it.mediaId },
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("highlightGrid"),
            minTile = 120.dp,
            padding = 8.dp,
        ) { photo, tile ->
            Photo(
                photo.copy(burstSize = 1),
                tile.clip(RoundedCornerShape(4.dp)).clickable { actions.open(photo.mediaId) },
                ContentScale.Crop,
            )
        }
        Column(
            Modifier.align(Alignment.CenterHorizontally).widthIn(max = 600.dp).fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val allCopied = state.copied == ids.size
            if (allCopied) {
                // Already done: a quiet confirmation that still opens the copy again if pressed.
                val album = state.albumPath.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\')
                SecondaryButton("In album “$album”", onCopy, Modifier.fillMaxWidth().testTag("copyToAlbum"), icon = BrandIcons.Check)
            } else {
                PrimaryButton(
                    when {
                        state.copying != null -> "Copying ${state.copying + 1} of ${ids.size}…"
                        state.copied > 0 -> "Copy the rest to the album"
                        else -> "Copy to album"
                    },
                    onClick = onCopy,
                    enabled = state.copying == null,
                    modifier = Modifier.fillMaxWidth().testTag("copyToAlbum"),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(actions.share.label, onClick = { actions.share(ids) }, icon = actions.share.icon, modifier = Modifier.weight(1f).testTag("shareAll"))
                if (actions.canFavorite) {
                    SecondaryButton(
                        "Favorite",
                        onClick = {
                            scope.launch {
                                val ok = actions.favorite(ids)
                                AppLog.i("Highlights", "Favorite request ${if (ok) "confirmed" else "cancelled"}")
                                if (ok) onFavorited()
                            }
                        },
                        icon = BrandIcons.Heart,
                        modifier = Modifier.weight(1f).testTag("favoriteAll"),
                    )
                }
            }
        }
    }
}

/** One half of the quick pass's bar. */
@Composable
private fun RowScope.BarAction(label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.weight(1f).fillMaxHeight().padding(vertical = 4.dp).clip(RoundedCornerShape(28.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MaterialTheme.typography.labelLarge, color = color) }
}

private fun androidx.compose.ui.input.key.KeyEvent.isUndo() =
    (key == Key.Z && (isCtrlPressed || isMetaPressed)) || key == Key.Backspace
