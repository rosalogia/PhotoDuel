package dev.rosalogia.photoduel.feature.highlights.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

class EliminationTest {
    /** Runs to the end with a judge who always prefers the lower "true rank". */
    private fun run(items: List<Long>, target: Int, rank: Map<Long, Int>): Elimination {
        val e = Elimination(items, target)
        while (true) {
            val (a, b) = e.next() ?: break
            if (rank.getValue(a) < rank.getValue(b)) e.record(a, b) else e.record(b, a)
        }
        return e
    }

    @Test
    fun endsWithExactlyTheTarget() {
        val items = (1L..30L).toList()
        val e = run(items, 10, items.associateWith { it.toInt() })
        assertThat(e.active).hasSize(10)
        assertThat(e.next()).isNull()
        assertThat(e.remainingEstimate()).isEqualTo(0)
    }

    @Test
    fun alreadyAtTargetNeedsNoPicks() {
        val e = Elimination(listOf(1L, 2L, 3L), 3)
        assertThat(e.isFinished).isTrue()
        assertThat(e.next()).isNull()
    }

    @Test
    fun consistentJudgeMostlyKeepsTheTrueTopAndCostsAboutTwoPicksPerCut() {
        var recall = 0
        var top5 = 0
        var picks = 0
        val runs = 200
        repeat(runs) { seed ->
            val items = (1L..30L).toList()
            val rank = items.shuffled(Random(seed)).withIndex().associate { it.value to it.index }
            val e = run(items, 10, rank)
            recall += e.active.count { rank.getValue(it) < 10 }
            top5 += e.active.count { rank.getValue(it) < 5 }
            picks += e.history.size
        }
        println("avg recall ${recall / runs.toDouble()} of 10, top-5 kept ${top5 / runs.toDouble()}, avg picks ${picks / runs.toDouble()}")
        // Misses sit near the cut-off; the clear favorites survive.
        assertThat(recall / runs.toDouble()).isAtLeast(8.0)
        assertThat(top5 / runs.toDouble()).isAtLeast(4.7)
        assertThat(picks / runs.toDouble()).isAtMost(50.0)
    }

    @Test
    fun estimateIsCloseToTheActualNumberOfPicks() {
        for ((m, k) in listOf(12 to 10, 22 to 13, 30 to 10, 100 to 10)) {
            val items = (1L..m.toLong()).toList()
            val estimate = Elimination(items, k).remainingEstimate()
            val actual = (0 until 50).map { seed ->
                val rank = items.shuffled(Random(seed)).withIndex().associate { it.value to it.index }
                run(items, k, rank).history.size
            }.average()
            assertThat(estimate.toDouble()).isWithin(actual * 0.2 + 1).of(actual)
        }
    }

    @Test
    fun replayResumesAndStopsAtAMismatch() {
        val items = (1L..8L).toList()
        val e = run(items, 3, items.associateWith { it.toInt() })
        val (resumed, used) = Elimination.replay(items, 3, e.history)
        assertThat(used).isEqualTo(e.history.size)
        assertThat(resumed.active).isEqualTo(e.active)

        val tampered = e.history.take(2) + Comparison(1, 8) + e.history.drop(3)
        assertThat(Elimination.replay(items, 3, tampered).second).isEqualTo(2)
    }
}
