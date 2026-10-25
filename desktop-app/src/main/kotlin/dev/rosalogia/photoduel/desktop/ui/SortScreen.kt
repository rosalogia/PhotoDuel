package dev.rosalogia.photoduel.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import coil3.compose.AsyncImage
import dev.rosalogia.photoduel.core.common.friendlyTime
import dev.rosalogia.photoduel.core.data.PhotoGroup
import dev.rosalogia.photoduel.core.ui.KeyShortcuts
import dev.rosalogia.photoduel.core.ui.PhotoRef
import dev.rosalogia.photoduel.core.ui.graphViewModel
import dev.rosalogia.photoduel.feature.duel.DuelRoute
import dev.rosalogia.photoduel.feature.duel.FeedViewModel

/**
 * The desktop take on the phone's feed: to-do groups in a list on the left, the selected group's
 * duel on the right. Finishing a group moves on to the next one.
 */
@Composable
fun SortScreen(selected: Long?, onSelect: (Long?) -> Unit, onOpenLibrary: () -> Unit) {
    val viewModel = graphViewModel { FeedViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val groups = state.groups
    // Where the selection was, so a finished group is replaced by the one that took its place.
    var lastIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(groups, selected) {
        if (!state.loaded) return@LaunchedEffect
        val index = groups.indexOfFirst { it.id == selected }
        if (index >= 0) {
            lastIndex = index
        } else {
            onSelect(groups.getOrNull(lastIndex.coerceAtMost(groups.lastIndex))?.id)
        }
    }

    KeyShortcuts { e ->
        if (e.type != KeyEventType.KeyDown) return@KeyShortcuts false
        val index = groups.indexOfFirst { it.id == selected }
        val step = when (e.key) {
            Key.DirectionUp, Key.PageUp -> -1
            Key.DirectionDown, Key.PageDown -> 1
            else -> return@KeyShortcuts false
        }
        groups.getOrNull(index + step)?.let { onSelect(it.id) }
        true
    }

    Row(Modifier.fillMaxSize()) {
        GroupList(groups, selected, onSelect, Modifier.width(300.dp).fillMaxHeight())
        VerticalDivider()
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val index = groups.indexOfFirst { it.id == selected }
            if (index >= 0) {
                val group = groups[index]
                key(group.id) {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides rememberDisposableStoreOwner(group.id)) {
                        DuelRoute(
                            groupId = group.id,
                            onFinished = {},
                            navigation = null,
                            title = friendlyTime(group.startTime),
                            position = "${index + 1} of ${groups.size}",
                        )
                    }
                }
            } else if (state.loaded) {
                EmptySort(showAllTime = state.showAllTime, busy = state.indexing || state.analyzing > 0, onShowAllTime = viewModel::showAllTime, onOpenLibrary = onOpenLibrary)
            }
        }
    }
}

@Composable
private fun GroupList(groups: List<PhotoGroup>, selected: Long?, onSelect: (Long) -> Unit, modifier: Modifier) {
    val list = rememberLazyListState()
    val selectedIndex = groups.indexOfFirst { it.id == selected }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex < 0) return@LaunchedEffect
        val visible = list.layoutInfo.visibleItemsInfo
        if (visible.none { it.index == selectedIndex + 1 && it.offset + it.size <= list.layoutInfo.viewportEndOffset }) {
            list.animateScrollToItem(maxOf(0, selectedIndex - 1))
        }
    }
    Column(modifier) {
        Text(
            "To do · ${groups.size} ${if (groups.size == 1) "group" else "groups"}",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(16.dp),
        )
        LazyColumn(state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
            item(key = "header") { }
            itemsIndexed(groups, key = { _, g -> g.id }) { _, group ->
                GroupRow(group, group.id == selected, onClick = { onSelect(group.id) })
            }
        }
    }
}

@Composable
private fun GroupRow(group: PhotoGroup, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).clip(RoundedCornerShape(10.dp))
            .background(bg).clickable(onClick = onClick).padding(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            group.mediaIds.take(4).forEach { id ->
                AsyncImage(
                    model = PhotoRef(id),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(62.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${group.mediaIds.size} photos · ${friendlyTime(group.startTime)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptySort(showAllTime: Boolean, busy: Boolean, onShowAllTime: () -> Unit, onOpenLibrary: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            when {
                busy -> "Looking for similar shots. Groups will show up here."
                showAllTime -> "Nothing to sort. Every group of similar shots is done."
                else -> "Nothing to sort from the last 30 days."
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (!showAllTime && !busy) Button(onClick = onShowAllTime) { Text("Include older photos") }
        OutlinedButton(onClick = onOpenLibrary) { Text("Library") }
    }
}

