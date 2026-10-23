package dev.rosalogia.photoduel.core.data.highlights

import com.google.common.truth.Truth.assertThat
import dev.rosalogia.photoduel.core.data.db.GroupStatus
import dev.rosalogia.photoduel.core.data.db.MembershipRow
import dev.rosalogia.photoduel.core.data.db.PhotoScoreTimeRow
import org.junit.Test

class CandidatesTest {
    private fun p(id: Long, score: Float? = null) = PhotoScoreTimeRow(id, dateTaken = id * 1000, preScore = score)

    @Test
    fun burstsCollapseToWinnerOrBestScore() {
        val photos = listOf(p(1), p(2, 0.2f), p(3, 0.9f), p(4, 0.5f), p(5), p(6), p(7))
        val memberships = listOf(
            MembershipRow(2, 10, GroupStatus.UNRESOLVED, null),
            MembershipRow(3, 10, GroupStatus.UNRESOLVED, null),
            MembershipRow(4, 10, GroupStatus.UNRESOLVED, null),
            MembershipRow(5, 20, GroupStatus.DONE, 6),
            MembershipRow(6, 20, GroupStatus.DONE, 6),
        )
        assertThat(collapseBursts(photos, memberships)).containsExactly(
            Candidate(1, 1),
            Candidate(3, 3),
            Candidate(6, 2),
            Candidate(7, 1),
        ).inOrder()
    }

    @Test
    fun unscoredBurstUsesItsFirstShot() {
        val photos = listOf(p(1), p(2))
        val memberships = listOf(MembershipRow(1, 10, GroupStatus.UNRESOLVED, null), MembershipRow(2, 10, GroupStatus.UNRESOLVED, null))
        assertThat(collapseBursts(photos, memberships)).containsExactly(Candidate(1, 2))
    }

    @Test
    fun winnerOutsideTheRangeFallsBackToScore() {
        val photos = listOf(p(1, 0.1f), p(2, 0.8f))
        val memberships = listOf(MembershipRow(1, 10, GroupStatus.DONE, 99), MembershipRow(2, 10, GroupStatus.DONE, 99))
        assertThat(collapseBursts(photos, memberships)).containsExactly(Candidate(2, 2))
    }
}
