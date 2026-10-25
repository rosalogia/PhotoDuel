package dev.rosalogia.photoduel.core.ml

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

    /** Box-filters [image] down to WIDTH×HEIGHT, then hashes it. */
    fun of(image: ArgbImage): Long {
        val luma = IntArray(WIDTH * HEIGHT)
        for (y in 0 until HEIGHT) {
            val y0 = y * image.height / HEIGHT
            val y1 = maxOf(y0 + 1, (y + 1) * image.height / HEIGHT)
            for (x in 0 until WIDTH) {
                val x0 = x * image.width / WIDTH
                val x1 = maxOf(x0 + 1, (x + 1) * image.width / WIDTH)
                var sum = 0L
                for (yy in y0 until y1) for (xx in x0 until x1) {
                    val p = image.pixels[yy * image.width + xx]
                    sum += (299 * (p shr 16 and 255) + 587 * (p shr 8 and 255) + 114 * (p and 255)) / 1000
                }
                luma[y * WIDTH + x] = (sum / ((y1 - y0) * (x1 - x0))).toInt()
            }
        }
        return fromLuma(luma)
    }
}
