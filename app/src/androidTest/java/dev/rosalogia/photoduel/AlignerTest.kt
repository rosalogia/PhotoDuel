package dev.rosalogia.photoduel.feature.duel.align

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.rosalogia.photoduel.core.ml.PhotoDecoder
import dev.rosalogia.photoduel.platform.toArgbImage
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.hypot

@RunWith(AndroidJUnit4::class)
class AlignerTest {
    private val context = InstrumentationRegistry.getInstrumentation().context
    // Only alignImages is exercised, which doesn't decode.
    private val aligner = Aligner(object : PhotoDecoder {
        override suspend fun decodeDisplay(mediaId: Long, maxEdge: Int) = error("unused")
        override suspend fun decodeArgb(mediaId: Long, maxEdge: Int) = error("unused")
    })

    private fun Aligner.alignBitmaps(king: Bitmap, challenger: Bitmap) = alignImages(king.toArgbImage(), challenger.toArgbImage())

    private fun fixture(name: String): Bitmap =
        context.assets.open(name).use { BitmapFactory.decodeStream(it) }!!

    /** Renders [src] as a handheld re-shot would see it: [t] maps king pixels to challenger pixels. */
    private fun reshoot(src: Bitmap, t: Matrix): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, t, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /** Max error, in king pixels, of the recovered transform at a grid of interior points. */
    private fun maxErrorPx(king: Bitmap, challenger: Bitmap, t: Matrix, a: Alignment): Double {
        var worst = 0.0
        for (fx in listOf(0.2f, 0.5f, 0.8f)) for (fy in listOf(0.2f, 0.5f, 0.8f)) {
            val p = floatArrayOf(king.width * fx, king.height * fy)
            val q = p.copyOf().also { t.mapPoints(it) }
            val (u, v) = a.map(q[0].toDouble() / challenger.width, q[1].toDouble() / challenger.height)
            worst = maxOf(worst, hypot(u * king.width - p[0], v * king.height - p[1]))
        }
        return worst
    }

    private fun assertRecovers(name: String, t: Matrix) {
        val king = fixture(name)
        val challenger = reshoot(king, t)
        val a = aligner.alignBitmaps(king, challenger)
        assertThat(a).isNotNull()
        assertThat(maxErrorPx(king, challenger, t, a!!)).isLessThan(2.0)
    }

    @Test
    fun recoversPureShift() = assertRecovers("forest.jpg", Matrix().apply { setTranslate(17f, -11f) })

    @Test
    fun recoversShiftRotationAndZoom() = assertRecovers(
        "forest.jpg",
        Matrix().apply {
            setRotate(1.2f, 800f, 600f)
            postScale(1.02f, 1.02f, 800f, 600f)
            postTranslate(-14f, 9f)
        },
    )

    @Test
    fun recoversOnAPortraitWithLittleTexture() = assertRecovers(
        "portrait.jpg",
        Matrix().apply {
            setRotate(-0.8f, 800f, 600f)
            postTranslate(10f, 6f)
        },
    )

    @Test
    fun identicalPhotosGiveIdentity() {
        val king = fixture("forest.jpg")
        val a = aligner.alignBitmaps(king, king)
        assertThat(a).isNotNull()
        assertThat(maxErrorPx(king, king, Matrix(), a!!)).isLessThan(1.0)
    }

    @Test
    fun unrelatedPhotosAreNotAligned() {
        assertThat(aligner.alignBitmaps(fixture("forest.jpg"), fixture("pug.jpg"))).isNull()
    }

    @Test
    fun largeReframingIsRejected() {
        val king = fixture("forest.jpg")
        // A 25% shift is a different framing, not handheld drift; don't warp it.
        val challenger = reshoot(king, Matrix().apply { setTranslate(400f, 0f) })
        assertThat(aligner.alignBitmaps(king, challenger)).isNull()
    }
}
