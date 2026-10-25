package dev.rosalogia.photoduel.core.ml

object OpenCv {
    @Volatile private var loaded = false

    /** Loads the native library and returns whether it worked. Set by each app at startup. */
    @Volatile var loader: () -> Boolean = { error("OpenCv.loader not set") }

    /** Loads OpenCV's native library once; safe to call from any thread. */
    fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (!loaded) {
                check(loader()) { "OpenCV failed to load" }
                loaded = true
            }
        }
    }
}
