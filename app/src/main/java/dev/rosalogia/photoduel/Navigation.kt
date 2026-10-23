package dev.rosalogia.photoduel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.rosalogia.photoduel.debug.DebugLogScreen
import dev.rosalogia.photoduel.feature.groups.GroupsRoute
import dev.rosalogia.photoduel.permissions.PartialAccessBanner
import kotlinx.serialization.Serializable

@Serializable data object GroupsKey : NavKey
@Serializable data class DuelKey(val groupId: Long) : NavKey
@Serializable data object LogsKey : NavKey

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
                    topBarActions = {
                        if (BuildConfig.DEBUG) {
                            TextButton(onClick = { backStack.add(LogsKey) }, modifier = Modifier.testTag("openLogs")) { Text("Logs") }
                        }
                    },
                    banner = { if (partialAccess) PartialAccessBanner(onGrantMore = onRequestMoreAccess) },
                )
            }
            entry<DuelKey> { key ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Duel for group ${key.groupId} (M2)") }
            }
            entry<LogsKey> { DebugLogScreen() }
        },
    )
}
