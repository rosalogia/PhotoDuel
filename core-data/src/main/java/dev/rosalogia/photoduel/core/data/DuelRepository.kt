package dev.rosalogia.photoduel.core.data

import dev.rosalogia.photoduel.core.data.db.DuelDecisionEntity
import dev.rosalogia.photoduel.core.data.db.PhotoDuelDatabase
import javax.inject.Inject
import javax.inject.Singleton

data class StoredDecision(val id: Long, val winner: Long, val loser: Long)

/**
 * Every pick is logged as it happens, so an interrupted duel can be resumed and so the history can
 * later train a preference model. Undo marks a row undone rather than deleting it.
 */
@Singleton
class DuelRepository @Inject constructor(
    private val db: PhotoDuelDatabase,
) {
    suspend fun active(groupId: Long): List<StoredDecision> =
        db.duelDao().activeDecisions(groupId).map { StoredDecision(it.id, it.winnerMediaId, it.loserMediaId) }

    suspend fun record(groupId: Long, winner: Long, loser: Long, flipCount: Int): Long =
        db.duelDao().insert(
            DuelDecisionEntity(
                groupId = groupId,
                winnerMediaId = winner,
                loserMediaId = loser,
                decidedAt = System.currentTimeMillis(),
                flipCount = flipCount,
            ),
        )

    suspend fun markUndone(decisionId: Long) = db.duelDao().markUndone(decisionId)

    suspend fun markUndone(decisionIds: Collection<Long>) = decisionIds.forEach { db.duelDao().markUndone(it) }

    /** Abandons the whole duel for a group, e.g. to start over. */
    suspend fun reset(groupId: Long) = db.duelDao().clearGroup(groupId)
}
