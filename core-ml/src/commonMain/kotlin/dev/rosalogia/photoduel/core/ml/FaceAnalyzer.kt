package dev.rosalogia.photoduel.core.ml

import dev.rosalogia.photoduel.core.common.AppLog
import dev.rosalogia.photoduel.core.common.LruCache
import dev.rosalogia.photoduel.core.data.index.PhotoQualityScorer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import kotlin.math.log10

/** All the pre-score tuning knobs in one place. */
data class PreScoreWeights(
    val eyesOpen: Float = 0.4f,
    val smiling: Float = 0.2f,
    val sharpness: Float = 0.4f,
    /** Used for a photo with no detected faces, so faceless shots aren't punished or favored. */
    val neutralFaceScore: Float = 0.5f,
    /** log10(Laplacian variance) that maps to sharpness 0 and 1 respectively. */
    val sharpnessLogLow: Float = 1.0f,
    val sharpnessLogHigh: Float = 3.0f,
)

data class DetectedFace(
    /** Bounding box in normalized image coordinates (0..1). */
    val box: NormRect,
    /** 0..1, or null if the detector can't tell. */
    val eyesOpen: Float?,
    val smiling: Float?,
)

/** Finds faces: ML Kit on Android, an ONNX model on desktop. */
interface FaceDetector {
    /** Faces at least [minFaceSize] (fraction of the image's width) wide. */
    suspend fun detect(image: ArgbImage, minFaceSize: Float): List<DetectedFace>
}

data class PhotoQuality(
    val faces: List<DetectedFace>,
    /** Variance of the Laplacian over the face regions, or the whole frame without faces. */
    val laplacianVariance: Double,
    val preScore: Float,
) {
    val largestFace: DetectedFace? get() = faces.maxByOrNull { it.box.width * it.box.height }
}

/**
 * Face detection plus a sharpness measure (OpenCV), combined into the heuristic pre-score that
 * orders a duel.
 */
class FaceAnalyzer(
    private val decoder: PhotoDecoder,
    private val detector: FaceDetector,
) : PhotoQualityScorer {
    private val weights = PreScoreWeights()
    private val cache = LruCache<Long, PhotoQuality>(CACHE_ENTRIES)

    override suspend fun preScore(mediaId: Long): Float = analyze(mediaId).preScore

    suspend fun analyze(mediaId: Long): PhotoQuality {
        cache.get(mediaId)?.let { return it }
        val image = withContext(Dispatchers.IO) { decoder.decodeArgb(mediaId, WORK_EDGE) }
        val faces = detector.detect(image, MIN_FACE_SIZE)
        val sharpness = withContext(Dispatchers.Default) { laplacianVariance(image, faces) }
        val quality = PhotoQuality(faces, sharpness, combine(faces, sharpness))
        AppLog.d(
            TAG,
            "$mediaId: ${faces.size} faces, eyes=${faces.mapNotNull { it.eyesOpen }.average().fmt()} " +
                "smile=${faces.mapNotNull { it.smiling }.average().fmt()} lapVar=${"%.0f".format(sharpness)} " +
                "score=${"%.3f".format(quality.preScore)}",
        )
        cache.put(mediaId, quality)
        return quality
    }

    private fun laplacianVariance(image: ArgbImage, faces: List<DetectedFace>): Double {
        val gray = image.toGrayMat()
        val lap = Mat()
        try {
            Imgproc.Laplacian(gray, lap, CvType.CV_64F)
            val regions = faces.map { it.box }.ifEmpty { listOf(NormRect(0f, 0f, 1f, 1f)) }
            return regions.map { r ->
                val x = (r.left * lap.cols()).toInt().coerceIn(0, lap.cols() - 1)
                val y = (r.top * lap.rows()).toInt().coerceIn(0, lap.rows() - 1)
                val rect = Rect(
                    x,
                    y,
                    (r.width * lap.cols()).toInt().coerceIn(1, lap.cols() - x),
                    (r.height * lap.rows()).toInt().coerceIn(1, lap.rows() - y),
                )
                val roi = lap.submat(rect)
                val mean = MatOfDouble()
                val std = MatOfDouble()
                Core.meanStdDev(roi, mean, std)
                val sd = std.toArray().first()
                roi.release()
                mean.release()
                std.release()
                sd * sd
            }.average()
        } finally {
            gray.release()
            lap.release()
        }
    }

    private fun combine(faces: List<DetectedFace>, laplacianVariance: Double): Float {
        val w = weights
        val eyes = faces.mapNotNull { it.eyesOpen }.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: w.neutralFaceScore
        val smile = faces.mapNotNull { it.smiling }.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: w.neutralFaceScore
        val sharp = ((log10(laplacianVariance.coerceAtLeast(1.0)).toFloat() - w.sharpnessLogLow) /
            (w.sharpnessLogHigh - w.sharpnessLogLow)).coerceIn(0f, 1f)
        return (w.eyesOpen * eyes + w.smiling * smile + w.sharpness * sharp) / (w.eyesOpen + w.smiling + w.sharpness)
    }

    private fun Double.fmt() = if (isNaN()) "-" else "%.2f".format(this)

    private companion object {
        const val TAG = "Faces"
        const val WORK_EDGE = 1024
        const val MIN_FACE_SIZE = 0.08f
        const val CACHE_ENTRIES = 64
    }
}
