package dev.rosalogia.photoduel.desktop

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.google.common.truth.Truth.assertThat
import dev.rosalogia.photoduel.desktop.catalog.CatalogDatabase_Impl
import dev.rosalogia.photoduel.desktop.catalog.FolderPhotoSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.OffsetDateTime

class FolderPhotoSourceTest {
    @get:Rule val tmp = TemporaryFolder()

    private val catalog = Room.inMemoryDatabaseBuilder { CatalogDatabase_Impl() }.setDriver(BundledSQLiteDriver()).build()

    @After fun close() = catalog.close()

    private fun fixture(name: String, to: File): File =
        File(to, name).also { f -> f.parentFile.mkdirs(); javaClass.getResourceAsStream("/fixtures/$name")!!.use { f.outputStream().use(it::copyTo) } }

    @Test
    fun findsCameraPhotosWithCaptureTimesAndUprightSizes() = runTest {
        val root = tmp.newFolder("Pictures")
        fixture("with_offset.jpg", File(root, "2026/October"))
        fixture("rotated.jpg", root)
        fixture("heic_photo.heic", root)
        fixture("with_offset.jpg", File(root, "Screenshots"))
        fixture("with_offset.jpg", File(root, "Highlights/Trip"))
        File(root, "notes.txt").writeText("not a photo")
        val source = FolderPhotoSource(catalog, MutableStateFlow(listOf(root))) { listOf(File(root, "Highlights")) }

        val photos = source.queryCameraPhotos().associateBy { File(source.path(it.id)!!.path).name }

        assertThat(photos.keys).containsExactly("with_offset.jpg", "rotated.jpg", "heic_photo.heic")
        val expected = OffsetDateTime.parse("2026-10-01T12:00:00.250+02:00").toInstant().toEpochMilli()
        assertThat(photos.getValue("with_offset.jpg").dateTaken).isEqualTo(expected)
        assertThat(photos.getValue("heic_photo.heic").dateTaken).isEqualTo(expected)
        // Stored 48x64 but displayed upright after EXIF rotation.
        assertThat(photos.getValue("rotated.jpg").width to photos.getValue("rotated.jpg").height).isEqualTo(64 to 48)
    }

    @Test
    fun idsSurviveRescansAndEditsAndAreNeverReused() = runTest {
        val root = tmp.newFolder("Pictures")
        val a = fixture("with_offset.jpg", root)
        val b = fixture("rotated.jpg", root)
        val source = FolderPhotoSource(catalog, MutableStateFlow(listOf(root))) { emptyList() }
        val first = source.queryCameraPhotos().associate { source.path(it.id)!!.name to it.id }

        a.setLastModified(a.lastModified() + 5_000)
        b.delete()
        val second = source.queryCameraPhotos().associate { source.path(it.id)!!.name to it.id }
        assertThat(second).containsExactly("with_offset.jpg", first.getValue("with_offset.jpg"))

        fixture("rotated.jpg", root)
        val third = source.queryCameraPhotos().associate { source.path(it.id)!!.name to it.id }
        assertThat(third.getValue("rotated.jpg")).isNotEqualTo(first.getValue("rotated.jpg"))
    }
}
