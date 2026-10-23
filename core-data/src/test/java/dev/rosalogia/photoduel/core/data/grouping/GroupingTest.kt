package dev.rosalogia.photoduel.core.data.grouping

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TimeSessionsTest {
    private fun p(id: Long, sec: Long) = TimedPhoto(id, sec * 1000)

    @Test
    fun emptyInputGivesNoSessions() {
        assertThat(splitIntoSessions(emptyList(), 60_000)).isEmpty()
    }

    @Test
    fun splitsOnGapsLongerThanThreshold() {
        val photos = listOf(p(1, 0), p(2, 10), p(3, 70), p(4, 200), p(5, 205))
        val sessions = splitIntoSessions(photos, 60_000).map { s -> s.map { it.mediaId } }
        assertThat(sessions).containsExactly(listOf(1L, 2L, 3L), listOf(4L, 5L)).inOrder()
    }

    @Test
    fun gapExactlyAtThresholdStaysInSession() {
        val sessions = splitIntoSessions(listOf(p(1, 0), p(2, 60)), 60_000)
        assertThat(sessions).hasSize(1)
    }

    @Test
    fun unsortedInputIsOrderedByTimeThenId() {
        val photos = listOf(p(3, 5), p(1, 5), p(2, 0))
        val sessions = splitIntoSessions(photos, 60_000).map { s -> s.map { it.mediaId } }
        assertThat(sessions).containsExactly(listOf(2L, 1L, 3L))
    }

    @Test
    fun chainOfShortGapsFormsOneSession() {
        val photos = (0L until 10L).map { p(it, it * 50) }
        assertThat(splitIntoSessions(photos, 60_000)).hasSize(1)
    }
}

class GroupReconcilerTest {
    @Test
    fun unchangedGroupsKeepTheirIds() {
        val plan = reconcileGroups(
            old = mapOf(10L to setOf(1L, 2L), 11L to setOf(3L, 4L)),
            new = listOf(listOf(1L, 2L), listOf(3L, 4L)),
        )
        assertThat(plan.keep).containsExactly(10L, listOf(1L, 2L), 11L, listOf(3L, 4L))
        assertThat(plan.create).isEmpty()
        assertThat(plan.delete).isEmpty()
    }

    @Test
    fun groupThatGainsAPhotoKeepsItsId() {
        val plan = reconcileGroups(old = mapOf(10L to setOf(1L, 2L)), new = listOf(listOf(1L, 2L, 3L)))
        assertThat(plan.keep).containsExactly(10L, listOf(1L, 2L, 3L))
    }

    @Test
    fun newClusterIsCreatedAndVanishedOneDeleted() {
        val plan = reconcileGroups(old = mapOf(10L to setOf(1L, 2L)), new = listOf(listOf(5L, 6L)))
        assertThat(plan.keep).isEmpty()
        assertThat(plan.create).containsExactly(listOf(5L, 6L))
        assertThat(plan.delete).containsExactly(10L)
    }

    @Test
    fun splitGroupGivesIdToLargerHalf() {
        val plan = reconcileGroups(
            old = mapOf(10L to setOf(1L, 2L, 3L, 4L, 5L)),
            new = listOf(listOf(1L, 2L), listOf(3L, 4L, 5L)),
        )
        assertThat(plan.keep).containsExactly(10L, listOf(3L, 4L, 5L))
        assertThat(plan.create).containsExactly(listOf(1L, 2L))
    }

    @Test
    fun mergedGroupsKeepOneIdAndDeleteTheOther() {
        val plan = reconcileGroups(
            old = mapOf(10L to setOf(1L, 2L), 11L to setOf(3L, 4L, 5L)),
            new = listOf(listOf(1L, 2L, 3L, 4L, 5L)),
        )
        assertThat(plan.keep.keys).containsExactly(11L)
        assertThat(plan.delete).containsExactly(10L)
    }
}
