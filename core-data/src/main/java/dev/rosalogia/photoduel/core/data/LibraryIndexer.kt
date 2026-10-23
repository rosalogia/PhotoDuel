package dev.rosalogia.photoduel.core.data

import androidx.room.withTransaction
import dev.rosalogia.photoduel.core.common.AppLog
import dev.rosalogia.photoduel.core.data.db.GroupMemberEntity
import dev.rosalogia.photoduel.core.data.db.GroupStatus
import dev.rosalogia.photoduel.core.data.db.PhotoDuelDatabase
import dev.rosalogia.photoduel.core.data.db.PhotoEntity
import dev.rosalogia.photoduel.core.data.db.PhotoGroupEntity
import dev.rosalogia.photoduel.core.data.grouping.TimedPhoto
import dev.rosalogia.photoduel.core.data.grouping.reconcileGroups
import dev.rosalogia.photoduel.core.data.grouping.splitIntoSessions
import dev.rosalogia.photoduel.core.data.media.MediaAccess
import dev.rosalogia.photoduel.core.data.media.MediaStoreRepository
import dev.rosalogia.photoduel.core.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

sealed interface IndexState {
    data object Idle : IndexState
    data object Running : IndexState
    data class Failed(val message: String) : IndexState
}

/**
 * Mirrors the camera roll into the `photo` table and rebuilds unresolved groups.
 *
 * Groups that are DONE or DISMISSED are frozen: their photos are left out of re-grouping so a
 * finished group never reappears. Their members are only removed when the media is gone.
 */
@Singleton
class LibraryIndexer @Inject constructor(
    private val media: MediaStoreRepository,
    private val db: PhotoDuelDatabase,
    private val settings: SettingsRepository,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<IndexState>(IndexState.Idle)
    val state: StateFlow<IndexState> = _state.asStateFlow()

    suspend fun sync() = mutex.withLock {
        if (media.currentAccess() == MediaAccess.NONE) {
            AppLog.i(TAG, "No media access; skipping sync")
            return@withLock
        }
        _state.value = IndexState.Running
        try {
            val ms = measureTimeMillis { syncLocked() }
            AppLog.i(TAG, "Sync finished in ${ms}ms")
            _state.value = IndexState.Idle
        } catch (e: Exception) {
            AppLog.e(TAG, "Sync failed", e)
            _state.value = IndexState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun syncLocked() {
        val photos = media.queryCameraPhotos()
        val photoDao = db.photoDao()
        val groupDao = db.groupDao()
        val gapMs = settings.current().sessionGapMs

        db.withTransaction {
            val existing = photoDao.getAll().associateBy { it.mediaId }
            val changed = photos.filter { p -> existing[p.id]?.dateModified != p.dateModified }
            if (changed.isNotEmpty()) {
                photoDao.upsertAll(
                    changed.map {
                        PhotoEntity(
                            mediaId = it.id,
                            dateTaken = it.dateTaken,
                            dateModified = it.dateModified,
                            width = it.width,
                            height = it.height,
                            relativePath = it.relativePath,
                        )
                    },
                )
            }
            val currentIds = photos.mapTo(HashSet()) { it.id }
            val removed = existing.keys.filter { it !in currentIds }
            if (removed.isNotEmpty()) {
                removed.chunked(500).forEach {
                    photoDao.deleteByIds(it)
                    groupDao.deleteMembersByMedia(it)
                }
            }
            AppLog.d(TAG, "Photos: ${photos.size} total, ${changed.size} new/changed, ${removed.size} removed")

            val groups = groupDao.getAllGroups()
            val (frozen, unresolved) = groups.partition { it.group.status != GroupStatus.UNRESOLVED }
            val emptyFrozen = frozen.filter { it.members.isEmpty() }.map { it.group.groupId }
            if (emptyFrozen.isNotEmpty()) groupDao.deleteGroups(emptyFrozen)
            val frozenIds = frozen.flatMapTo(HashSet()) { g -> g.members.map { it.mediaId } }

            val timeOf = photos.associate { it.id to it.dateTaken }
            val candidates = photos.filter { it.id !in frozenIds }.map { TimedPhoto(it.id, it.dateTaken) }
            val newGroups = splitIntoSessions(candidates, gapMs)
                .filter { it.size >= 2 }
                .map { session -> session.map { it.mediaId } }

            val plan = reconcileGroups(
                old = unresolved.associate { g -> g.group.groupId to g.members.mapTo(HashSet()) { it.mediaId } },
                new = newGroups,
            )
            val now = System.currentTimeMillis()

            suspend fun writeMembers(groupId: Long, ids: List<Long>) {
                groupDao.deleteMembersOf(groupId)
                groupDao.insertMembers(ids.mapIndexed { i, id -> GroupMemberEntity(groupId, id, i) })
                groupDao.updateTimes(groupId, timeOf.getValue(ids.first()), timeOf.getValue(ids.last()))
            }

            val oldMembers = unresolved.associate { it.group.groupId to it.orderedMediaIds }
            plan.keep.forEach { (gid, ids) -> if (oldMembers[gid] != ids) writeMembers(gid, ids) }
            plan.create.forEach { ids ->
                val gid = groupDao.insertGroup(
                    PhotoGroupEntity(createdAt = now, startTime = timeOf.getValue(ids.first()), endTime = timeOf.getValue(ids.last())),
                )
                writeMembers(gid, ids)
            }
            if (plan.delete.isNotEmpty()) {
                plan.delete.forEach { groupDao.deleteMembersOf(it) }
                groupDao.deleteGroups(plan.delete.toList())
            }
            AppLog.d(
                TAG,
                "Groups: ${plan.keep.size} kept, ${plan.create.size} created, ${plan.delete.size} deleted, ${frozen.size} frozen",
            )
        }
    }

    private companion object {
        const val TAG = "Indexer"
    }
}
