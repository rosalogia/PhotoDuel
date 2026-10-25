package dev.rosalogia.photoduel.platform

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import dev.rosalogia.photoduel.core.ml.ArgbImage
import dev.rosalogia.photoduel.core.ml.DetectedFace
import dev.rosalogia.photoduel.core.ml.FaceDetector
import dev.rosalogia.photoduel.core.ml.NormRect
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** ML Kit face detection (bundled model) with classification, for eyes-open and smiling probabilities. */
class MlKitFaceDetector : FaceDetector {
    private var minFaceSize = -1f
    private var detector: com.google.mlkit.vision.face.FaceDetector? = null

    @Synchronized
    private fun detector(minSize: Float) = detector?.takeIf { minSize == minFaceSize } ?: FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(minSize)
            .build(),
    ).also {
        detector = it
        minFaceSize = minSize
    }

    override suspend fun detect(image: ArgbImage, minFaceSize: Float): List<DetectedFace> {
        val bitmap = image.toBitmap()
        try {
            return suspendCancellableCoroutine { cont ->
                detector(minFaceSize).process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { faces ->
                        val w = image.width.toFloat()
                        val h = image.height.toFloat()
                        cont.resume(
                            faces.map { f ->
                                val b = f.boundingBox
                                val eyes = listOfNotNull(f.leftEyeOpenProbability, f.rightEyeOpenProbability)
                                DetectedFace(
                                    box = NormRect(b.left / w, b.top / h, b.right / w, b.bottom / h).clampToUnit(),
                                    eyesOpen = if (eyes.isEmpty()) null else eyes.average().toFloat(),
                                    smiling = f.smilingProbability,
                                )
                            },
                        )
                    }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
        } finally {
            bitmap.recycle()
        }
    }
}
