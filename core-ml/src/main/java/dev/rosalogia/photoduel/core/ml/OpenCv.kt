package dev.rosalogia.photoduel.core.ml

import org.opencv.android.OpenCVLoader

object OpenCv {
    @Volatile private var loaded = false

    /** Loads OpenCV's native library once; safe to call from any thread. */
    fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (!loaded) {
                check(OpenCVLoader.initLocal()) { "OpenCV failed to load" }
                loaded = true
            }
        }
    }
}
