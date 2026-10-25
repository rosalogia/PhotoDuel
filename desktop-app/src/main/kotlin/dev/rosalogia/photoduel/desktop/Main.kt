package dev.rosalogia.photoduel.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.rosalogia.photoduel.core.common.AppLog
import dev.rosalogia.photoduel.core.ui.ShortcutDispatcher
import dev.rosalogia.photoduel.desktop.ui.App
import java.awt.Dimension
import java.io.File

const val VERSION = "0.3.0"

fun main() {
    AppLog.init(File(AppDirs.data, "logs"))
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, t ->
        AppLog.recordCrash(thread, t)
        previous?.uncaughtException(thread, t)
    }
    AppLog.i("App", "Start desktop $VERSION on ${System.getProperty("os.name")} ${System.getProperty("os.version")}, Java ${System.getProperty("java.version")}")
    val app = DesktopApp.create()
    app.start()
    val shortcuts = ShortcutDispatcher()
    application {
        val state = rememberWindowState(size = DpSize(1280.dp, 860.dp))
        // Keys a focused text field didn't use go to whichever screens registered shortcuts.
        Window(onCloseRequest = ::exitApplication, title = "Photo Duel", state = state, onKeyEvent = shortcuts::dispatch) {
            window.minimumSize = Dimension(900, 600)
            App(app, shortcuts)
        }
    }
}
