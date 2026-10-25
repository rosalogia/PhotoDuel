package dev.rosalogia.photoduel.desktop.platform

import dev.rosalogia.photoduel.core.common.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** The photo folders the user chose, one absolute path per line in folders.txt. */
class FolderPrefs(private val file: File) {
    private val _folders = MutableStateFlow(load())
    val folders: StateFlow<List<File>> = _folders.asStateFlow()

    fun add(folder: File) = save((_folders.value + folder.absoluteFile).distinct())

    fun remove(folder: File) = save(_folders.value - folder)

    private fun load(): List<File> = runCatching {
        if (!file.exists()) emptyList() else file.readLines().filter { it.isNotBlank() }.map(::File)
    }.getOrElse {
        AppLog.w("Folders", "Couldn't read $file", it)
        emptyList()
    }

    @Synchronized
    private fun save(list: List<File>) {
        file.parentFile.mkdirs()
        file.writeText(list.joinToString("\n") { it.path })
        _folders.value = list
        AppLog.i("Folders", "Photo folders: ${list.joinToString()}")
    }

    companion object {
        /** Where camera photos usually land on a desktop: the Pictures folder, plus OneDrive's camera roll. */
        fun suggestions(pictures: File): List<File> {
            val home = File(System.getProperty("user.home"))
            return listOfNotNull(
                pictures,
                File(home, "OneDrive/Pictures/Camera Roll"),
                File(home, "OneDrive/Pictures"),
                File(pictures, "Camera Roll"),
                File(home, "DCIM"),
            ).filter { it.isDirectory }.distinct()
        }
    }
}
