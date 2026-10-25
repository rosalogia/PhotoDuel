package dev.rosalogia.photoduel.feature.highlights

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.rosalogia.photoduel.core.common.AppLog
import dev.rosalogia.photoduel.core.ui.KeyShortcuts
import dev.rosalogia.photoduel.core.ui.LocalDarkColors
import dev.rosalogia.photoduel.core.ui.LocalMessages
import dev.rosalogia.photoduel.core.ui.LocalPhotoActions
import dev.rosalogia.photoduel.core.ui.PhotoRef
import dev.rosalogia.photoduel.core.ui.graphViewModel
import dev.rosalogia.photoduel.core.ui.hasShortcuts
import kotlin.math.abs
import kotlinx.coroutines.launch

private val KeepColor = Color(0xFF4CC38A)
private val SkipColor = Color(0xFFE5484D)

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
    HighlightTheme {
        when (val s = state) {
            HighlightUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is HighlightUiState.Error -> Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(s.message, textAlign = TextAlign.Center)
                TextButton(onClick = onClose) { Text("Back") }
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

/** Photos read best on black, like the duel. */
@Composable
private fun HighlightTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LocalDarkColors.current) {
        Surface(color = Color.Black, contentColor = Color.White, modifier = Modifier.fillMaxSize(), content = content)
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
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        Column(Modifier.weight(1f).then(if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag(titleTag))
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.testTag("highlightSubtitle"),
                )
            }
        }
        if (undo != null) TextButton(onClick = undo, modifier = Modifier.testTag("undo")) { Text("Undo") }
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
            Text(
                "Best of ${photo.burstSize}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

private fun picks(n: Int) = if (n == 1) "1 pick" else "$n picks"

@Composable
private fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
            .border(2.dp, color, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun CullScreen(state: HighlightUiState.Cull, onClose: () -> Unit, onDecide: (Boolean) -> Unit, onUndo: () -> Unit) {
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }
    val scope = rememberCoroutineScope()
    // Reset per photo, so the next card starts centered.
    val offset = remember(state.current.mediaId) { Animatable(0f) }
    var width by remember { mutableStateOf(1000f) }

    fun decide(keep: Boolean) {
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
            subtitle = "${state.position + 1} of ${state.total} · ${state.kept} kept · aiming for ${state.target}",
            onClose = onClose,
            undo = if (state.canUndo) onUndo else null,
        )
        Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
            state.next?.let { Photo(it, Modifier.fillMaxSize().graphicsLayer { alpha = 0.35f }) }
            Photo(
                state.current,
                Modifier.fillMaxSize().testTag("cullPhoto")
                    .graphicsLayer {
                        width = size.width
                        translationX = offset.value
                        rotationZ = offset.value / size.width * 12f
                    }
                    .background(Color.Black)
                    .pointerInput(state.current.mediaId) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (abs(offset.value) > threshold) decide(offset.value > 0) else scope.launch { offset.animateTo(0f) }
                            },
                            onDragCancel = { scope.launch { offset.animateTo(0f) } },
                        ) { change, dx ->
                            change.consume()
                            scope.launch { offset.snapTo(offset.value + dx) }
                        }
                    },
            )
            if (abs(offset.value) > threshold * 0.3f) {
                val keep = offset.value > 0
                Pill(
                    if (keep) "Keep" else "Skip",
                    if (keep) KeepColor else SkipColor,
                    Modifier.align(if (keep) Alignment.TopStart else Alignment.TopEnd).padding(16.dp)
                        .graphicsLayer { alpha = (abs(offset.value) / threshold).coerceIn(0.3f, 1f) },
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { decide(false) },
                modifier = Modifier.weight(1f).height(56.dp).testTag("skip"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SkipColor),
            ) { Text("← Skip") }
            OutlinedButton(
                onClick = { decide(true) },
                modifier = Modifier.weight(1f).height(56.dp).testTag("keep"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = KeepColor),
            ) { Text("Keep →") }
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
            Text("You kept $kept of ${state.reviewed}", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("passSummary"))
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    kept == 0 -> "Nothing kept this time."
                    kept > state.target -> "You're aiming for ${state.target}, so ${kept - state.target} more need to go."
                    kept < state.target -> "That's fewer than the ${state.target} you were aiming for, which is fine."
                    else -> "Exactly the ${state.target} you were aiming for."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(state.kept, key = { it.mediaId }) {
                Photo(it.copy(burstSize = 1), Modifier.aspectRatio(1f).clip(RoundedCornerShape(6.dp)), ContentScale.Crop)
            }
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                kept == 0 -> Button(onClick = onStartOver, modifier = Modifier.fillMaxWidth().testTag("startOver")) { Text("Start over") }
                kept > state.target -> {
                    Button(onClick = onTrim, modifier = Modifier.fillMaxWidth().testTag("trim")) {
                        Text("Narrow down head to head (about ${picks(state.estimatedPicks)})")
                    }
                    OutlinedButton(onClick = onAnotherPass, modifier = Modifier.fillMaxWidth().testTag("anotherPass")) {
                        Text("Another quick pass through these $kept")
                    }
                    TextButton(onClick = onUseKept, modifier = Modifier.fillMaxWidth().testTag("useKept")) { Text("Keep all $kept") }
                }
                else -> Button(onClick = onUseKept, modifier = Modifier.fillMaxWidth().testTag("useKept")) {
                    Text(if (kept == 1) "Use this one" else "Use these $kept")
                }
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
                title = "Which one is better?",
                subtitle = "${state.stillIn} still in, keeping ${state.target} · about ${picks(state.picksLeft)} left",
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
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.06f))
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
            Text(
                if (keys) "Click the better one, or press ← / → · hold to see it full size" else "Tap the better one · hold to see it full screen",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
            title = { Text("Rename") },
            text = {
                Column {
                    OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text(
                        "Also the album's folder name for photos copied from now on.",
                        style = MaterialTheme.typography.bodySmall,
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
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
    val actions = LocalPhotoActions.current
    val scope = rememberCoroutineScope()
    val ids = state.selected.map { it.mediaId }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TopBar(
            title = state.name,
            subtitle = "${ids.size} highlights · ${formatRange(state.startTime, state.endTime)}",
            onClose = onClose,
            undo = onUndo,
            titleTag = "resultTitle",
            onTitleClick = { renaming = true },
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 120.dp),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f).testTag("highlightGrid"),
        ) {
            items(state.selected, key = { it.mediaId }) { photo ->
                Photo(
                    photo.copy(burstSize = 1),
                    Modifier.aspectRatio(1f).clip(RoundedCornerShape(6.dp)).clickable { actions.open(photo.mediaId) },
                    ContentScale.Crop,
                )
            }
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val allCopied = state.copied == ids.size
            Button(
                onClick = onCopy,
                enabled = state.copying == null,
                modifier = Modifier.fillMaxWidth().testTag("copyToAlbum"),
            ) {
                Text(
                    when {
                        state.copying != null -> "Copying ${state.copying + 1} of ${ids.size}…"
                        allCopied -> "In album ${state.albumPath.trimEnd('/')} ✓"
                        state.copied > 0 -> "Copy the rest to the album"
                        else -> "Copy to album"
                    },
                    maxLines = 1,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { actions.share(ids) }, modifier = Modifier.weight(1f).testTag("shareAll")) {
                    Icon(actions.share.icon, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(actions.share.label)
                }
                if (actions.canFavorite) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val ok = actions.favorite(ids)
                                AppLog.i("Highlights", "Favorite request ${if (ok) "confirmed" else "cancelled"}")
                                if (ok) onFavorited()
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("favoriteAll"),
                    ) {
                        Icon(Icons.Filled.Favorite, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Favorite")
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.input.key.KeyEvent.isUndo() =
    (key == Key.Z && (isCtrlPressed || isMetaPressed)) || key == Key.Backspace
