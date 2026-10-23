package dev.rosalogia.photoduel.feature.groups

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.rosalogia.photoduel.core.data.PhotoGroup
import dev.rosalogia.photoduel.core.data.media.mediaContentUri
import java.text.DateFormat
import java.util.Date

private const val MAX_THUMBS = 4

@Composable
fun GroupsRoute(
    onBack: () -> Unit,
    onOpenTodoGroup: (Long) -> Unit,
    onOpenGroup: (Long) -> Unit,
    onEditGroup: (Long) -> Unit,
    topBarActions: @Composable () -> Unit = {},
    banner: @Composable () -> Unit = {},
    viewModel: GroupsViewModel = hiltViewModel(),
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
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    state: GroupsUiState,
    onTab: (GroupsTab) -> Unit,
    onShowAllTime: (Boolean) -> Unit,
    onBack: () -> Unit,
    /** A to-do group opens in the feed; a done one opens on its own. */
    onOpenTodoGroup: (Long) -> Unit,
    onOpenGroup: (Long) -> Unit,
    onEditGroup: (Long) -> Unit,
    topBarActions: @Composable () -> Unit = {},
    banner: @Composable () -> Unit = {},
) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("All groups") },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("listBack")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to feed")
                        }
                    },
                    actions = {
                        FilterChip(
                            selected = state.showAllTime,
                            onClick = { onShowAllTime(!state.showAllTime) },
                            label = { Text(if (state.showAllTime) "All time" else "Last 30 days") },
                            modifier = Modifier.testTag("scopeToggle"),
                        )
                        topBarActions()
                    },
                )
                PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
                    Tab(
                        selected = state.tab == GroupsTab.TODO,
                        onClick = { onTab(GroupsTab.TODO) },
                        text = { Text("To do (${state.todo.size})") },
                    )
                    Tab(
                        selected = state.tab == GroupsTab.DONE,
                        onClick = { onTab(GroupsTab.DONE) },
                        text = { Text("Done (${state.done.size})") },
                    )
                }
                if (state.indexing || state.analyzing > 0) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().testTag("indexing"))
                } else {
                    Spacer(Modifier.height(4.dp))
                }
                if (state.analyzing > 0) {
                    Text(
                        "Analyzing photos… ${state.analyzing} left. Groups may still change.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("analyzing"),
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            banner()
            if (state.loaded && state.visible.isEmpty()) {
                EmptyState(state)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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
}

@Composable
private fun EmptyState(state: GroupsUiState) {
    val text = when {
        state.indexing -> "Looking for similar shots…"
        state.tab == GroupsTab.DONE -> "Groups you finish will show up here."
        state.showAllTime -> "No groups of similar shots found in your camera roll."
        else -> "No groups of similar shots in the last 30 days. Try \"All time\"."
    }
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun GroupCard(group: PhotoGroup, onClick: () -> Unit, onEdit: (() -> Unit)?) {
    // Resolved groups lead with their winner.
    val ordered = group.winnerMediaId?.let { w -> listOf(w) + (group.mediaIds - w) } ?: group.mediaIds
    val shown = ordered.take(MAX_THUMBS)
    val extra = ordered.size - shown.size
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("groupCard"),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                shown.forEachIndexed { i, id ->
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        Thumbnail(id, Modifier.fillMaxSize())
                        if (i == 0 && group.winnerMediaId != null) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = "Winner",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50)),
                            )
                        }
                        if (i == shown.lastIndex && extra > 0) {
                            Box(
                                Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("+$extra", color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
                // Keep thumbnails the same size in small groups.
                repeat(MAX_THUMBS - shown.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${group.mediaIds.size} photos", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(8.dp))
                Text(
                    formatGroupTime(group.startTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (onEdit != null) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp).testTag("editGroup")) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit group", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun Thumbnail(mediaId: Long, modifier: Modifier = Modifier) {
    AsyncImage(
        model = mediaContentUri(mediaId),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

private fun formatGroupTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
