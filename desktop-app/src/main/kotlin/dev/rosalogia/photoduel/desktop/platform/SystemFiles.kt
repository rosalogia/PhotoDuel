package dev.rosalogia.photoduel.desktop.platform

import dev.rosalogia.photoduel.core.common.AppLog
import dev.rosalogia.photoduel.desktop.AppDirs
import java.awt.Desktop
import java.io.File
import java.net.URLEncoder
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Trash, reveal and open, through whatever each OS offers. */
object SystemFiles {
    private val desktop: Desktop? = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null

    /** Moves [file] to the Recycle Bin / Trash, so it can be restored. */
    fun trash(file: File) {
        if (desktop?.isSupported(Desktop.Action.MOVE_TO_TRASH) == true) {
            check(desktop.moveToTrash(file)) { "Couldn't move $file to the trash" }
            return
        }
        if (!AppDirs.isWindows && !AppDirs.isMac) {
            if (run("gio", "trash", file.path)) return
            freedesktopTrash(file)
            return
        }
        error("No trash available for $file")
    }

    /** Opens the file manager with [file] selected, or at least its folder. */
    fun reveal(file: File) {
        val ok = when {
            AppDirs.isWindows -> run("explorer.exe", "/select,${file.path}")
            AppDirs.isMac -> run("open", "-R", file.path)
            else -> run(
                "dbus-send", "--session", "--dest=org.freedesktop.FileManager1", "--type=method_call",
                "/org/freedesktop/FileManager1", "org.freedesktop.FileManager1.ShowItems",
                "array:string:${file.toURI()}", "string:",
            )
        }
        if (!ok) open(file.parentFile)
    }

    /** Opens [file] in its default app (the system photo viewer for a photo). */
    fun open(file: File) {
        if (desktop?.isSupported(Desktop.Action.OPEN) == true) {
            runCatching { desktop!!.open(file) }.onSuccess { return }.onFailure { AppLog.w(TAG, "Desktop.open failed", it) }
        }
        if (!AppDirs.isWindows && !AppDirs.isMac) run("xdg-open", file.path)
    }

    /** The freedesktop.org trash spec, for the home trash; enough when gio isn't installed. */
    private fun freedesktopTrash(file: File) {
        val dataHome = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "${System.getProperty("user.home")}/.local/share"
        val trash = File(dataHome, "Trash")
        val files = File(trash, "files").apply { mkdirs() }
        val info = File(trash, "info").apply { mkdirs() }
        var name = file.name
        var n = 2
        while (File(files, name).exists() || File(info, "$name.trashinfo").exists()) name = "${file.nameWithoutExtension}.$n.${file.extension}".also { n++ }
        File(info, "$name.trashinfo").writeText(
            "[Trash Info]\nPath=${URLEncoder.encode(file.absolutePath, "UTF-8").replace("%2F", "/").replace("+", "%20")}\n" +
                "DeletionDate=${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).take(19)}\n",
        )
        Files.move(file.toPath(), File(files, name).toPath())
    }

    private fun run(vararg command: String): Boolean = runCatching {
        val p = ProcessBuilder(*command).redirectErrorStream(true).start()
        p.inputStream.readAllBytes()
        // explorer.exe returns 1 even when it worked.
        p.waitFor() == 0 || command.first() == "explorer.exe"
    }.onFailure { AppLog.d(TAG, "${command.first()} unavailable: ${it.message}") }.getOrDefault(false)

    private const val TAG = "SystemFiles"
}
