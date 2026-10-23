package dev.rosalogia.photoduel.core.data.highlights

import androidx.room.withTransaction
import dev.rosalogia.photoduel.core.data.db.HighlightComparisonEntity
import dev.rosalogia.photoduel.core.data.db.HighlightCullEntity
import dev.rosalogia.photoduel.core.data.db.HighlightItemEntity
import dev.rosalogia.photoduel.core.data.db.HighlightPhase
import dev.rosalogia.photoduel.core.data.db.HighlightSetEntity
import dev.rosalogia.photoduel.core.data.db.HighlightSetSummary
import dev.rosalogia.photoduel.core.data.db.MembershipRow
import dev.rosalogia.photoduel.core.data.db.PhotoDuelDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Everything stored for one set, enough to rebuild where the user left off. */
data class HighlightSession(
    val set: HighlightSetEntity,
    /** Items whose photos still exist, chronological. */
    val items: List<HighlightItemEntity>,
    val culls: List<HighlightCullEntity>,
    val comparisons: List<HighlightComparisonEntity>,
)

@Singleton
class HighlightRepository @Inject constructor(
    private val db: PhotoDuelDatabase,
) {
    private val dao get() = db.highlightDao()

    fun observeSuggestions(): Flow<List<SeriesSuggestion>> =
        db.photoDao().observeCount().distinctUntilChanged().map { SeriesSuggester.suggest(dao.allPhotoTimes(), ZoneId.systemDefault()) }

    fun observeSets(): Flow<List<HighlightSetSummary>> = dao.observeSets()

    /** The photos a set over [start, end) would start from, bursts collapsed. */
    suspend fun candidates(start: Long, end: Long): List<Candidate> {
        val photos = dao.photosInRange(start, end)
        val memberships = ArrayList<MembershipRow>()
        photos.map { it.mediaId }.chunked(CHUNK).forEach { memberships += dao.memberships(it) }
        return collapseBursts(photos, memberships)
    }

    suspend fun create(name: String, start: Long, end: Long, target: Int): Long = db.withTransaction {
        val candidates = candidates(start, end)
        val now = System.currentTimeMillis()
        val id = dao.insertSet(
            HighlightSetEntity(name = name, startTime = start, endTime = end, targetCount = target, createdAt = now, updatedAt = now),
        )
        dao.insertItems(candidates.mapIndexed { i, c -> HighlightItemEntity(id, c.mediaId, i, c.burstSize) })
        id
    }

    suspend fun load(setId: Long): HighlightSession? = db.withTransaction {
        val set = dao.getSet(setId) ?: return@withTransaction null
        HighlightSession(set, dao.items(setId), dao.culls(setId), dao.comparisons(setId))
    }

    suspend fun recordCull(setId: Long, mediaId: Long, pass: Int, keep: Boolean): Long =
        dao.insertCull(HighlightCullEntity(setId = setId, mediaId = mediaId, pass = pass, keep = keep, decidedAt = System.currentTimeMillis()))

    suspend fun deleteCull(id: Long) = dao.deleteCull(id)

    suspend fun setProgress(setId: Long, phase: HighlightPhase, passCount: Int) =
        dao.setProgress(setId, phase, passCount, System.currentTimeMillis())

    suspend fun recordComparison(setId: Long, winner: Long, loser: Long): Long =
        dao.insertComparison(
            HighlightComparisonEntity(setId = setId, winnerMediaId = winner, loserMediaId = loser, decidedAt = System.currentTimeMillis()),
        )

    suspend fun deleteComparisons(ids: List<Long>) = dao.deleteComparisons(ids)

    /** Stores the final picks and marks the set done; an empty list with [phase] reopens it. */
    suspend fun setResult(setId: Long, selected: List<Long>, phase: HighlightPhase, passCount: Int) = db.withTransaction {
        dao.setSelected(setId, selected)
        dao.setProgress(setId, phase, passCount, System.currentTimeMillis())
    }

    /** Back to the first quick pass with nothing decided. Album copies are kept. */
    suspend fun reset(setId: Long) = db.withTransaction {
        dao.deleteCulls(setId)
        dao.deleteAllComparisons(setId)
        dao.setSelected(setId, emptyList())
        dao.setProgress(setId, HighlightPhase.CULL, 1, System.currentTimeMillis())
    }

    suspend fun setAlbumCopy(setId: Long, mediaId: Long, albumMediaId: Long?) = dao.setAlbumCopy(setId, mediaId, albumMediaId)

    suspend fun rename(setId: Long, name: String) = dao.rename(setId, name, System.currentTimeMillis())

    /** Forgets the set. Album copies stay in the gallery; they're ordinary photos now. */
    suspend fun delete(setId: Long) = db.withTransaction {
        dao.deleteCulls(setId)
        dao.deleteAllComparisons(setId)
        dao.deleteItems(setId)
        dao.deleteSet(setId)
    }

    private companion object {
        const val CHUNK = 500
    }
}
