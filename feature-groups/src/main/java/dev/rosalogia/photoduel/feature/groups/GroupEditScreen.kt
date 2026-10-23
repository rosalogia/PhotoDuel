package dev.rosalogia.photoduel.feature.groups

import android.widget.Toast
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rosalogia.photoduel.core.data.PhotoGroup

@Composable
fun GroupEditRoute(groupId: Long, onClose: () -> Unit) {
    val viewModel = hiltViewModel<GroupEditViewModel, GroupEditViewModel.Factory>(key = "edit-$groupId") { it.create(groupId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            when (e) {
                is GroupEditEvent.Message -> Toast.makeText(context, e.text, Toast.LENGTH_SHORT).show()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupEditScreen(
    state: GroupEditUiState,
    onBack: () -> Unit,
    onRemove: (Long) -> Unit,
    onSplitBefore: (Long) -> Unit,
    onMerge: (Long) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.group?.let { "Edit group · ${it.mediaIds.size} photos" } ?: "Edit group") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        val group = state.group
        if (group == null || state.busy) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                "Tap a photo to remove it or start a new group from it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
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
                state.previous?.let { MergeButton("Merge with previous group", it, onMerge, "mergePrevious") }
                state.next?.let { MergeButton("Merge with next group", it, onMerge, "mergeNext") }
            }
        }
    }
}

@Composable
private fun EditablePhoto(mediaId: Long, canSplit: Boolean, onRemove: () -> Unit, onSplit: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Thumbnail(
            mediaId,
            Modifier.fillMaxWidth().aspectRatio(1f)
                .then(if (menu) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier)
                .clickable { menu = true }
                .testTag("editPhoto"),
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Remove from group") },
                onClick = {
                    menu = false
                    onRemove()
                },
                modifier = Modifier.testTag("removePhoto"),
            )
            if (canSplit) {
                DropdownMenuItem(
                    text = { Text("Start a new group here") },
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
    OutlinedButton(onClick = { onMerge(other.id) }, modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Text("$label (${other.mediaIds.size} photos)")
    }
}
