package dev.rosalogia.photoduel.feature.duel.align

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.rosalogia.photoduel.core.common.AppLog
import dev.rosalogia.photoduel.core.data.media.mediaContentUri
import dev.rosalogia.photoduel.core.ml.OpenCv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.features2d.BFMatcher
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

/**
 * A 3x3 transform (currently always a similarity, but stored as a homography), row-major, mapping the challenger's normalized coordinates (0..1 across its
 * width and height) to the king's normalized coordinates. Normalizing makes it independent of the
 * resolution either photo is decoded at.
 */
class Alignment(val h: DoubleArray, val inliers: Int, val matches: Int) {
    init {
        require(h.size == 9)
    }

    fun map(u: Double, v: Double): Pair<Double, Double> {
        val w = h[6] * u + h[7] * v + h[8]
        return (h[0] * u + h[1] * v + h[2]) / w to (h[3] * u + h[4] * v + h[5]) / w
    }

    override fun toString() = "Alignment(inliers=$inliers/$matches)"
}

/**
 * Registers a challenger to the king so burst shots don't jump around when flipped. Uses ORB
 * features on downscaled grayscale copies, a ratio-tested Hamming match and a RANSAC similarity fit.
 * Only accepts transforms that look like handheld drift (small shift/rotation/zoom); anything
 * else returns null and the photo is shown unaligned, which beats a wrong warp.
 */
@Singleton
class Aligner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val grayCache = LruCache<Long, Mat>(GRAY_CACHE_ENTRIES)
    private val resultCache = LruCache<Pair<Long, Long>, Result>(RESULT_CACHE_ENTRIES)
    private val mutex = Mutex()

    /** Wraps a nullable result so "tried and failed" is cached too. */
    private class Result(val alignment: Alignment?)

    suspend fun align(king: Long, challenger: Long): Alignment? = withContext(Dispatchers.Default) {
        resultCache.get(king to challenger)?.let { return@withContext it.alignment }
        // OpenCV calls are CPU-bound and allocate a lot; one at a time.
        mutex.withLock {
            resultCache.get(king to challenger)?.let { return@withLock it.alignment }
            val result = try {
                var a: Alignment? = null
                val ms = measureTimeMillis { a = alignGray(gray(king), gray(challenger)) }
                AppLog.d(TAG, "Align $challenger -> $king: ${a ?: "rejected"} in ${ms}ms")
                a
            } catch (e: Exception) {
                AppLog.e(TAG, "Alignment of $challenger to $king failed", e)
                null
            }
            resultCache.put(king to challenger, Result(result))
            result
        }
    }

    /** For tests and callers that already hold bitmaps. */
    fun alignBitmaps(king: Bitmap, challenger: Bitmap): Alignment? {
        OpenCv.ensureLoaded()
        val k = toGray(king)
        val c = toGray(challenger)
        return try {
            alignGray(k, c)
        } finally {
            k.release()
            c.release()
        }
    }

    private fun gray(mediaId: Long): Mat {
        grayCache.get(mediaId)?.let { return it }
        OpenCv.ensureLoaded()
        val source = ImageDecoder.createSource(context.contentResolver, mediaContentUri(mediaId))
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longEdge = max(info.size.width, info.size.height)
            if (longEdge > WORK_EDGE) {
                val f = WORK_EDGE.toFloat() / longEdge
                decoder.setTargetSize((info.size.width * f).roundToInt(), (info.size.height * f).roundToInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        return toGray(bitmap).also {
            bitmap.recycle()
            grayCache.put(mediaId, it)
        }
    }

    private fun toGray(bitmap: Bitmap): Mat {
        val src = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val longEdge = max(src.width, src.height)
        val scaled = if (longEdge > WORK_EDGE) {
            val f = WORK_EDGE.toFloat() / longEdge
            Bitmap.createScaledBitmap(src, (src.width * f).roundToInt(), (src.height * f).roundToInt(), true)
        } else {
            src
        }
        val rgba = Mat()
        Utils.bitmapToMat(scaled, rgba)
        val gray = Mat()
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        rgba.release()
        return gray
    }

    private fun alignGray(king: Mat, challenger: Mat): Alignment? {
        val orb = ORB.create(MAX_FEATURES)
        val kpK = MatOfKeyPoint()
        val kpC = MatOfKeyPoint()
        val descK = Mat()
        val descC = Mat()
        val noMask = Mat()
        try {
            orb.detectAndCompute(king, noMask, kpK, descK)
            orb.detectAndCompute(challenger, noMask, kpC, descC)
            if (descK.empty() || descC.empty()) return null

            val knn = ArrayList<MatOfDMatch>()
            BFMatcher.create(Core.NORM_HAMMING, false).knnMatch(descC, descK, knn, 2)
            val keysK = kpK.toArray()
            val keysC = kpC.toArray()
            val srcPts = ArrayList<Point>()
            val dstPts = ArrayList<Point>()
            for (m in knn) {
                val pair = m.toArray()
                m.release()
                if (pair.size < 2 || pair[0].distance >= RATIO * pair[1].distance) continue
                srcPts += keysC[pair[0].queryIdx].pt
                dstPts += keysK[pair[0].trainIdx].pt
            }
            if (srcPts.size < MIN_MATCHES) return null

            val src = MatOfPoint2f(*srcPts.toTypedArray())
            val dst = MatOfPoint2f(*dstPts.toTypedArray())
            val inlierMask = Mat()
            // Shift + roll + zoom (4 DOF). A full homography overfits when features cluster on the
            // subject (portraits), and handheld burst drift is almost entirely these three motions.
            val affine = Calib3d.estimateAffinePartial2D(
                src, dst, inlierMask, Calib3d.RANSAC, RANSAC_THRESHOLD_PX, 2000, 0.995, 10,
            )
            src.release()
            dst.release()
            if (affine.empty()) return null
            val inliers = Core.countNonZero(inlierMask)
            inlierMask.release()
            if (inliers < MIN_INLIERS || inliers < MIN_INLIER_RATIO * srcPts.size) return null

            val small = DoubleArray(9).also {
                affine.get(0, 0, it)
                it[6] = 0.0
                it[7] = 0.0
                it[8] = 1.0
            }
            affine.release()
            val normalized = normalize(small, challenger.cols(), challenger.rows(), king.cols(), king.rows())
            val alignment = Alignment(normalized, inliers, srcPts.size)
            return if (looksLikeHandheldDrift(alignment)) alignment else null
        } finally {
            kpK.release()
            kpC.release()
            descK.release()
            descC.release()
            noMask.release()
        }
    }

    private companion object {
        const val TAG = "Aligner"
        const val WORK_EDGE = 1024
        const val MAX_FEATURES = 2000
        const val RATIO = 0.75f
        const val MIN_MATCHES = 20
        const val MIN_INLIERS = 15
        const val MIN_INLIER_RATIO = 0.25
        const val RANSAC_THRESHOLD_PX = 3.0
        const val GRAY_CACHE_ENTRIES = 6
        const val RESULT_CACHE_ENTRIES = 32
        /** Corner displacement limit, as a fraction of the normalized image diagonal. */
        const val MAX_CORNER_SHIFT = 0.12

        /** H_norm = diag(1/Wk, 1/Hk, 1) · H · diag(Wc, Hc, 1). */
        fun normalize(h: DoubleArray, wc: Int, hc: Int, wk: Int, hk: Int): DoubleArray {
            val r = DoubleArray(9)
            val sc = doubleArrayOf(wc.toDouble(), hc.toDouble(), 1.0)
            val sk = doubleArrayOf(1.0 / wk, 1.0 / hk, 1.0)
            for (i in 0 until 3) for (j in 0 until 3) r[i * 3 + j] = sk[i] * h[i * 3 + j] * sc[j]
            return r
        }

        /** True if the challenger's corners each move only a little: drift, not a different framing. */
        fun looksLikeHandheldDrift(a: Alignment): Boolean {
            val diagonal = hypot(1.0, 1.0)
            val corners = listOf(0.0 to 0.0, 1.0 to 0.0, 1.0 to 1.0, 0.0 to 1.0)
            return corners.all { (u, v) ->
                val (x, y) = a.map(u, v)
                x.isFinite() && y.isFinite() && hypot(x - u, y - v) <= MAX_CORNER_SHIFT * diagonal
            }
        }
    }
}
