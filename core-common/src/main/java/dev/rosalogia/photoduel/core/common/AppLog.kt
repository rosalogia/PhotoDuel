package dev.rosalogia.photoduel.core.common

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * App-wide logger. Everything goes to logcat, and is also appended to a rolling file in the
 * app's private storage so logs can be shared from a phone that isn't attached to adb.
 */
object AppLog {
    private const val MAX_FILE_BYTES = 512 * 1024L

    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "AppLog").apply { isDaemon = true } }
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile private var logDir: File? = null

    val currentFile: File? get() = logDir?.let { File(it, "app.log") }
    private val previousFile: File? get() = logDir?.let { File(it, "app.log.1") }
    val crashFile: File? get() = logDir?.let { File(it, "last-crash.txt") }

    fun init(context: Context) {
        logDir = File(context.filesDir, "logs").apply { mkdirs() }
    }

    fun d(tag: String, msg: String) = log(Log.DEBUG, tag, msg, null)
    fun i(tag: String, msg: String) = log(Log.INFO, tag, msg, null)
    fun w(tag: String, msg: String, t: Throwable? = null) = log(Log.WARN, tag, msg, t)
    fun e(tag: String, msg: String, t: Throwable? = null) = log(Log.ERROR, tag, msg, t)

    private fun log(priority: Int, tag: String, msg: String, t: Throwable?) {
        val full = if (t != null) "$msg\n${stackTrace(t)}" else msg
        Log.println(priority, "PD/$tag", full)
        val file = currentFile ?: return
        val line = "${timeFormat.format(Date())} ${"VDIWE"[(priority - 2).coerceIn(0, 4)]}/$tag: $full\n"
        writer.execute {
            runCatching {
                if (file.length() > MAX_FILE_BYTES) {
                    previousFile?.let { prev -> prev.delete(); file.renameTo(prev) }
                }
                file.appendText(line)
            }
        }
    }

    /** Both log files, oldest first, as one string. */
    fun readAll(): String = buildString {
        listOfNotNull(previousFile, currentFile).filter { it.exists() }.forEach { append(it.readText()) }
    }

    /** Writes the crash synchronously: the process is about to die, so the executor can't be trusted. */
    fun recordCrash(thread: Thread, t: Throwable) {
        Log.e("PD/Crash", "Uncaught exception on ${thread.name}", t)
        val text = "Crash at ${timeFormat.format(Date())} on thread ${thread.name}\n${stackTrace(t)}"
        runCatching { crashFile?.writeText(text) }
        runCatching { currentFile?.appendText("${timeFormat.format(Date())} E/Crash: $text\n") }
    }

    fun stackTrace(t: Throwable): String = StringWriter().also { t.printStackTrace(PrintWriter(it)) }.toString()
}
