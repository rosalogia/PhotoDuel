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
