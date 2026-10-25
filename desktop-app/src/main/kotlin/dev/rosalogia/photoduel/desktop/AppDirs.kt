package dev.rosalogia.photoduel.desktop

import java.io.File

/** Where the app keeps its own files, following each OS's conventions. */
object AppDirs {
    private const val NAME = "PhotoDuel"
    private val os = System.getProperty("os.name").lowercase()
    val isWindows = os.startsWith("windows")
    val isMac = os.startsWith("mac")
    private val home = File(System.getProperty("user.home"))

    /** For testing: -Dphotoduel.<name> or PHOTODUEL_<NAME> replaces a directory. */
    private fun override(name: String): File? =
        (System.getProperty("photoduel.$name") ?: System.getenv("PHOTODUEL_${name.uppercase()}"))?.takeIf { it.isNotBlank() }?.let(::File)

    /** Database, settings and logs. */
    val data: File by lazy {
        override("dataDir")?.let { return@lazy it.apply { mkdirs() } }
        when {
            isWindows -> File(System.getenv("APPDATA") ?: "$home/AppData/Roaming", NAME)
            isMac -> File(home, "Library/Application Support/$NAME")
            else -> File(System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share", NAME)
        }.apply { mkdirs() }
    }

    /** Thumbnails; safe to delete. */
    val cache: File by lazy {
        override("dataDir")?.let { return@lazy File(it, "cache").apply { mkdirs() } }
        when {
            isWindows -> File(System.getenv("LOCALAPPDATA") ?: "$home/AppData/Local", "$NAME/Cache")
            isMac -> File(home, "Library/Caches/$NAME")
            else -> File(System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.cache", NAME)
        }.apply { mkdirs() }
    }

    /** The user's Pictures folder, where highlight albums go. */
    val pictures: File by lazy {
        override("picturesDir")?.let { return@lazy it }
        val xdg = if (!isWindows && !isMac) xdgUserDir("PICTURES") else null
        xdg ?: File(home, "Pictures")
    }

    /** Reads ~/.config/user-dirs.dirs, which localizes folder names like "Bilder". */
    private fun xdgUserDir(key: String): File? = runCatching {
        File(home, ".config/user-dirs.dirs").readLines()
            .firstOrNull { it.startsWith("XDG_${key}_DIR=") }
            ?.substringAfter('=')?.trim('"')?.replace("\$HOME", home.path)
            ?.let(::File)
    }.getOrNull()
}
