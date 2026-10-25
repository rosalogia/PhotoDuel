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

enum class HighlightPhase { CULL, TRIM, DONE }

/**
 * A "best of a series" selection: pick the top [targetCount] photos from a day or trip. Unlike a
 * burst duel, nothing is ever trashed; the result is copied, shared or favorited.
 */
@Entity(tableName = "highlight_set")
data class HighlightSetEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "set_id") val setId: Long = 0,
    val name: String,
    @ColumnInfo(name = "start_time") val startTime: Long,
    /** Exclusive. */
    @ColumnInfo(name = "end_time") val endTime: Long,
    @ColumnInfo(name = "target_count") val targetCount: Int,
    val phase: HighlightPhase = HighlightPhase.CULL,
    /** Quick passes started; pass N reviews the photos kept in pass N-1. */
    @ColumnInfo(name = "pass_count") val passCount: Int = 1,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** A candidate photo in a set. Burst groups are collapsed to one representative. */
@Entity(
    tableName = "highlight_item",
    primaryKeys = ["set_id", "media_id"],
    indices = [Index("media_id")],
)
data class HighlightItemEntity(
    @ColumnInfo(name = "set_id") val setId: Long,
    @ColumnInfo(name = "media_id") val mediaId: Long,
    /** Chronological. */
    val position: Int,
    /** How many photos of a burst this one stands for (1 for a single shot). */
    @ColumnInfo(name = "burst_size") val burstSize: Int = 1,
    val selected: Boolean = false,
    /** The copy made in the highlights album, if any. */
    @ColumnInfo(name = "album_media_id") val albumMediaId: Long? = null,
)

/** A keep/skip swipe in one quick pass. */
@Entity(
    tableName = "highlight_cull",
    indices = [Index(value = ["set_id", "pass", "media_id"], unique = true)],
)
data class HighlightCullEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "set_id") val setId: Long,
    @ColumnInfo(name = "media_id") val mediaId: Long,
    val pass: Int,
    val keep: Boolean,
    @ColumnInfo(name = "decided_at") val decidedAt: Long,
)

/** A head-to-head pick while trimming down to the target. */
@Entity(tableName = "highlight_comparison", indices = [Index("set_id")])
data class HighlightComparisonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "set_id") val setId: Long,
    @ColumnInfo(name = "winner_media_id") val winnerMediaId: Long,
    @ColumnInfo(name = "loser_media_id") val loserMediaId: Long,
    @ColumnInfo(name = "decided_at") val decidedAt: Long,
)
