package dev.rosalogia.photoduel.feature.duel

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs

val KingColor = Color(0xFF4C8DF6)
val ChallengerColor = Color(0xFFFF9F1C)

/** How the duel's top-left button behaves: a plain back arrow, or the feed's way to the list. */
data class DuelNavigation(
    val icon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    val label: String = "Back",
    val onClick: () -> Unit,
)

/**
 * One group's duel. [onFinished] runs when the group is done or dissolved; [subtitle] is an extra
 * line under the progress, such as the feed position.
 */
@Composable
fun DuelRoute(
    groupId: Long,
    onFinished: () -> Unit,
    navigation: DuelNavigation = DuelNavigation(onClick = onFinished),
    subtitle: String? = null,
) {
    val viewModel = hiltViewModel<DuelViewModel, DuelViewModel.Factory>(key = "duel-$groupId") { it.create(groupId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val finished by rememberUpdatedState(onFinished)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            when (e) {
                is DuelEvent.Message -> Toast.makeText(context, e.text, Toast.LENGTH_SHORT).show()
                DuelEvent.Close -> finished()
            }
        }
    }
    DuelTheme {
        when (val s = state) {
            DuelUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is DuelUiState.Error -> ErrorView(s.message, navigation)
            is DuelUiState.Dueling -> DuelScreen(
                s,
                navigation = navigation,
                subtitle = subtitle,
                onPick = viewModel::pick,
                onUndo = viewModel::undo,
                onKeepBoth = viewModel::keepBoth,
            )
            is DuelUiState.Result -> ResultScreen(
                state = s,
                navigation = navigation,
                subtitle = subtitle,
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

/** Photos read best on black, so the duel flow is always dark regardless of the system theme. */
@Composable
fun DuelTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = if (Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(context) else darkColorScheme()
    MaterialTheme(colorScheme = dark) {
        Surface(color = Color.Black, contentColor = Color.White, modifier = Modifier.fillMaxSize(), content = content)
    }
}

/** The top-left button plus a title with an optional second line, shared by the duel and result. */
@Composable
internal fun DuelTopBar(
    title: String,
    subtitle: String?,
    navigation: DuelNavigation,
    titleTag: String,
    actions: @Composable () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = navigation.onClick, modifier = Modifier.testTag("duelNav")) {
            Icon(navigation.icon, contentDescription = navigation.label)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag(titleTag))
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.testTag("duelSubtitle"),
                )
            }
        }
        actions()
    }
}

@Composable
private fun ErrorView(message: String, navigation: DuelNavigation) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, textAlign = TextAlign.Center)
        TextButton(onClick = navigation.onClick) { Text(navigation.label) }
    }
}

@Composable
fun DuelScreen(
    state: DuelUiState.Dueling,
    navigation: DuelNavigation,
    subtitle: String?,
    onPick: (keepChallenger: Boolean, flipCount: Int) -> Unit,
    onUndo: () -> Unit,
    onKeepBoth: () -> Unit,
) {
    val zoom = remember { ZoomState() }
    var showChallenger by remember { mutableStateOf(false) }
    // Per matchup: how many times the user flipped before deciding.
    var flips by remember(state.matchup) { mutableIntStateOf(0) }
    var dragX by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = with(LocalDensity.current) { 96.dp.toPx() }

    fun setFlip(on: Boolean) {
        if (on && !showChallenger) flips++
        showChallenger = on
    }

    fun pick(keepChallenger: Boolean) {
        showChallenger = false
        dragX = 0f
        onPick(keepChallenger, flips)
    }

    val edge = if (showChallenger) ChallengerColor else KingColor
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        DuelTopBar(
            title = (if (state.round == Round.RUNNER_UP) "Runner-up · " else "") +
                "Pick ${state.matchup.step + 1} of ${state.matchup.totalSteps}",
            subtitle = subtitle,
            navigation = navigation,
            titleTag = "duelProgress",
        ) {
            if (state.round == Round.MAIN) {
                TextButton(onClick = onKeepBoth, enabled = state.ready, modifier = Modifier.testTag("keepBoth")) { Text("Keep both") }
            }
            TextButton(onClick = onUndo, enabled = state.canUndo, modifier = Modifier.testTag("undo")) { Text("Undo") }
        }

        Box(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)
                .border(3.dp, edge, RoundedCornerShape(6.dp))
                .padding(3.dp),
        ) {
            if (state.ready) {
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
                    modifier = Modifier.fillMaxSize().graphicsLayer { translationX = dragX * 0.4f },
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            Label(
                text = if (showChallenger) "Challenger" else "Current pick",
                color = edge,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp).testTag("flipLabel"),
            )
            if (abs(dragX) > swipeThreshold * 0.3f) {
                val right = dragX > 0
                Label(
                    text = if (right) "Keep challenger →" else "← Keep current pick",
                    color = if (right) ChallengerColor else KingColor,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
                        .graphicsLayer { alpha = (abs(dragX) / swipeThreshold).coerceIn(0.3f, 1f) },
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { pick(keepChallenger = false) },
                enabled = state.ready,
                modifier = Modifier.weight(1f).testTag("keepKing"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = KingColor),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
            ) { Text("Keep\ncurrent", textAlign = TextAlign.Center, maxLines = 2, softWrap = false) }
            HoldButton(
                enabled = state.ready,
                onPress = ::setFlip,
                modifier = Modifier.weight(1.3f).height(64.dp).testTag("hold"),
            )
            OutlinedButton(
                onClick = { pick(keepChallenger = true) },
                enabled = state.ready,
                modifier = Modifier.weight(1f).testTag("keepChallenger"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ChallengerColor),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
            ) { Text("Keep\nchallenger", textAlign = TextAlign.Center, maxLines = 2, softWrap = false) }
        }
    }
}

@Composable
private fun Label(text: String, color: Color, modifier: Modifier = Modifier) {
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

/** A large button that shows the challenger for as long as it is held. */
@Composable
private fun HoldButton(enabled: Boolean, onPress: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    var held by remember { mutableStateOf(false) }
    Box(
        modifier
            .background(if (held) ChallengerColor else Color.White.copy(alpha = if (enabled) 0.15f else 0.05f), RoundedCornerShape(32.dp))
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown()
                    held = true
                    onPress(true)
                    waitForUpOrCancellation()
                    held = false
                    onPress(false)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Hold to compare",
            color = if (held) Color.Black else Color.White,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}
