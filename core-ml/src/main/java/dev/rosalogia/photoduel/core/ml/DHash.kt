package dev.rosalogia.photoduel.core.ml

import android.graphics.Bitmap
import android.graphics.Color

/**
 * 64-bit difference hash: shrink to 9×8 grayscale and record, for each row, whether each pixel is
 * brighter than its right neighbor. Near-identical photos differ in only a few bits.
 */
object DHash {
    const val WIDTH = 9
    const val HEIGHT = 8

    /** [luma] is a row-major WIDTH×HEIGHT grayscale image. */
    fun fromLuma(luma: IntArray): Long {
        require(luma.size == WIDTH * HEIGHT)
        var hash = 0L
        var bit = 0
        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH - 1) {
                if (luma[y * WIDTH + x] > luma[y * WIDTH + x + 1]) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    fun of(bitmap: Bitmap): Long {
        val small = Bitmap.createScaledBitmap(bitmap, WIDTH, HEIGHT, true)
        val pixels = IntArray(WIDTH * HEIGHT)
        small.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
        if (small !== bitmap) small.recycle()
        return fromLuma(IntArray(pixels.size) { i ->
            val p = pixels[i]
            (299 * Color.red(p) + 587 * Color.green(p) + 114 * Color.blue(p)) / 1000
        })
    }
}
