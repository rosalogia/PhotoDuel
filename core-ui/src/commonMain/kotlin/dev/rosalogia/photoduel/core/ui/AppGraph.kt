package dev.rosalogia.photoduel.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.rosalogia.photoduel.core.data.DataGraph
import dev.rosalogia.photoduel.core.ml.FaceAnalyzer
import dev.rosalogia.photoduel.core.ml.PhotoDecoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Everything screens need, built once per process by the app (Android or desktop) and provided
 * through [LocalAppGraph].
 */
class AppGraph(
    val data: DataGraph,
    val decoder: PhotoDecoder,
    val faces: FaceAnalyzer,
    val platform: Platform,
) {
    private val singletons = ConcurrentHashMap<KClass<*>, Any>()

    /** A process-wide instance of [type], created on first use; for feature-owned caches. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> singleton(type: KClass<T>, create: () -> T): T = singletons.computeIfAbsent(type) { create() } as T
}

/** What differs between platforms in ways the shared UI cares about. */
data class Platform(
    /** True on desktop: mouse and keyboard first, wide windows. */
    val isDesktop: Boolean,
)

val LocalAppGraph = staticCompositionLocalOf<AppGraph> { error("No AppGraph provided") }

/** A ViewModel from the current ViewModelStoreOwner, built from the app graph on first use. */
@Composable
inline fun <reified VM : ViewModel> graphViewModel(key: String? = null, crossinline create: (AppGraph) -> VM): VM {
    val graph = LocalAppGraph.current
    return viewModel(key = key) { create(graph) }
}
