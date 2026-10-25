package dev.rosalogia.photoduel.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class GroupWithMembers(
    @Embedded val group: PhotoGroupEntity,
    @Relation(parentColumn = "group_id", entityColumn = "group_id")
    val members: List<GroupMemberEntity>,
) {
    val orderedMediaIds: List<Long> get() = members.sortedBy { it.position }.map { it.mediaId }
}

data class SignalRow(
    @ColumnInfo(name = "media_id") val mediaId: Long,
    val embedding: ByteArray?,
    val dhash: Long?,
    @ColumnInfo(name = "indexed_at") val indexedAt: Long?,
)

data class PhotoTimeRow(
    @ColumnInfo(name = "media_id") val mediaId: Long,
    @ColumnInfo(name = "date_taken") val dateTaken: Long,
    @ColumnInfo(name = "date_modified") val dateModified: Long,
)

@Dao
interface PhotoDao {
    /** Without the embedding blobs, which regrouping only needs for some photos. */
    @Query("SELECT media_id, date_taken, date_modified FROM photo")
    suspend fun getAllTimes(): List<PhotoTimeRow>

    @Query("SELECT * FROM photo WHERE media_id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<PhotoEntity>

    @Upsert
    suspend fun upsertAll(photos: List<PhotoEntity>)

    @Query("DELETE FROM photo WHERE media_id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM photo")
    fun observeCount(): Flow<Int>

    /** Newest first, so recent groups get visual clustering soonest. */
    @Query("SELECT media_id FROM photo WHERE indexed_at IS NULL ORDER BY date_taken DESC LIMIT :limit")
    suspend fun unindexedIds(limit: Int): List<Long>

    @Query("SELECT COUNT(*) FROM photo WHERE indexed_at IS NULL")
    fun observeUnindexedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM photo WHERE indexed_at IS NULL")
    suspend fun unindexedCount(): Int

    @Query("UPDATE photo SET embedding = :embedding, dhash = :dhash, indexed_at = :indexedAt WHERE media_id = :mediaId")
    suspend fun setFeatures(mediaId: Long, embedding: ByteArray?, dhash: Long?, indexedAt: Long)

    @Query("SELECT media_id, embedding, dhash, indexed_at FROM photo WHERE media_id IN (:ids)")
    suspend fun signals(ids: List<Long>): List<SignalRow>

    @Query("SELECT media_id, pre_score FROM photo WHERE media_id IN (:ids)")
    suspend fun preScores(ids: List<Long>): List<PreScoreRow>

    @Query("UPDATE photo SET pre_score = :score WHERE media_id = :mediaId")
    suspend fun setPreScore(mediaId: Long, score: Float)

    /** Members of to-do groups that haven't been scored yet: the photos a duel will need next. */
    @Query(
        """
        SELECT p.media_id FROM photo p
        JOIN group_member m ON m.media_id = p.media_id
        JOIN photo_group g ON g.group_id = m.group_id
        WHERE p.pre_score IS NULL AND g.status = 'UNRESOLVED'
        ORDER BY g.start_time DESC
        LIMIT :limit
        """,
    )
    suspend fun unscoredGroupMembers(limit: Int): List<Long>
}

data class PreScoreRow(
    @ColumnInfo(name = "media_id") val mediaId: Long,
    @ColumnInfo(name = "pre_score") val preScore: Float?,
)

@Dao
interface GroupDao {
    @Transaction
    @Query(
        """
        SELECT * FROM photo_group
        WHERE status = :status AND end_time >= :sinceMillis
        ORDER BY start_time DESC
        """,
    )
    fun observeGroups(status: GroupStatus, sinceMillis: Long): Flow<List<GroupWithMembers>>

    @Transaction
    @Query("SELECT * FROM photo_group WHERE group_id = :groupId")
    fun observeGroup(groupId: Long): Flow<GroupWithMembers?>

    @Transaction
    @Query("SELECT * FROM photo_group WHERE group_id = :groupId")
    suspend fun getGroup(groupId: Long): GroupWithMembers?

    @Transaction
    @Query("SELECT * FROM photo_group")
    suspend fun getAllGroups(): List<GroupWithMembers>

    @Query("SELECT COUNT(*) FROM photo_group WHERE status = :status")
    fun observeCount(status: GroupStatus): Flow<Int>

    @Insert
    suspend fun insertGroup(group: PhotoGroupEntity): Long

    @Query("UPDATE photo_group SET start_time = :start, end_time = :end WHERE group_id = :groupId")
    suspend fun updateTimes(groupId: Long, start: Long, end: Long)

    @Query(
        """
        UPDATE photo_group SET status = :status, winner_media_id = :winner, runner_up_media_id = :runnerUp
        WHERE group_id = :groupId
        """,
    )
    suspend fun setResult(groupId: Long, status: GroupStatus, winner: Long?, runnerUp: Long?)

    @Query("DELETE FROM photo_group WHERE group_id IN (:ids)")
    suspend fun deleteGroups(ids: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<GroupMemberEntity>)

    @Query("DELETE FROM group_member WHERE group_id = :groupId")
    suspend fun deleteMembersOf(groupId: Long)

    @Query("DELETE FROM group_member WHERE media_id IN (:mediaIds)")
    suspend fun deleteMembersByMedia(mediaIds: List<Long>)
}

@Dao
interface OverrideDao {
    @Query("SELECT * FROM group_override ORDER BY created_at, id")
    suspend fun getAll(): List<GroupOverrideEntity>

    @Insert
    suspend fun insert(override: GroupOverrideEntity): Long
}

@Dao
interface DuelDao {
    @Insert
    suspend fun insert(decision: DuelDecisionEntity): Long

    @Query("UPDATE duel_decision SET undone = 1 WHERE id = :id")
    suspend fun markUndone(id: Long)

    @Query("SELECT * FROM duel_decision WHERE group_id = :groupId AND undone = 0 ORDER BY decided_at, id")
    suspend fun activeDecisions(groupId: Long): List<DuelDecisionEntity>

    @Query("UPDATE duel_decision SET undone = 1 WHERE group_id = :groupId")
    suspend fun clearGroup(groupId: Long)
}

data class PhotoScoreTimeRow(
    @ColumnInfo(name = "media_id") val mediaId: Long,
    @ColumnInfo(name = "date_taken") val dateTaken: Long,
    @ColumnInfo(name = "pre_score") val preScore: Float?,
)

data class MembershipRow(
    @ColumnInfo(name = "media_id") val mediaId: Long,
    @ColumnInfo(name = "group_id") val groupId: Long,
    val status: GroupStatus,
    @ColumnInfo(name = "winner_media_id") val winnerMediaId: Long?,
)

data class HighlightSetSummary(
    @Embedded val set: HighlightSetEntity,
    @ColumnInfo(name = "item_count") val itemCount: Int,
    @ColumnInfo(name = "selected_count") val selectedCount: Int,
)

@Dao
interface HighlightDao {
    @Query("SELECT date_taken FROM photo")
    suspend fun allPhotoTimes(): List<Long>

    @Query("SELECT media_id, date_taken, pre_score FROM photo WHERE date_taken >= :start AND date_taken < :end ORDER BY date_taken, media_id")
    suspend fun photosInRange(start: Long, end: Long): List<PhotoScoreTimeRow>

    @Query(
        """
        SELECT m.media_id, m.group_id, g.status, g.winner_media_id FROM group_member m
        JOIN photo_group g ON g.group_id = m.group_id
        WHERE m.media_id IN (:mediaIds)
        """,
    )
    suspend fun memberships(mediaIds: List<Long>): List<MembershipRow>

    @Query(
        """
        SELECT s.*, COUNT(i.media_id) AS item_count, COALESCE(SUM(i.selected), 0) AS selected_count
        FROM highlight_set s LEFT JOIN highlight_item i ON i.set_id = s.set_id
        GROUP BY s.set_id ORDER BY s.updated_at DESC
        """,
    )
    fun observeSets(): Flow<List<HighlightSetSummary>>

    @Query("SELECT * FROM highlight_set WHERE set_id = :setId")
    suspend fun getSet(setId: Long): HighlightSetEntity?

    @Insert
    suspend fun insertSet(set: HighlightSetEntity): Long

    @Query("UPDATE highlight_set SET phase = :phase, pass_count = :passCount, updated_at = :now WHERE set_id = :setId")
    suspend fun setProgress(setId: Long, phase: HighlightPhase, passCount: Int, now: Long)

    @Query("UPDATE highlight_set SET name = :name, updated_at = :now WHERE set_id = :setId")
    suspend fun rename(setId: Long, name: String, now: Long)

    @Query("DELETE FROM highlight_set WHERE set_id = :setId")
    suspend fun deleteSet(setId: Long)

    @Insert
    suspend fun insertItems(items: List<HighlightItemEntity>)

    /** Items whose photo still exists, in order. */
    @Query(
        """
        SELECT i.* FROM highlight_item i JOIN photo p ON p.media_id = i.media_id
        WHERE i.set_id = :setId ORDER BY i.position
        """,
    )
    suspend fun items(setId: Long): List<HighlightItemEntity>

    @Query("DELETE FROM highlight_item WHERE set_id = :setId")
    suspend fun deleteItems(setId: Long)

    @Query("UPDATE highlight_item SET selected = (media_id IN (:mediaIds)) WHERE set_id = :setId")
    suspend fun setSelected(setId: Long, mediaIds: List<Long>)

    @Query("UPDATE highlight_item SET album_media_id = :albumMediaId WHERE set_id = :setId AND media_id = :mediaId")
    suspend fun setAlbumCopy(setId: Long, mediaId: Long, albumMediaId: Long?)

    @Query("SELECT * FROM highlight_cull WHERE set_id = :setId ORDER BY pass, decided_at, id")
    suspend fun culls(setId: Long): List<HighlightCullEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCull(cull: HighlightCullEntity): Long

    @Query("DELETE FROM highlight_cull WHERE id = :id")
    suspend fun deleteCull(id: Long)

    @Query("DELETE FROM highlight_cull WHERE set_id = :setId")
    suspend fun deleteCulls(setId: Long)

    @Query("SELECT * FROM highlight_comparison WHERE set_id = :setId ORDER BY decided_at, id")
    suspend fun comparisons(setId: Long): List<HighlightComparisonEntity>

    @Insert
    suspend fun insertComparison(c: HighlightComparisonEntity): Long

    @Query("DELETE FROM highlight_comparison WHERE id IN (:ids)")
    suspend fun deleteComparisons(ids: List<Long>)

    @Query("DELETE FROM highlight_comparison WHERE set_id = :setId")
    suspend fun deleteAllComparisons(setId: Long)
}
