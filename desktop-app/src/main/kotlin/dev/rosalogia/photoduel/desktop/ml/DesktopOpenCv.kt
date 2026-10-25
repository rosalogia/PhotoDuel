package dev.rosalogia.photoduel.desktop.ml

import dev.rosalogia.photoduel.core.common.AppLog
import org.bytedeco.javacpp.Loader
import org.bytedeco.opencv.opencv_java
import java.io.File

/**
 * Loads OpenCV's Java bindings from JavaCPP's build. JavaCPP would normally load every module,
 * including highgui, which needs GTK 2 on Linux; the bindings don't use it, so it's skipped.
 */
object DesktopOpenCv {
    fun load(): Boolean = runCatching {
        Loader.load(org.bytedeco.openblas.global.openblas_nolapack::class.java)
        val dir = File(Loader.cacheResource(opencv_java::class.java, Loader.getPlatform()).toURI())
        val pending = dir.listFiles()!!.filter { f ->
            val n = f.name
            !n.contains("jni") && !n.contains("highgui") && n.contains("opencv_") &&
                (n.endsWith(".dll") || n.matches(Regex(".*\\.so(\\.\\d+)*")) || n.endsWith(".dylib"))
        }.toMutableList()
        // Load in dependency order without knowing it: retry whatever failed until nothing changes.
        var progress = true
        while (pending.isNotEmpty() && progress) {
            progress = false
            for (f in pending.toList()) {
                if (runCatching { System.load(f.absolutePath) }.isSuccess) {
                    pending.remove(f)
                    progress = true
                }
            }
        }
        check(pending.isEmpty()) { "Couldn't load ${pending.map { it.name }}" }
        AppLog.i("OpenCV", "Loaded OpenCV ${org.opencv.core.Core.VERSION}")
        true
    }.onFailure { AppLog.e("OpenCV", "OpenCV failed to load", it) }.getOrDefault(false)
}
