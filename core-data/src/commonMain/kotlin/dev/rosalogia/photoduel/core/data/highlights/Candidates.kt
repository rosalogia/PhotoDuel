package dev.rosalogia.photoduel.core.data.highlights

import dev.rosalogia.photoduel.core.data.db.GroupStatus
import dev.rosalogia.photoduel.core.data.db.MembershipRow
import dev.rosalogia.photoduel.core.data.db.PhotoScoreTimeRow

data class Candidate(val mediaId: Long, val burstSize: Int)

/**
 * One candidate per burst group, so a series isn't ten near-identical shots of the same view: the
 * duel winner for a finished group, otherwise the best pre-scored shot. [photos] must be in capture
 * order; the result keeps that order, placing each burst at its representative's time.
 */
fun collapseBursts(photos: List<PhotoScoreTimeRow>, memberships: List<MembershipRow>): List<Candidate> {
    val groupOf = memberships.associateBy { it.mediaId }
    val byGroup = photos.filter { it.mediaId in groupOf }.groupBy { groupOf.getValue(it.mediaId).groupId }
    val representative = HashMap<Long, Long>()
    for ((groupId, members) in byGroup) {
        val g = groupOf.getValue(members.first().mediaId)
        val winner = g.winnerMediaId?.takeIf { w -> g.status == GroupStatus.DONE && members.any { it.mediaId == w } }
        representative[groupId] = winner
            ?: members.withIndex().maxWith(compareBy<IndexedValue<PhotoScoreTimeRow>> { it.value.preScore ?: -1f }.thenByDescending { it.index }).value.mediaId
    }
    return photos.mapNotNull { p ->
        val g = groupOf[p.mediaId] ?: return@mapNotNull Candidate(p.mediaId, 1)
        if (representative[g.groupId] == p.mediaId) Candidate(p.mediaId, byGroup.getValue(g.groupId).size) else null
    }
}
