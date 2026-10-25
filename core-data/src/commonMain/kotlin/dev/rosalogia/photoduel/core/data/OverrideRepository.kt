package dev.rosalogia.photoduel.core.data

import dev.rosalogia.photoduel.core.common.AppLog
import dev.rosalogia.photoduel.core.data.db.GroupOverrideEntity
import dev.rosalogia.photoduel.core.data.db.GroupStatus
import dev.rosalogia.photoduel.core.data.db.OverrideKind
import dev.rosalogia.photoduel.core.data.db.PhotoDuelDatabase
import dev.rosalogia.photoduel.core.data.grouping.GroupingOverride
import kotlinx.serialization.json.Json

/** Storage for manual grouping corrections. */
class OverrideRepository(
    private val db: PhotoDuelDatabase,
) {
    suspend fun all(): List<GroupingOverride> = db.overrideDao().getAll().mapNotNull { row ->
        val ids = runCatching { Json.decodeFromString<List<Long>>(row.mediaIds) }
            .onFailure { AppLog.w(TAG, "Unreadable override ${row.id}", it) }
            .getOrNull() ?: return@mapNotNull null
        when (row.kind) {
            OverrideKind.EXCLUDE -> ids.singleOrNull()?.let { GroupingOverride.Exclude(it) }
            OverrideKind.MERGE -> GroupingOverride.Merge(ids.toSet())
            OverrideKind.SPLIT -> GroupingOverride.Split(ids.toSet())
        }
    }

    suspend fun add(override: GroupingOverride) {
        val (kind, ids) = when (override) {
            is GroupingOverride.Exclude -> OverrideKind.EXCLUDE to listOf(override.mediaId)
            is GroupingOverride.Merge -> OverrideKind.MERGE to override.mediaIds.sorted()
            is GroupingOverride.Split -> OverrideKind.SPLIT to override.firstPart.sorted()
        }
        db.overrideDao().insert(
            GroupOverrideEntity(kind = kind, mediaIds = Json.encodeToString(ids), createdAt = System.currentTimeMillis()),
        )
        AppLog.i(TAG, "Added override $override")
    }

    private companion object {
        const val TAG = "Overrides"
    }
}

/** The group edits the user can make: remove a photo, split a group, merge two groups. */
class GroupEditor(
    private val db: PhotoDuelDatabase,
    private val overrides: OverrideRepository,
    private val indexer: LibraryIndexer,
) {
    suspend fun removePhoto(mediaId: Long) {
        overrides.add(GroupingOverride.Exclude(mediaId))
        indexer.regroup()
    }

    /** Splits [groupId] so that [firstOfSecondPart] and every later photo form a new group. */
    suspend fun splitBefore(groupId: Long, firstOfSecondPart: Long) {
        val members = db.groupDao().getGroup(groupId)?.orderedMediaIds ?: return
        val at = members.indexOf(firstOfSecondPart)
        if (at <= 0) return
        overrides.add(GroupingOverride.Split(members.subList(0, at).toSet()))
        indexer.regroup()
    }

    suspend fun merge(groupId: Long, otherGroupId: Long) {
        val a = db.groupDao().getGroup(groupId)?.orderedMediaIds ?: return
        val b = db.groupDao().getGroup(otherGroupId)?.orderedMediaIds ?: return
        overrides.add(GroupingOverride.Merge((a + b).toSet()))
        indexer.regroup()
    }

    /** The to-do groups just before and after [groupId] in capture time, for "merge with neighbor". */
    suspend fun neighbors(groupId: Long): Pair<Long?, Long?> {
        val all = db.groupDao().getAllGroups()
            .filter { it.group.status == GroupStatus.UNRESOLVED }
            .sortedBy { it.group.startTime }
        val i = all.indexOfFirst { it.group.groupId == groupId }
        if (i < 0) return null to null
        return all.getOrNull(i - 1)?.group?.groupId to all.getOrNull(i + 1)?.group?.groupId
    }
}
