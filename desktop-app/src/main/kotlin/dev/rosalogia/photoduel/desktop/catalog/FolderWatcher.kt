package dev.rosalogia.photoduel.desktop.catalog

import dev.rosalogia.photoduel.core.common.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.File
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.util.concurrent.TimeUnit
import kotlin.io.path.isDirectory

/** Emits when anything changes under the chosen folders, watching each subfolder (Java's watcher isn't recursive). */
class FolderWatcher {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun changes(folders: StateFlow<List<File>>): Flow<Unit> =
        folders.flatMapLatest { roots -> watch(roots.filter { it.isDirectory }.map { it.toPath() }) }

    private fun watch(roots: List<Path>): Flow<Unit> = callbackFlow {
        if (roots.isEmpty()) {
            awaitClose { }
            return@callbackFlow
        }
        val service = FileSystems.getDefault().newWatchService()
        fun register(dir: Path) {
            runCatching {
                Files.walk(dir).use { s ->
                    s.filter { it.isDirectory() && !it.fileName.toString().startsWith(".") }
                        .forEach { it.register(service, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY) }
                }
            }.onFailure { AppLog.w("Watcher", "Can't watch $dir", it) }
        }
        roots.forEach(::register)
        val thread = Thread({
            try {
                while (isActive) {
                    val key = service.poll(1, TimeUnit.SECONDS) ?: continue
                    val dir = key.watchable() as Path
                    for (event in key.pollEvents()) {
                        val child = (event.context() as? Path)?.let(dir::resolve)
                        if (event.kind() == ENTRY_CREATE && child != null && child.isDirectory()) register(child)
                    }
                    key.reset()
                    trySend(Unit)
                }
            } catch (_: ClosedWatchServiceException) {
            } catch (_: InterruptedException) {
            }
        }, "FolderWatcher").apply { isDaemon = true; start() }
        awaitClose {
            service.close()
            thread.interrupt()
        }
    }.flowOn(Dispatchers.IO)
}
