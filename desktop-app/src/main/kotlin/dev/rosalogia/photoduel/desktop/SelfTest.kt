package dev.rosalogia.photoduel.desktop

import dev.rosalogia.photoduel.core.common.AppLog
import dev.rosalogia.photoduel.core.ml.ArgbImage
import dev.rosalogia.photoduel.core.ml.OpenCv
import dev.rosalogia.photoduel.desktop.image.Decoding
import dev.rosalogia.photoduel.desktop.image.DesktopPhotoDecoder
import dev.rosalogia.photoduel.desktop.image.LibHeif
import dev.rosalogia.photoduel.desktop.ml.DesktopNatives
import dev.rosalogia.photoduel.desktop.ml.OnnxFeatureExtractor
import dev.rosalogia.photoduel.desktop.ml.YuNetFaceDetector
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfKeyPoint
import org.opencv.features2d.ORB
import java.io.File

/**
 * `PhotoDuel --self-test`: exercises each native library without opening a window and logs the
 * results, for checking a package on a new OS or a user's machine.
 */
object SelfTest {
    /** Runs every check, or only those whose names contain one of [only]. */
    fun run(only: List<String> = emptyList()): Boolean {
        DesktopNatives.init()
        OpenCv.loader = DesktopNatives::loadOpenCv
        // A fixed folder: a random temp name would seed SecureRandom, which some environments (Wine) can't.
        val tmp = File(AppDirs.cache, "selftest").apply { deleteRecursively(); mkdirs() }
        val checks = listOf<Pair<String, () -> String>>(
            "OpenCV" to {
                OpenCv.ensureLoaded()
                val m = Mat(240, 320, CvType.CV_8UC1).also { Core.randu(it, 0.0, 255.0) }
                val keys = MatOfKeyPoint().also { ORB.create(200).detect(m, it) }
                "OpenCV ${Core.VERSION}, ORB found ${keys.toArray().size} keypoints"
            },
            "Faces (YuNet)" to {
                val image = ArgbImage(320, 240, IntArray(320 * 240) { 0xFF808080.toInt() })
                val faces = runBlocking { YuNetFaceDetector(File(tmp, "models")).detect(image, 0.08f) }
                "${faces.size} faces in a blank image"
            },
            "Embeddings (ONNX Runtime)" to {
                val extractor = OnnxFeatureExtractor(DesktopPhotoDecoder({ null }, tmp))
                val bitmap = Bitmap().apply { allocPixels(ImageInfo(256, 192, ColorType.N32, ColorAlphaType.PREMUL)) }
                "embedding of size ${extractor.embed(bitmap).size}"
            },
            "HEIC (libheif)" to {
                check(LibHeif.available) { "libheif isn't available" }
                val file = File(tmp, "selftest.heic")
                javaClass.getResourceAsStream("/selftest.heic")!!.use { input -> file.outputStream().use(input::copyTo) }
                val bitmap = Decoding.decode(file, 4096)
                "decoded ${bitmap.width}x${bitmap.height}"
            },
        )
        var ok = true
        for ((name, check) in checks.filter { (n, _) -> only.isEmpty() || only.any { n.contains(it, ignoreCase = true) } }) {
            runCatching(check)
                .onSuccess { AppLog.i(TAG, "PASS $name: $it") }
                .onFailure {
                    ok = false
                    AppLog.e(TAG, "FAIL $name", it)
                }
        }
        AppLog.i(TAG, if (ok) "All checks passed" else "Some checks failed")
        tmp.deleteRecursively()
        return ok
    }

    private const val TAG = "SelfTest"
}
