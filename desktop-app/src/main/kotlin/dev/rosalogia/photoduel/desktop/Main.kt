@file:Suppress("DEPRECATION") // painterResource(String): Compose resources would be overkill for one icon.

package dev.rosalogia.photoduel.desktop

import androidx.compose.ui.res.painterResource
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
import kotlin.system.exitProcess

/** Set by the packaged app's launcher; "dev" when run from Gradle. */
val VERSION: String = System.getProperty("jpackage.app-version") ?: "dev"

fun main(args: Array<String>) {
    AppLog.init(File(AppDirs.data, "logs"))
    if (args.firstOrNull() == "--self-test") {
        // Returning beats System.exit on success: jpackage's Linux launcher aborts noisily after exit().
        if (!SelfTest.run(only = args.drop(1))) exitProcess(1)
        return
    }
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
        Window(
            onCloseRequest = ::exitApplication,
            title = "Photo Duel",
            state = state,
            icon = painterResource("icon.png"),
            onKeyEvent = shortcuts::dispatch,
        ) {
            window.minimumSize = Dimension(900, 600)
            App(app, shortcuts)
        }
    }
}
