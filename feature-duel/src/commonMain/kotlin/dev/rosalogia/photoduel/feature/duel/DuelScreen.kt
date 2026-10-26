package dev.rosalogia.photoduel.feature.duel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rosalogia.photoduel.core.ui.BrandIconButton
import dev.rosalogia.photoduel.core.ui.BrandIcons
import dev.rosalogia.photoduel.core.ui.BrandTopBar
import dev.rosalogia.photoduel.core.ui.FloatingBar
import dev.rosalogia.photoduel.core.ui.Ink
import dev.rosalogia.photoduel.core.ui.KeyShortcuts
import dev.rosalogia.photoduel.core.ui.LocalMessages
import dev.rosalogia.photoduel.core.ui.Moment
import dev.rosalogia.photoduel.core.ui.PhotoStage
import dev.rosalogia.photoduel.core.ui.PhotoTag
import dev.rosalogia.photoduel.core.ui.QuietButton
import dev.rosalogia.photoduel.core.ui.Readout
import dev.rosalogia.photoduel.core.ui.StepProgress
import dev.rosalogia.photoduel.core.ui.graphViewModel
import dev.rosalogia.photoduel.core.ui.hasShortcuts
import dev.rosalogia.photoduel.core.ui.rememberHaptics
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Your pick is framed in the accent; the challenger in plain white. */
val KingColor = Ink.Kept
val ChallengerColor = Ink.Challenger

/** How the duel's top-left button behaves: a plain back arrow, or the feed's way to the library. */
data class DuelNavigation(
    val icon: ImageVector = BrandIcons.Back,
    val label: String = "Back",
    val onClick: () -> Unit,
)

/**
 * One stack's duel. [onFinished] runs when the stack is done or dissolved. [title] names the stack,
 * such as when it was taken; [position] is where it sits in a list, such as "3 / 9".
 */
@Composable
fun DuelRoute(
    groupId: Long,
    onFinished: () -> Unit,
    /** Null hides the top-left button, e.g. on desktop where a sidebar does that job. */
    navigation: DuelNavigation? = DuelNavigation(onClick = onFinished),
    title: String? = null,
    position: String? = null,
) {
    val viewModel = graphViewModel(key = "duel-$groupId") { DuelViewModel.create(groupId, it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val showMessage = LocalMessages.current
    val finished by rememberUpdatedState(onFinished)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            when (e) {
                is DuelEvent.Message -> showMessage(e.text)
                DuelEvent.Close -> finished()
            }
        }
    }
    PhotoStage {
        when (val s = state) {
            DuelUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner() }
            is DuelUiState.Error -> ErrorView(s.message, navigation)
            is DuelUiState.Dueling -> DuelScreen(
                s,
                navigation = navigation,
                title = title,
                position = position,
                onPick = viewModel::pick,
                onUndo = viewModel::undo,
                onKeepBoth = viewModel::keepBoth,
            )
            is DuelUiState.Result -> ResultScreen(
                state = s,
                navigation = navigation,
                subtitle = listOfNotNull(title, position?.let { "Stack $it" }).joinToString(" · ").ifEmpty { null },
                onUndo = viewModel::undo,
                onPickRunnerUp = viewModel::pickRunnerUp,
                onDone = viewModel::markDone,
                onTrashed = viewModel::onTrashed,
                onFavorited = viewModel::onFavorited,
                onDuelAgain = viewModel::duelAgain,
            )
        }
    }
}

/** A thin, quiet loading indicator. */
@Composable
internal fun Spinner() {
    CircularProgressIndicator(Modifier.size(28.dp), color = Ink.Dim, strokeWidth = 1.5.dp, trackColor = Color.Transparent)
}

@Composable
private fun ErrorView(message: String, navigation: DuelNavigation?) {
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Moment(title = "Can't open this stack.", body = message) {
            if (navigation != null) QuietButton(navigation.label, navigation.onClick, color = Ink.Fg)
        }
    }
}

@Composable
fun DuelScreen(
    state: DuelUiState.Dueling,
    navigation: DuelNavigation?,
    title: String?,
    position: String?,
    onPick: (keepChallenger: Boolean, flipCount: Int) -> Unit,
    onUndo: () -> Unit,
    onKeepBoth: () -> Unit,
) {
    val zoom = remember { ZoomState() }
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var showChallenger by remember { mutableStateOf(false) }
    // Per matchup: how many times the user flipped before deciding.
    var flips by remember(state.matchup) { mutableIntStateOf(0) }
    var dragX by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = with(LocalDensity.current) { 96.dp.toPx() }
    // The frame thickens for a moment after each pick.
    val pulse = remember { Animatable(0f) }

    fun setFlip(on: Boolean) {
        if (on == showChallenger) return
        if (on) flips++
        showChallenger = on
        haptics.tick()
    }

    fun pick(keepChallenger: Boolean) {
        showChallenger = false
        dragX = 0f
        haptics.confirm()
        scope.launch {
            pulse.snapTo(1f)
            pulse.animateTo(0f, tween(360))
        }
        onPick(keepChallenger, flips)
    }

    KeyShortcuts { e ->
        val down = e.type == KeyEventType.KeyDown
        when {
            e.key == Key.Spacebar -> {
                if (state.ready) setFlip(down)
                true
            }
            !down -> false
            e.key == Key.DirectionLeft && state.ready -> true.also { pick(keepChallenger = false) }
            e.key == Key.DirectionRight && state.ready -> true.also { pick(keepChallenger = true) }
            (e.key == Key.Z && (e.isCtrlPressed || e.isMetaPressed)) || e.key == Key.Backspace ->
                true.also { if (state.canUndo) onUndo() }
            e.key == Key.B && state.round == Round.MAIN && state.ready -> true.also { onKeepBoth() }
            e.key == Key.Equals || e.key == Key.Plus || e.key == Key.NumPadAdd -> true.also { zoom.zoomBy(1.5f) }
            e.key == Key.Minus || e.key == Key.NumPadSubtract -> true.also { zoom.zoomBy(1 / 1.5f) }
            e.key == Key.Zero || e.key == Key.NumPad0 -> true.also { zoom.reset() }
            else -> false
        }
    }
    val keys = hasShortcuts
    // Everything but the photo steps back while you compare.
    val chrome by animateFloatAsState(if (showChallenger) 0.3f else 1f, tween(140))

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        val photos = state.matchup.totalSteps + 1
        Column(Modifier.alpha(chrome)) {
            BrandTopBar(
                title = if (state.round == Round.RUNNER_UP) "Pick a runner-up" else title ?: "Pick the better shot",
                subtitle = listOfNotNull(
                    position?.let { "Stack $it" },
                    if (state.round == Round.MAIN) "$photos photos" else title,
                ).joinToString(" · "),
                onBack = navigation?.onClick,
                backIcon = navigation?.icon ?: BrandIcons.Back,
                backLabel = navigation?.label ?: "Back",
                backTag = "duelNav",
                titleTag = "duelTitle",
                subtitleTag = "duelSubtitle",
            ) {
                if (state.round == Round.MAIN) {
                    QuietButton("Keep both", onKeepBoth, enabled = state.ready, modifier = Modifier.testTag("keepBoth"))
                }
                BrandIconButton(BrandIcons.Undo, "Undo", onUndo, Modifier.testTag("undo"), enabled = state.canUndo)
            }
            StepProgress(
                step = state.matchup.step,
                total = state.matchup.totalSteps,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp).testTag("duelProgress"),
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 6.dp, vertical = 12.dp)) {
            if (state.ready) {
                val edge = if (showChallenger) ChallengerColor else KingColor
                FlipView(
                    king = state.king!!,
                    challenger = state.challenger!!,
                    alignment = state.alignment,
                    focusFace = state.kingFace,
                    showChallenger = showChallenger,
                    zoom = zoom,
                    onPress = ::setFlip,
                    onSwipe = { keepChallenger -> pick(keepChallenger) },
                    onDrag = { dragX = it },
                    frameColor = edge,
                    frameWidth = 2.dp + 3.dp * pulse.value,
                    tag = {
                        PhotoTag(
                            if (showChallenger) "Challenger" else "Yours",
                            edge,
                            Modifier.testTag("flipLabel"),
                        )
                    },
                    modifier = Modifier.fillMaxSize().graphicsLayer { translationX = dragX * 0.4f },
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner() }
            }
            if (abs(dragX) > swipeThreshold * 0.3f) {
                val right = dragX > 0
                PhotoTag(
                    if (right) "Keep challenger →" else "← Keep yours",
                    if (right) ChallengerColor else KingColor,
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
                        .graphicsLayer { alpha = (abs(dragX) / swipeThreshold).coerceIn(0.3f, 1f) },
                )
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FloatingBar(Modifier.alpha(0.5f + chrome / 2)) {
                PickButton("← Keep", "Yours", KingColor, state.ready, Modifier.testTag("keepKing")) { pick(keepChallenger = false) }
                HoldButton(
                    enabled = state.ready,
                    held = showChallenger,
                    onPress = ::setFlip,
                    modifier = Modifier.testTag("hold"),
                )
                PickButton("Keep →", "Challenger", ChallengerColor, state.ready, Modifier.testTag("keepChallenger")) {
                    pick(keepChallenger = true)
                }
            }
            if (keys) Readout("Hold space to compare · ← → keep · B both · Z undo", small = true, color = Ink.Faint)
        }
    }
}

/** One side of the bar: what it does on top, whose photo it keeps underneath. */
@Composable
private fun RowScope.PickButton(
    label: String,
    whose: String,
    color: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier.weight(1f).fillMaxHeight().padding(vertical = 4.dp).clip(RoundedCornerShape(28.dp)).clickable(enabled = enabled, onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (enabled) color else Ink.Faint)
        Readout(whose, small = true, color = if (enabled) color.copy(alpha = 0.6f) else Ink.Faint)
    }
}

/** A white pill that shows the challenger for as long as it's held; outlined while held, however that happens. */
@Composable
private fun HoldButton(enabled: Boolean, held: Boolean, onPress: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(28.dp)
    val press by rememberUpdatedState(onPress)
    Row(
        modifier
            .height(52.dp)
            .scale(if (held) 0.96f else 1f)
            .then(
                if (held) Modifier.border(1.5.dp, Color.White, shape)
                else Modifier.background(if (enabled) Color.White else Ink.Raised, shape),
            )
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown()
                    press(true)
                    waitForUpOrCancellation()
                    press(false)
                }
            }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val content = if (held) Color.White else if (enabled) Ink.Black else Ink.Faint
        Icon(BrandIcons.Pair, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Hold to compare", style = MaterialTheme.typography.labelLarge, color = content, textAlign = TextAlign.Center, maxLines = 1)
    }
}
