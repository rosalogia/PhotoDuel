package dev.rosalogia.photoduel.core.ml

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DHashTest {
    private fun image(f: (x: Int, y: Int) -> Int) = IntArray(DHash.WIDTH * DHash.HEIGHT) { f(it % DHash.WIDTH, it / DHash.WIDTH) }

    @Test
    fun brighteningLeftToRightGivesAllZeros() {
        assertThat(DHash.fromLuma(image { x, _ -> x * 20 })).isEqualTo(0L)
    }

    @Test
    fun darkeningLeftToRightGivesAllOnes() {
        assertThat(DHash.fromLuma(image { x, _ -> 255 - x * 20 })).isEqualTo(-1L)
    }

    @Test
    fun uniformBrightnessChangeKeepsHash() {
        val base = image { x, y -> (x * 37 + y * 91) % 200 }
        val brighter = IntArray(base.size) { base[it] + 30 }
        assertThat(DHash.fromLuma(brighter)).isEqualTo(DHash.fromLuma(base))
    }

    @Test
    fun smallLocalChangeFlipsFewBits() {
        val base = image { x, y -> (x * 37 + y * 91) % 200 }
        val changed = base.copyOf().also { it[30] += 100 }
        val diff = java.lang.Long.bitCount(DHash.fromLuma(base) xor DHash.fromLuma(changed))
        assertThat(diff).isAtMost(2)
    }
}
