package dev.rosalogia.photoduel.core.data

import androidx.room.withTransaction
import dev.rosalogia.photoduel.core.common.AppLog
import dev.rosalogia.photoduel.core.data.db.GroupMemberEntity
import dev.rosalogia.photoduel.core.data.db.GroupStatus
import dev.rosalogia.photoduel.core.data.db.PhotoDuelDatabase
import dev.rosalogia.photoduel.core.data.db.PhotoEntity
import dev.rosalogia.photoduel.core.data.db.PhotoGroupEntity
import dev.rosalogia.photoduel.core.data.grouping.ClusterParams
import dev.rosalogia.photoduel.core.data.grouping.PhotoSignal
import dev.rosalogia.photoduel.core.data.grouping.TimedPhoto
import dev.rosalogia.photoduel.core.data.grouping.applyOverrides
import dev.rosalogia.photoduel.core.data.grouping.clusterSession
import dev.rosalogia.photoduel.core.data.grouping.describeClustering
import dev.rosalogia.photoduel.core.data.grouping.reconcileGroups
import dev.rosalogia.photoduel.core.data.grouping.splitIntoSessions
import dev.rosalogia.photoduel.core.data.grouping.toFloats
import dev.rosalogia.photoduel.core.data.index.IndexScheduler
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
 * Grouping is deterministic given the photos, their features, the settings and the overrides:
 * time sessions, then visual clusters within each session, then user overrides on top.
 *
 * Groups that are DONE or DISMISSED are frozen: their photos are left out of re-grouping so a
 * finished group never reappears. Their members are only removed when the media is gone.
 */
@Singleton
class LibraryIndexer @Inject constructor(
    private val media: MediaStoreRepository,
    private val db: PhotoDuelDatabase,
    private val settings: SettingsRepository,
    private val overrides: OverrideRepository,
    private val scheduler: IndexScheduler,
) {
    private val mutex = Mutex()
    /**
     * With partial access (Android 14+ "selected photos") MediaStore only returns what the user
     * picked, so a missing photo may just be hidden. Then nothing is deleted and only these are grouped.
     */
    private var visibleOnly: Set<Long>? = null
    private val _state = MutableStateFlow<IndexState>(IndexState.Idle)
    val state: StateFlow<IndexState> = _state.asStateFlow()

    /** Reads MediaStore, updates the photo table, regroups, and schedules feature extraction if needed. */
    suspend fun sync() = mutex.withLock {
        if (media.currentAccess() == MediaAccess.NONE) {
            AppLog.i(TAG, "No media access; skipping sync")
            return@withLock
        }
        _state.value = IndexState.Running
        try {
            val ms = measureTimeMillis {
                syncPhotosLocked()
                regroupLocked()
            }
            AppLog.i(TAG, "Sync finished in ${ms}ms")
            _state.value = IndexState.Idle
        } catch (e: Exception) {
            AppLog.e(TAG, "Sync failed", e)
            _state.value = IndexState.Failed(e.message ?: e.javaClass.simpleName)
        }
        val dao = db.photoDao()
        if (dao.unindexedCount() > 0 || dao.unscoredGroupMembers(1).isNotEmpty()) scheduler.schedule()
    }

    /**
     * Rebuilds groups from what's already in the database, e.g. after new features or a manual edit.
     * With [logStats], logs a similarity summary per multi-photo session for threshold tuning.
     */
    suspend fun regroup(logStats: Boolean = false) = mutex.withLock {
        try {
            regroupLocked(logStats)
        } catch (e: Exception) {
            AppLog.e(TAG, "Regroup failed", e)
        }
    }

    private suspend fun syncPhotosLocked() {
        val photos = media.queryCameraPhotos()
        val partial = media.currentAccess() == MediaAccess.PARTIAL
        visibleOnly = if (partial) photos.mapTo(HashSet()) { it.id } else null
        val photoDao = db.photoDao()
        val groupDao = db.groupDao()
        db.withTransaction {
            val existing = photoDao.getAllTimes().associate { it.mediaId to it.dateModified }
            val changed = photos.filter { p -> existing[p.id] != p.dateModified }
            if (changed.isNotEmpty()) {
                // A new or edited photo starts without features; the index worker fills them in.
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
            val removed = if (partial) emptyList() else existing.keys.filter { it !in currentIds }
            removed.chunked(500).forEach {
                photoDao.deleteByIds(it)
                groupDao.deleteMembersByMedia(it)
            }
            AppLog.d(
                TAG,
                "Photos: ${photos.size} visible, ${changed.size} new/changed, ${removed.size} removed" +
                    if (partial) " (partial access: not removing hidden photos)" else "",
            )
        }
    }

    private suspend fun regroupLocked(logStats: Boolean = false) {
        val photoDao = db.photoDao()
        val groupDao = db.groupDao()
        val s = settings.current()
        val params = ClusterParams(similarityThreshold = s.similarityThreshold)
        val overrideList = overrides.all()

        db.withTransaction {
            val photos = photoDao.getAllTimes()
            val groups = groupDao.getAllGroups()
            val (frozen, unresolved) = groups.partition { it.group.status != GroupStatus.UNRESOLVED }
            val emptyFrozen = frozen.filter { it.members.isEmpty() }.map { it.group.groupId }
            if (emptyFrozen.isNotEmpty()) groupDao.deleteGroups(emptyFrozen)
            val frozenIds = frozen.flatMapTo(HashSet()) { g -> g.members.map { it.mediaId } }

            val timeOf = photos.associate { it.mediaId to it.dateTaken }
            val visible = visibleOnly
            val candidates = photos
                .filter { it.mediaId !in frozenIds && (visible == null || it.mediaId in visible) }
                .map { TimedPhoto(it.mediaId, it.dateTaken) }
            val sessions = splitIntoSessions(candidates, s.sessionGapMs).map { session -> session.map { it.mediaId } }

            val needSignals = sessions.filter { it.size >= 2 }.flatten()
            val signals = HashMap<Long, PhotoSignal>(needSignals.size)
            needSignals.chunked(900).forEach { chunk ->
                photoDao.signals(chunk).forEach { r ->
                    signals[r.mediaId] = PhotoSignal(r.embedding?.toFloats(), r.dhash, indexed = r.indexedAt != null)
                }
            }
            val perSession = sessions.map { clusterSession(it, signals, params) }
            if (logStats) {
                AppLog.i(TAG, "Clustering at threshold ${params.similarityThreshold}:")
                perSession.filter { c -> c.sumOf { it.size } >= 2 }
                    .forEach { AppLog.i(TAG, "  " + describeClustering(it, signals)) }
            }
            val clusters = perSession.flatten()
            val order = sessions.flatten().withIndex().associate { (i, id) -> id to i }
            val newGroups = applyOverrides(clusters, overrideList, order).filter { it.size >= 2 }

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
                "Groups: ${plan.keep.size} kept, ${plan.create.size} created, ${plan.delete.size} deleted, " +
                    "${frozen.size} frozen; ${sessions.count { it.size >= 2 }} multi-photo sessions, ${overrideList.size} overrides",
            )
        }
    }

    private companion object {
        const val TAG = "Indexer"
    }
}
