package dev.rosalogia.photoduel.feature.groups

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rosalogia.photoduel.core.data.PhotoGroup
import dev.rosalogia.photoduel.core.ui.BrandTopBar
import dev.rosalogia.photoduel.core.ui.Ink
import dev.rosalogia.photoduel.core.ui.LocalMessages
import dev.rosalogia.photoduel.core.ui.PhotoThumb
import dev.rosalogia.photoduel.core.ui.SecondaryButton
import dev.rosalogia.photoduel.core.ui.graphViewModel

@Composable
fun GroupEditRoute(groupId: Long, onClose: () -> Unit) {
    val viewModel = graphViewModel(key = "edit-$groupId") { GroupEditViewModel(groupId, it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val showMessage = LocalMessages.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            when (e) {
                is GroupEditEvent.Message -> showMessage(e.text)
                GroupEditEvent.Close -> onClose()
            }
        }
    }
    GroupEditScreen(
        state = state,
        onBack = onClose,
        onRemove = viewModel::remove,
        onSplitBefore = viewModel::splitBefore,
        onMerge = viewModel::mergeWith,
    )
}

@Composable
fun GroupEditScreen(
    state: GroupEditUiState,
    onBack: () -> Unit,
    onRemove: (Long) -> Unit,
    onSplitBefore: (Long) -> Unit,
    onMerge: (Long) -> Unit,
) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        BrandTopBar(
            title = "Edit stack",
            subtitle = state.group?.let { "${it.mediaIds.size} photos" },
            onBack = onBack,
        )
        val group = state.group
        if (group == null || state.busy) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), color = Ink.Dim, strokeWidth = 1.5.dp)
            }
            return@Column
        }
        Text(
            "Tap a photo to take it out of this stack, or to start a new stack from it.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.Dim,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 120.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f).testTag("editGrid"),
        ) {
            itemsIndexed(group.mediaIds, key = { _, id -> id }) { index, id ->
                EditablePhoto(
                    mediaId = id,
                    canSplit = index > 0,
                    onRemove = { onRemove(id) },
                    onSplit = { onSplitBefore(id) },
                )
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.previous?.let { MergeButton("Merge with the stack before", it, onMerge, "mergePrevious") }
            state.next?.let { MergeButton("Merge with the stack after", it, onMerge, "mergeNext") }
        }
    }
}

@Composable
private fun EditablePhoto(mediaId: Long, canSplit: Boolean, onRemove: () -> Unit, onSplit: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        PhotoThumb(
            mediaId,
            Modifier.fillMaxWidth().aspectRatio(1f)
                .then(if (menu) Modifier.border(2.dp, Ink.Kept, RoundedCornerShape(6.dp)) else Modifier)
                .clickable { menu = true }
                .testTag("editPhoto"),
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Ink.Raised2) {
            DropdownMenuItem(
                text = { Text("Take out of this stack") },
                onClick = {
                    menu = false
                    onRemove()
                },
                modifier = Modifier.testTag("removePhoto"),
            )
            if (canSplit) {
                DropdownMenuItem(
                    text = { Text("Start a new stack here") },
                    onClick = {
                        menu = false
                        onSplit()
                    },
                    modifier = Modifier.testTag("splitHere"),
                )
            }
        }
    }
}

@Composable
private fun MergeButton(label: String, other: PhotoGroup, onMerge: (Long) -> Unit, tag: String) {
    SecondaryButton("$label · ${other.mediaIds.size} photos", onClick = { onMerge(other.id) }, modifier = Modifier.fillMaxWidth().testTag(tag))
}
