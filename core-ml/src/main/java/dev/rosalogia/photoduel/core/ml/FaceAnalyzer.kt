package dev.rosalogia.photoduel.core.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.RectF
import android.util.LruCache
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.rosalogia.photoduel.core.common.AppLog
import dev.rosalogia.photoduel.core.data.index.PhotoQualityScorer
import dev.rosalogia.photoduel.core.data.media.mediaContentUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt

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
    val box: RectF,
    val eyesOpen: Float?,
    val smiling: Float?,
)

data class PhotoQuality(
    val faces: List<DetectedFace>,
    /** Variance of the Laplacian over the face regions, or the whole frame without faces. */
    val laplacianVariance: Double,
    val preScore: Float,
) {
    val largestFace: DetectedFace? get() = faces.maxByOrNull { it.box.width() * it.box.height() }
}

/**
 * Face detection (ML Kit, bundled model, classification on) plus a sharpness measure (OpenCV),
 * combined into the heuristic pre-score that orders a duel.
 */
@Singleton
class FaceAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
) : PhotoQualityScorer {
    private val weights = PreScoreWeights()
    private val cache = LruCache<Long, PhotoQuality>(CACHE_ENTRIES)
    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(MIN_FACE_SIZE)
                .build(),
        )
    }

    override suspend fun preScore(mediaId: Long): Float = analyze(mediaId).preScore

    suspend fun analyze(mediaId: Long): PhotoQuality {
        cache.get(mediaId)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) { decode(mediaId) }
        try {
            val faces = detectFaces(bitmap)
            val sharpness = withContext(Dispatchers.Default) { laplacianVariance(bitmap, faces) }
            val quality = PhotoQuality(faces, sharpness, combine(faces, sharpness))
            AppLog.d(
                TAG,
                "$mediaId: ${faces.size} faces, eyes=${faces.mapNotNull { it.eyesOpen }.average().fmt()} " +
                    "smile=${faces.mapNotNull { it.smiling }.average().fmt()} lapVar=${"%.0f".format(sharpness)} " +
                    "score=${"%.3f".format(quality.preScore)}",
            )
            cache.put(mediaId, quality)
            return quality
        } finally {
            bitmap.recycle()
        }
    }

    private fun decode(mediaId: Long): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, mediaContentUri(mediaId))
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longEdge = max(info.size.width, info.size.height)
            if (longEdge > WORK_EDGE) {
                val f = WORK_EDGE.toFloat() / longEdge
                decoder.setTargetSize((info.size.width * f).roundToInt(), (info.size.height * f).roundToInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }.let { if (it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, false) }
    }

    private suspend fun detectFaces(bitmap: Bitmap): List<DetectedFace> = suspendCancellableCoroutine { cont ->
        detector.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { faces ->
                val w = bitmap.width.toFloat()
                val h = bitmap.height.toFloat()
                cont.resume(
                    faces.map { f ->
                        val b = f.boundingBox
                        val eyes = listOfNotNull(f.leftEyeOpenProbability, f.rightEyeOpenProbability)
                        DetectedFace(
                            box = RectF(b.left / w, b.top / h, b.right / w, b.bottom / h).apply { intersect(0f, 0f, 1f, 1f) },
                            eyesOpen = if (eyes.isEmpty()) null else eyes.average().toFloat(),
                            smiling = f.smilingProbability,
                        )
                    },
                )
            }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    private fun laplacianVariance(bitmap: Bitmap, faces: List<DetectedFace>): Double {
        OpenCv.ensureLoaded()
        val rgba = Mat()
        val gray = Mat()
        val lap = Mat()
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.Laplacian(gray, lap, CvType.CV_64F)
            val regions = faces.map { it.box }.ifEmpty { listOf(RectF(0f, 0f, 1f, 1f)) }
            return regions.map { r ->
                val x = (r.left * lap.cols()).toInt().coerceIn(0, lap.cols() - 1)
                val y = (r.top * lap.rows()).toInt().coerceIn(0, lap.rows() - 1)
                val rect = Rect(
                    x,
                    y,
                    (r.width() * lap.cols()).toInt().coerceIn(1, lap.cols() - x),
                    (r.height() * lap.rows()).toInt().coerceIn(1, lap.rows() - y),
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
            rgba.release()
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

@Module
@InstallIn(SingletonComponent::class)
abstract class QualityModule {
    @Binds
    abstract fun qualityScorer(impl: FaceAnalyzer): PhotoQualityScorer
}
