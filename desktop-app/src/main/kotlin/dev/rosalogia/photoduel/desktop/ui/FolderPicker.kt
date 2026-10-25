package dev.rosalogia.photoduel.desktop.ui

import dev.rosalogia.photoduel.desktop.AppDirs
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager

/** Asks for a folder with the most native dialog available. Blocking; call off the UI thread. */
object FolderPicker {
    fun pick(start: File?): File? {
        if (!AppDirs.isWindows && !AppDirs.isMac) {
            linuxDialog(start)?.let { return it.getOrNull() }
        }
        return swing(start)
    }

    /** zenity (GNOME) or kdialog (KDE); null if neither exists, a failed result if the user cancelled. */
    private fun linuxDialog(start: File?): Result<File>? {
        val dir = (start ?: File(System.getProperty("user.home"))).path + "/"
        val commands = listOf(
            listOf("zenity", "--file-selection", "--directory", "--title=Choose a photo folder", "--filename=$dir"),
            listOf("kdialog", "--getexistingdirectory", dir, "--title", "Choose a photo folder"),
        )
        for (command in commands) {
            val process = runCatching { ProcessBuilder(command).start() }.getOrNull() ?: continue
            val out = process.inputStream.bufferedReader().readText().trim()
            return if (process.waitFor() == 0 && out.isNotEmpty()) Result.success(File(out)) else Result.failure(Exception("cancelled"))
        }
        return null
    }

    private fun swing(start: File?): File? {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        val chooser = JFileChooser(start).apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Choose a photo folder"
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }
}
