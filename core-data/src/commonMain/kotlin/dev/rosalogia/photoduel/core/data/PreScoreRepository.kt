package dev.rosalogia.photoduel.core.data

import dev.rosalogia.photoduel.core.common.AppLog
import dev.rosalogia.photoduel.core.data.db.PhotoDuelDatabase
import dev.rosalogia.photoduel.core.data.index.PhotoQualityScorer

class PreScoreRepository(
    private val db: PhotoDuelDatabase,
    private val scorer: PhotoQualityScorer,
) {
    /**
     * Pre-scores for [ids], computing and saving any that are missing. Scores are persisted so a
     * group's duel order is stable, which resuming a duel from stored decisions relies on.
     */
    suspend fun ensure(ids: List<Long>): Map<Long, Float> {
        val known = ids.chunked(900).flatMap { db.photoDao().preScores(it) }.associate { it.mediaId to it.preScore }
        return ids.associateWith { id ->
            known[id] ?: compute(id)
        }
    }

    suspend fun compute(id: Long): Float {
        val score = try {
            scorer.preScore(id)
        } catch (e: Exception) {
            AppLog.w(TAG, "Pre-score failed for $id", e)
            0f
        }
        db.photoDao().setPreScore(id, score)
        return score
    }

    private companion object {
        const val TAG = "PreScore"
    }
}
