package dev.rosalogia.photoduel.core.data

import dev.rosalogia.photoduel.core.data.db.GroupStatus
import dev.rosalogia.photoduel.core.data.db.GroupWithMembers
import dev.rosalogia.photoduel.core.data.db.PhotoDuelDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class PhotoGroup(
    val id: Long,
    val status: GroupStatus,
    val startTime: Long,
    val endTime: Long,
    /** Ordered by capture time. */
    val mediaIds: List<Long>,
    val winnerMediaId: Long?,
    val runnerUpMediaId: Long?,
)

private fun GroupWithMembers.toModel() = PhotoGroup(
    id = group.groupId,
    status = group.status,
    startTime = group.startTime,
    endTime = group.endTime,
    mediaIds = orderedMediaIds,
    winnerMediaId = group.winnerMediaId,
    runnerUpMediaId = group.runnerUpMediaId,
)

@Singleton
class GroupRepository @Inject constructor(
    private val db: PhotoDuelDatabase,
) {
    fun observeGroups(status: GroupStatus, sinceMillis: Long): Flow<List<PhotoGroup>> =
        db.groupDao().observeGroups(status, sinceMillis).map { list -> list.map { it.toModel() } }

    fun observeGroup(groupId: Long): Flow<PhotoGroup?> =
        db.groupDao().observeGroup(groupId).map { it?.toModel() }

    /** Photos still waiting for visual features; their sessions are grouped by time only for now. */
    fun observeUnindexedCount(): Flow<Int> = db.photoDao().observeUnindexedCount()

    suspend fun getGroup(groupId: Long): PhotoGroup? = db.groupDao().getGroup(groupId)?.toModel()

    suspend fun setResult(groupId: Long, status: GroupStatus, winner: Long?, runnerUp: Long?) =
        db.groupDao().setResult(groupId, status, winner, runnerUp)
}
