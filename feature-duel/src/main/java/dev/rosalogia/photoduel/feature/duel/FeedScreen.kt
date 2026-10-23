package dev.rosalogia.photoduel.feature.duel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import java.text.DateFormat
import java.util.Date

/**
 * The main screen: to-do groups as a vertical feed, one duel per page. Swipe up or down to move
 * between groups; a finished group drops out of the feed and the next one takes its place.
 *
 * [jumpTo] scrolls to that group once it's in the feed (e.g. after tapping it in the list), then
 * [onJumped] clears the request.
 */
@Composable
fun FeedRoute(
    onOpenList: () -> Unit,
    jumpTo: Long?,
    onJumped: () -> Unit,
    /** Shown above the feed, e.g. the partial-access notice; null for none. */
    banner: (@Composable () -> Unit)? = null,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val groups = state.groups
    val pager = rememberPagerState { groups.size }
    val jumped by rememberUpdatedState(onJumped)

    LaunchedEffect(jumpTo, state.loaded, groups) {
        if (jumpTo == null || !state.loaded) return@LaunchedEffect
        val index = groups.indexOfFirst { it.id == jumpTo }
        if (index >= 0) pager.scrollToPage(index)
        jumped()
    }

    val listNavigation = DuelNavigation(icon = Icons.AutoMirrored.Filled.List, label = "All groups", onClick = onOpenList)
    DuelTheme {
        Column(Modifier.fillMaxSize()) {
            if (banner != null) Box(Modifier.statusBarsPadding()) { banner() }
            Box(Modifier.weight(1f).then(if (banner != null) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier)) {
                if (state.loaded && groups.isEmpty()) {
                    EmptyFeed(state, onOpenList, viewModel::showAllTime)
                } else {
                    VerticalPager(
                        state = pager,
                        key = { groups[it].id },
                        modifier = Modifier.fillMaxSize().testTag("feed"),
                    ) { page ->
                        val group = groups[page]
                        CompositionLocalProvider(LocalViewModelStoreOwner provides rememberPageViewModelStoreOwner()) {
                            DuelRoute(
                                groupId = group.id,
                                // A finished group leaves the to-do list, so the pager moves on by itself.
                                onFinished = {},
                                navigation = listNavigation,
                                subtitle = "${page + 1} of ${groups.size} · ${formatTime(group.startTime)}",
                            )
                        }
                    }
                }
                if (state.indexing || state.analyzing > 0) {
                    LinearProgressIndicator(
                        Modifier.align(Alignment.TopCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
                            .height(2.dp).testTag("feedIndexing"),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyFeed(state: FeedUiState, onOpenList: () -> Unit, onShowAllTime: () -> Unit) {
    val text = when {
        state.analyzing > 0 -> "Analyzing your photos… ${state.analyzing} left. Groups of similar shots will show up here."
        state.indexing -> "Looking for similar shots…"
        state.showAllTime -> "Nothing to sort. Every group of similar shots is done."
        else -> "Nothing to sort from the last 30 days."
    }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp).testTag("feedEmpty"),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        if (!state.showAllTime && state.analyzing == 0 && !state.indexing) {
            Button(onClick = onShowAllTime, modifier = Modifier.testTag("feedAllTime")) { Text("Include older photos") }
        }
        OutlinedButton(onClick = onOpenList, modifier = Modifier.testTag("feedOpenList")) { Text("All groups") }
    }
}

/**
 * Gives a page its own ViewModel store, cleared when the page leaves composition, so swiping
 * through many groups doesn't keep every group's full-size photos alive. Coming back reloads the
 * duel, which resumes from the stored decisions.
 */
@Composable
private fun rememberPageViewModelStoreOwner(): ViewModelStoreOwner {
    val parent = checkNotNull(LocalViewModelStoreOwner.current) { "No ViewModelStoreOwner for the feed" }
    val owner = remember(parent) { PageViewModelStoreOwner(parent) }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    return owner
}

/** Uses the parent's (Hilt) factory and extras, so hiltViewModel works inside a page. */
private class PageViewModelStoreOwner(private val parent: ViewModelStoreOwner) :
    ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    override val viewModelStore = ViewModelStore()
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = (parent as HasDefaultViewModelProviderFactory).defaultViewModelProviderFactory
    override val defaultViewModelCreationExtras: CreationExtras
        get() = (parent as? HasDefaultViewModelProviderFactory)?.defaultViewModelCreationExtras ?: CreationExtras.Empty
}

private fun formatTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
