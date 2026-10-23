package dev.rosalogia.photoduel.core.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.rosalogia.photoduel.core.data.db.DuelDecisionEntity
import dev.rosalogia.photoduel.core.data.db.GroupMemberEntity
import dev.rosalogia.photoduel.core.data.db.GroupStatus
import dev.rosalogia.photoduel.core.data.db.PhotoDuelDatabase
import dev.rosalogia.photoduel.core.data.db.PhotoEntity
import dev.rosalogia.photoduel.core.data.db.PhotoGroupEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTest {
    private lateinit var db: PhotoDuelDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, PhotoDuelDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    private fun photo(id: Long, t: Long) = PhotoEntity(id, dateTaken = t, dateModified = 1, width = 4, height = 3, relativePath = "DCIM/Camera/")

    private suspend fun group(status: GroupStatus, start: Long, vararg ids: Long): Long {
        val gid = db.groupDao().insertGroup(PhotoGroupEntity(createdAt = 0, status = status, startTime = start, endTime = start + 10))
        db.groupDao().insertMembers(ids.mapIndexed { i, id -> GroupMemberEntity(gid, id, i) })
        return gid
    }

    @Test
    fun groupsAreFilteredByStatusAndDateAndNewestFirst() = runTest {
        db.photoDao().upsertAll((1L..6L).map { photo(it, it * 100) })
        val old = group(GroupStatus.UNRESOLVED, 100, 1, 2)
        val recent = group(GroupStatus.UNRESOLVED, 500, 5, 6)
        group(GroupStatus.DONE, 300, 3, 4)

        val todo = db.groupDao().observeGroups(GroupStatus.UNRESOLVED, sinceMillis = 0).first()
        assertThat(todo.map { it.group.groupId }).containsExactly(recent, old).inOrder()
        assertThat(todo.first().orderedMediaIds).containsExactly(5L, 6L).inOrder()
        assertThat(db.groupDao().observeGroups(GroupStatus.UNRESOLVED, sinceMillis = 400).first().map { it.group.groupId })
            .containsExactly(recent)
    }

    @Test
    fun unindexedNewestFirstAndFeaturesRoundTrip() = runTest {
        db.photoDao().upsertAll(listOf(photo(1, 100), photo(2, 300), photo(3, 200)))
        assertThat(db.photoDao().unindexedIds(10)).containsExactly(2L, 3L, 1L).inOrder()
        db.photoDao().setFeatures(2, byteArrayOf(1, 2, 3, 4), 42L, indexedAt = 7)
        assertThat(db.photoDao().unindexedCount()).isEqualTo(2)
        val s = db.photoDao().signals(listOf(2)).single()
        assertThat(s.dhash).isEqualTo(42L)
        assertThat(s.embedding!!.toList()).containsExactly(1.toByte(), 2.toByte(), 3.toByte(), 4.toByte()).inOrder()
    }

    @Test
    fun unscoredMembersOnlyFromTodoGroups() = runTest {
        db.photoDao().upsertAll((1L..4L).map { photo(it, it) })
        group(GroupStatus.UNRESOLVED, 1, 1, 2)
        group(GroupStatus.DONE, 3, 3, 4)
        db.photoDao().setPreScore(2, 0.5f)
        assertThat(db.photoDao().unscoredGroupMembers(10)).containsExactly(1L)
    }

    @Test
    fun undoneDecisionsAreNotActive() = runTest {
        val dao = db.duelDao()
        val a = dao.insert(DuelDecisionEntity(groupId = 1, winnerMediaId = 1, loserMediaId = 2, decidedAt = 1, flipCount = 0))
        dao.insert(DuelDecisionEntity(groupId = 1, winnerMediaId = 1, loserMediaId = 3, decidedAt = 2, flipCount = 2))
        dao.markUndone(a)
        assertThat(dao.activeDecisions(1).map { it.loserMediaId }).containsExactly(3L)
        dao.clearGroup(1)
        assertThat(dao.activeDecisions(1)).isEmpty()
    }
}
