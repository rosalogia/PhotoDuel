package dev.rosalogia.photoduel.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A camera-roll photo, referenced by MediaStore ID. The file itself is never copied. */
@Entity(tableName = "photo", indices = [Index("date_taken")])
data class PhotoEntity(
    @PrimaryKey @ColumnInfo(name = "media_id") val mediaId: Long,
    @ColumnInfo(name = "date_taken") val dateTaken: Long,
    @ColumnInfo(name = "date_modified") val dateModified: Long,
    /** Display width/height, i.e. already swapped for 90/270° EXIF orientation. */
    val width: Int,
    val height: Int,
    @ColumnInfo(name = "relative_path") val relativePath: String?,
    /** float32 little-endian image embedding; null until indexed. */
    val embedding: ByteArray? = null,
    val dhash: Long? = null,
    @ColumnInfo(name = "pre_score") val preScore: Float? = null,
    @ColumnInfo(name = "indexed_at") val indexedAt: Long? = null,
) {
    // ByteArray breaks data-class equality; compare by identity of the media version instead.
    override fun equals(other: Any?): Boolean =
        other is PhotoEntity && other.mediaId == mediaId && other.dateModified == dateModified && other.indexedAt == indexedAt

    override fun hashCode(): Int = mediaId.hashCode() * 31 + dateModified.hashCode()
}

enum class GroupStatus { UNRESOLVED, DONE, DISMISSED }

@Entity(tableName = "photo_group", indices = [Index("status"), Index("start_time")])
data class PhotoGroupEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "group_id") val groupId: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val status: GroupStatus = GroupStatus.UNRESOLVED,
    @ColumnInfo(name = "winner_media_id") val winnerMediaId: Long? = null,
    @ColumnInfo(name = "runner_up_media_id") val runnerUpMediaId: Long? = null,
    /** Capture time of the first/last member, denormalized for sorting and date filtering. */
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long,
)

@Entity(
    tableName = "group_member",
    primaryKeys = ["group_id", "media_id"],
    indices = [Index("media_id")],
)
data class GroupMemberEntity(
    @ColumnInfo(name = "group_id") val groupId: Long,
    @ColumnInfo(name = "media_id") val mediaId: Long,
    /** Ordered by capture time. */
    val position: Int,
)

enum class OverrideKind { EXCLUDE, MERGE, SPLIT }

/** A manual grouping correction, re-applied after every re-cluster so user edits stick. */
@Entity(tableName = "group_override")
data class GroupOverrideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: OverrideKind,
    /** JSON array of media IDs. */
    @ColumnInfo(name = "media_ids") val mediaIds: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** One head-to-head pick. Kept as training data for a future preference model. */
@Entity(tableName = "duel_decision", indices = [Index("group_id")])
data class DuelDecisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "group_id") val groupId: Long,
    @ColumnInfo(name = "winner_media_id") val winnerMediaId: Long,
    @ColumnInfo(name = "loser_media_id") val loserMediaId: Long,
    @ColumnInfo(name = "decided_at") val decidedAt: Long,
    @ColumnInfo(name = "flip_count") val flipCount: Int,
    val undone: Boolean = false,
)
