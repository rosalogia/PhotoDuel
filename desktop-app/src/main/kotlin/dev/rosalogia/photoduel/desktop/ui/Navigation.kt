package dev.rosalogia.photoduel.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/** A screen in the window: a sidebar destination, or something opened on top of one. */
sealed interface Screen {
    data object Sort : Screen
    data object Library : Screen
    data object Settings : Screen
    data class Duel(val groupId: Long) : Screen
    data class EditGroup(val groupId: Long) : Screen
    data class Highlight(val setId: Long) : Screen
}

/** ViewModels per screen, kept while the screen is on the back stack and cleared when it leaves. */
class ScreenStores {
    private val stores = HashMap<Any, ViewModelStore>()

    fun owner(key: Any): ViewModelStoreOwner {
        val store = stores.getOrPut(key) { ViewModelStore() }
        return object : ViewModelStoreOwner {
            override val viewModelStore = store
        }
    }

    /** Clears every store whose key isn't in [keep]. */
    fun retain(keep: Set<Any>) {
        stores.keys.filter { it !in keep }.forEach { stores.remove(it)?.clear() }
    }
}

/** Gives [content] its own ViewModels and saved state under [key]. */
@Composable
fun ScreenScope(key: Any, stores: ScreenStores, saveable: SaveableStateHolder, content: @Composable () -> Unit) {
    val owner = remember(key) { stores.owner(key) }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        saveable.SaveableStateProvider(key.toString(), content)
    }
}

/** A ViewModel store that lives only while this composable does; for per-item duels. */
@Composable
fun rememberDisposableStoreOwner(key: Any): ViewModelStoreOwner {
    val owner = remember(key) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    return owner
}
