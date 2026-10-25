package dev.rosalogia.photoduel.desktop.ml

import dev.rosalogia.photoduel.core.ml.ArgbImage
import dev.rosalogia.photoduel.core.ml.DetectedFace
import dev.rosalogia.photoduel.core.ml.FaceDetector
import dev.rosalogia.photoduel.core.ml.NormRect
import dev.rosalogia.photoduel.core.ml.OpenCv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.objdetect.FaceDetectorYN
import java.io.File

/**
 * Face boxes from YuNet (OpenCV's small ONNX face detector). Unlike ML Kit it doesn't say whether
 * eyes are open or the person is smiling, so those stay unknown and the pre-score leans on sharpness.
 */
class YuNetFaceDetector(private val modelDir: File) : FaceDetector {
    private val mutex = Mutex()
    private var detector: FaceDetectorYN? = null

    private fun detector(): FaceDetectorYN = detector ?: run {
        OpenCv.ensureLoaded()
        val model = File(modelDir, "yunet-2023mar.onnx")
        if (!model.exists()) {
            modelDir.mkdirs()
            javaClass.getResourceAsStream("/models/yunet.onnx")!!.use { input -> model.outputStream().use { input.copyTo(it) } }
        }
        FaceDetectorYN.create(model.path, "", Size(320.0, 320.0), SCORE_THRESHOLD, 0.3f, 50).also { detector = it }
    }

    override suspend fun detect(image: ArgbImage, minFaceSize: Float): List<DetectedFace> = withContext(Dispatchers.Default) {
        OpenCv.ensureLoaded()
        val bgr = Mat(image.height, image.width, CvType.CV_8UC3)
        val faces = Mat()
        try {
            val bytes = ByteArray(image.width * image.height * 3)
            for (i in image.pixels.indices) {
                val p = image.pixels[i]
                bytes[i * 3] = (p and 255).toByte()
                bytes[i * 3 + 1] = (p shr 8 and 255).toByte()
                bytes[i * 3 + 2] = (p shr 16 and 255).toByte()
            }
            bgr.put(0, 0, bytes)
            mutex.withLock {
                val d = detector()
                d.inputSize = Size(image.width.toDouble(), image.height.toDouble())
                d.detect(bgr, faces)
            }
            val w = image.width.toFloat()
            val h = image.height.toFloat()
            (0 until faces.rows()).mapNotNull { r ->
                val x = faces.get(r, 0)[0].toFloat()
                val y = faces.get(r, 1)[0].toFloat()
                val fw = faces.get(r, 2)[0].toFloat()
                val fh = faces.get(r, 3)[0].toFloat()
                if (fw / w < minFaceSize) return@mapNotNull null
                DetectedFace(NormRect(x / w, y / h, (x + fw) / w, (y + fh) / h).clampToUnit(), eyesOpen = null, smiling = null)
            }
        } finally {
            bgr.release()
            faces.release()
        }
    }

    private companion object {
        const val SCORE_THRESHOLD = 0.8f
    }
}
