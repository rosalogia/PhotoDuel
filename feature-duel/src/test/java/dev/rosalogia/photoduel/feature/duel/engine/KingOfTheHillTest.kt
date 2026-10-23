package dev.rosalogia.photoduel.feature.duel.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class KingOfTheHillTest {
    @Test
    fun findsWinnerInExactlyNMinusOneDecisions() {
        for (n in 2..12) {
            var duel = KingOfTheHill.start((1L..n).toList())
            var steps = 0
            while (!duel.isFinished) {
                duel = duel.pick(keepChallenger = steps % 3 == 0)
                steps++
            }
            assertThat(steps).isEqualTo(n - 1)
            assertThat(duel.winner).isNotNull()
        }
    }

    @Test
    fun kingStaysWhenKept_challengerTakesOverWhenPicked() {
        var duel = KingOfTheHill.start(listOf(10, 20, 30))
        assertThat(duel.current).isEqualTo(Matchup(10, 20, 0, 2))
        duel = duel.pick(keepChallenger = false)
        assertThat(duel.current).isEqualTo(Matchup(10, 30, 1, 2))
        duel = duel.pick(keepChallenger = true)
        assertThat(duel.isFinished).isTrue()
        assertThat(duel.winner).isEqualTo(30L)
        assertThat(duel.current).isNull()
    }

    @Test
    fun undoRestoresPreviousMatchup() {
        val start = KingOfTheHill.start(listOf(1, 2, 3, 4))
        val after = start.pick(true).pick(false)
        val undone = after.undo()
        assertThat(undone.current).isEqualTo(Matchup(2, 3, 1, 3))
        assertThat(undone.undo().current).isEqualTo(start.current)
        assertThat(start.canUndo).isFalse()
        assertThrows(IllegalStateException::class.java) { start.undo() }
    }

    @Test
    fun undoAfterFinishReopensLastStep() {
        val done = KingOfTheHill.start(listOf(1, 2)).pick(true)
        assertThat(done.winner).isEqualTo(2L)
        assertThat(done.undo().winner).isNull()
        assertThat(done.undo().current).isEqualTo(Matchup(1, 2, 0, 1))
    }

    @Test
    fun runnerUpCandidatesAreThoseBeatenDirectlyByWinner() {
        // 1 beats 2, 3 beats 1, 3 beats 4, 5 beats 3, 5 beats 6.
        val duel = KingOfTheHill.start(listOf(1, 2, 3, 4, 5, 6))
            .pick(false).pick(true).pick(false).pick(true).pick(false)
        assertThat(duel.winner).isEqualTo(5L)
        assertThat(duel.runnerUpCandidates()).containsExactly(3L, 6L).inOrder()
    }

    @Test
    fun runnerUpWhenFirstKingWinsEverything() {
        val duel = KingOfTheHill.start(listOf(1, 2, 3)).pick(false).pick(false)
        assertThat(duel.runnerUpCandidates()).containsExactly(2L, 3L).inOrder()
    }

    @Test
    fun nextChallengerIsPrefetchTarget() {
        val duel = KingOfTheHill.start(listOf(1, 2, 3))
        assertThat(duel.nextChallenger).isEqualTo(3L)
        assertThat(duel.pick(false).nextChallenger).isNull()
    }

    @Test
    fun replayRestoresValidPrefix() {
        val order = listOf(1L, 2L, 3L, 4L)
        val stored = KingOfTheHill.start(order).pick(true).pick(false).decisions
        val (duel, used) = KingOfTheHill.replay(order, stored)
        assertThat(used).isEqualTo(2)
        assertThat(duel.current).isEqualTo(Matchup(2, 4, 2, 3))
    }

    @Test
    fun replayStopsAtFirstDecisionThatNoLongerFits() {
        val stored = listOf(Decision(1, 2), Decision(3, 1), Decision(3, 4))
        // Photo 3 was deleted, so the second stored decision no longer matches.
        val (duel, used) = KingOfTheHill.replay(listOf(1L, 2L, 4L), stored)
        assertThat(used).isEqualTo(1)
        assertThat(duel.current).isEqualTo(Matchup(1, 4, 1, 2))
    }

    @Test
    fun rejectsTooFewOrDuplicatePhotos() {
        assertThrows(IllegalArgumentException::class.java) { KingOfTheHill.start(listOf(1)) }
        assertThrows(IllegalArgumentException::class.java) { KingOfTheHill.start(listOf(1, 1)) }
    }
}
