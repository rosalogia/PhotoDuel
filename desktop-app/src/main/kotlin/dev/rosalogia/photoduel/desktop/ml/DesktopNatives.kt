package dev.rosalogia.photoduel.desktop.ml

import dev.rosalogia.photoduel.core.common.AppLog
import java.io.File

/**
 * The native libraries staged by the build's prepareNatives task, which the Compose plugin puts in
 * the app's resources folder (packaged or run from Gradle): OpenCV, OpenBLAS, ONNX Runtime and,
 * on Windows, libheif. Loading them from plain files avoids unpacking anything at startup.
 */
object DesktopNatives {
    val dir: File? = System.getProperty("compose.application.resources.dir")?.let(::File)?.takeIf { it.isDirectory }
    private val windows = System.getProperty("os.name").startsWith("Windows")

    /** Points ONNX Runtime and JNA (for libheif) at the folder; call before either is used. */
    fun init() {
        val d = dir ?: return AppLog.w(TAG, "No native library folder; running without packaged libraries")
        System.setProperty("onnxruntime.native.path", d.path)
        System.setProperty("jna.library.path", d.path)
        AppLog.i(TAG, "Native libraries from $d")
    }

    /** Loads OpenCV's Java bindings and everything they link, in whatever order works. */
    fun loadOpenCv(): Boolean = runCatching {
        val d = checkNotNull(dir) { "No native library folder" }
        // The Visual C++ runtime goes first, so OpenCV binds to the bundled copy, which may be newer than Java's.
        val pending = d.listFiles()!!.filter { isLibrary(it) && !it.name.contains("onnxruntime") && !isSystemProvided(it) }
            .sortedBy { if (it.name.matches(VC_RUNTIME)) 0 else 1 }
            .toMutableList()
        // Without the dependency graph, retry whatever failed until a pass loads nothing new.
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
        check(pending.none { it.name.contains("opencv_java") }) { "Couldn't load ${pending.map { it.name }}" }
        if (pending.isNotEmpty()) AppLog.d(TAG, "Not loaded (not needed by OpenCV): ${pending.map { it.name }}")
        AppLog.i(TAG, "Loaded OpenCV ${org.opencv.core.Core.VERSION}")
        true
    }.onFailure { AppLog.e(TAG, "OpenCV failed to load", it) }.getOrDefault(false)

    private fun isLibrary(f: File) = if (windows) f.name.endsWith(".dll") else f.name.matches(Regex("lib.*\\.so(\\.\\d+)*"))

    /** Every Linux system has its own C++ runtime; loading a second copy invites trouble. */
    private fun isSystemProvided(f: File) = !windows && (f.name.startsWith("libgcc_s") || f.name.startsWith("libstdc++"))

    private val VC_RUNTIME = Regex("(msvcp|vcruntime|concrt|vcomp)140.*\\.dll", RegexOption.IGNORE_CASE)
    private const val TAG = "Natives"
}
