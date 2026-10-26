package dev.rosalogia.photoduel.feature.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rosalogia.photoduel.core.common.friendlyTime
import dev.rosalogia.photoduel.core.data.PhotoGroup
import dev.rosalogia.photoduel.core.ui.BrandCard
import dev.rosalogia.photoduel.core.ui.BrandIconButton
import dev.rosalogia.photoduel.core.ui.BrandIcons
import dev.rosalogia.photoduel.core.ui.BrandTopBar
import dev.rosalogia.photoduel.core.ui.Ink
import dev.rosalogia.photoduel.core.ui.Moment
import dev.rosalogia.photoduel.core.ui.PhotoThumb
import dev.rosalogia.photoduel.core.ui.QuietButton
import dev.rosalogia.photoduel.core.ui.Readout
import dev.rosalogia.photoduel.core.ui.graphViewModel

private const val MAX_THUMBS = 4

@Composable
fun GroupsRoute(
    /** Null hides the back arrow, e.g. on desktop where the sidebar navigates. */
    onBack: (() -> Unit)?,
    onOpenTodoGroup: (Long) -> Unit,
    onOpenGroup: (Long) -> Unit,
    onEditGroup: (Long) -> Unit,
    topBarActions: @Composable () -> Unit = {},
    banner: @Composable () -> Unit = {},
    highlights: @Composable () -> Unit = {},
    viewModel: GroupsViewModel = graphViewModel { GroupsViewModel(it) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GroupsScreen(
        state = state,
        onTab = viewModel::selectTab,
        onShowAllTime = viewModel::setShowAllTime,
        onBack = onBack,
        onOpenTodoGroup = onOpenTodoGroup,
        onOpenGroup = onOpenGroup,
        onEditGroup = onEditGroup,
        topBarActions = topBarActions,
        banner = banner,
        highlights = highlights,
    )
}

@Composable
fun GroupsScreen(
    state: GroupsUiState,
    onTab: (GroupsTab) -> Unit,
    onShowAllTime: (Boolean) -> Unit,
    onBack: (() -> Unit)?,
    /** A to-do stack opens in the feed; a done one opens on its own. */
    onOpenTodoGroup: (Long) -> Unit,
    onOpenGroup: (Long) -> Unit,
    onEditGroup: (Long) -> Unit,
    topBarActions: @Composable () -> Unit = {},
    banner: @Composable () -> Unit = {},
    /** Content of the Highlights tab. */
    highlights: @Composable () -> Unit = {},
) {
    val bursts = state.tab != GroupsTab.HIGHLIGHTS
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        BrandTopBar(
            title = "Library",
            onBack = onBack,
            backTag = "listBack",
            backLabel = "Back to sorting",
            large = true,
        ) {
            if (bursts) {
                QuietButton(
                    if (state.showAllTime) "All time" else "Last 30 days",
                    onClick = { onShowAllTime(!state.showAllTime) },
                    color = if (state.showAllTime) Ink.Kept else Ink.Dim,
                    modifier = Modifier.testTag("scopeToggle"),
                )
            }
            topBarActions()
        }
        Tabs(state, onTab)
        Box(Modifier.fillMaxWidth().height(1.dp)) {
            if (state.indexing || state.analyzing > 0) {
                LinearProgressIndicator(Modifier.fillMaxSize().testTag("indexing"), color = Ink.Kept, trackColor = Ink.Hair)
            } else {
                Box(Modifier.fillMaxSize().background(Ink.Hair))
            }
        }
        banner()
        if (bursts && state.analyzing > 0) {
            Readout(
                "Analyzing · ${state.analyzing} photos left · stacks may still change",
                Modifier.padding(horizontal = 20.dp, vertical = 10.dp).testTag("analyzing"),
                small = true,
            )
        }
        if (!bursts) {
            highlights()
        } else if (state.loaded && state.visible.isEmpty()) {
            EmptyState(state)
        } else {
            // One column on a phone; as many as fit in a wide desktop window.
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 340.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().testTag("groupList"),
            ) {
                items(state.visible, key = { it.id }) { group ->
                    GroupCard(
                        group,
                        onClick = { if (state.tab == GroupsTab.TODO) onOpenTodoGroup(group.id) else onOpenGroup(group.id) },
                        onEdit = if (state.tab == GroupsTab.TODO) ({ onEditGroup(group.id) }) else null,
                    )
                }
            }
        }
    }
}

/** Upper-case mono tabs; the selected one is white and underlined in the accent. */
@Composable
private fun Tabs(state: GroupsUiState, onTab: (GroupsTab) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Tab("To sort ${state.todo.size}", state.tab == GroupsTab.TODO, Modifier.testTag("todoTab")) { onTab(GroupsTab.TODO) }
        Tab("Sorted ${state.done.size}", state.tab == GroupsTab.DONE, Modifier.testTag("doneTab")) { onTab(GroupsTab.DONE) }
        Tab("Highlights", state.tab == GroupsTab.HIGHLIGHTS, Modifier.testTag("highlightsTabButton")) { onTab(GroupsTab.HIGHLIGHTS) }
    }
}

@Composable
private fun Tab(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).padding(top = 10.dp), horizontalAlignment = Alignment.Start) {
        Readout(text, color = if (selected) Ink.Fg else Ink.Dim)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.size(width = if (selected) 18.dp else 0.dp, height = 1.5.dp).background(Ink.Kept))
    }
}

@Composable
private fun EmptyState(state: GroupsUiState) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
        when {
            state.indexing -> Moment("Looking for similar shots.")
            state.tab == GroupsTab.DONE -> Moment("Nothing sorted yet.", body = "Stacks you finish show up here, with the shot you kept.")
            state.showAllTime -> Moment("No stacks.", body = "There are no groups of similar shots in your photos.")
            else -> Moment("No stacks this month.", body = "Nothing from the last 30 days. Try “All time” above.")
        }
    }
}

@Composable
private fun GroupCard(group: PhotoGroup, onClick: () -> Unit, onEdit: (() -> Unit)?) {
    // Sorted stacks lead with the kept shot.
    val ordered = group.winnerMediaId?.let { w -> listOf(w) + (group.mediaIds - w) } ?: group.mediaIds
    val shown = ordered.take(MAX_THUMBS)
    val extra = ordered.size - shown.size
    BrandCard(Modifier.fillMaxWidth().testTag("groupCard"), onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            shown.forEachIndexed { i, id ->
                Box(Modifier.weight(1f).aspectRatio(1f)) {
                    val kept = i == 0 && group.winnerMediaId != null
                    PhotoThumb(
                        id,
                        Modifier.fillMaxSize().then(if (kept) Modifier.border(1.5.dp, Ink.Kept, RoundedCornerShape(6.dp)) else Modifier),
                        alpha = if (group.winnerMediaId != null && !kept) 0.45f else 1f,
                    )
                    if (i == shown.lastIndex && extra > 0) {
                        Box(
                            Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center,
                        ) { Readout("+$extra", color = Ink.Fg) }
                    }
                }
            }
            // Keep thumbnails the same size in small stacks.
            repeat(MAX_THUMBS - shown.size) { Spacer(Modifier.weight(1f)) }
        }
        Row(Modifier.padding(top = 12.dp, start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(friendlyTime(group.startTime), style = MaterialTheme.typography.titleSmall)
                Readout("${group.mediaIds.size} photos" + if (group.winnerMediaId != null) " · 1 kept" else "", small = true)
            }
            if (onEdit != null) {
                BrandIconButton(BrandIcons.Edit, "Edit stack", onEdit, Modifier.size(40.dp).testTag("editGroup"), tint = Ink.Dim)
            }
        }
    }
}
