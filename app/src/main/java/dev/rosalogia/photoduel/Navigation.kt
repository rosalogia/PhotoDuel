package dev.rosalogia.photoduel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.rosalogia.photoduel.debug.DebugLogScreen
import dev.rosalogia.photoduel.feature.duel.DuelRoute
import dev.rosalogia.photoduel.feature.groups.GroupEditRoute
import dev.rosalogia.photoduel.feature.groups.GroupsRoute
import dev.rosalogia.photoduel.permissions.PartialAccessBanner
import dev.rosalogia.photoduel.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable data object GroupsKey : NavKey
@Serializable data class DuelKey(val groupId: Long) : NavKey
@Serializable data class EditGroupKey(val groupId: Long) : NavKey
@Serializable data object SettingsKey : NavKey
@Serializable data object LogsKey : NavKey

/** Pops [key] only if it's on top, so a screen asking to close twice can't pop its parent too. */
private fun MutableList<NavKey>.pop(key: NavKey) {
    if (lastOrNull() == key) removeAt(lastIndex)
}

@Composable
fun MainNavigation(partialAccess: Boolean, onRequestMoreAccess: () -> Unit) {
    val backStack = rememberNavBackStack(GroupsKey)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<GroupsKey> {
                GroupsRoute(
                    onOpenGroup = { backStack.add(DuelKey(it)) },
                    onEditGroup = { backStack.add(EditGroupKey(it)) },
                    topBarActions = {
                        IconButton(onClick = { backStack.add(SettingsKey) }, modifier = Modifier.testTag("openSettings")) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    },
                    banner = { if (partialAccess) PartialAccessBanner(onGrantMore = onRequestMoreAccess) },
                )
            }
            entry<DuelKey> { key ->
                DuelRoute(groupId = key.groupId, onClose = { backStack.pop(key) })
            }
            entry<EditGroupKey> { key ->
                GroupEditRoute(groupId = key.groupId, onClose = { backStack.pop(key) })
            }
            entry<SettingsKey> {
                SettingsScreen(onBack = { backStack.pop(SettingsKey) }, onOpenLogs = { backStack.add(LogsKey) })
            }
            entry<LogsKey> { DebugLogScreen() }
        },
    )
}
