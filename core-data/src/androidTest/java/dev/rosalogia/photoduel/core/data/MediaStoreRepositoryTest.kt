package dev.rosalogia.photoduel.core.data

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.google.common.truth.Truth.assertThat
import dev.rosalogia.photoduel.core.data.media.MediaAccess
import dev.rosalogia.photoduel.core.data.media.MediaStoreRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs against whatever is in the device gallery. Seed it first with tools/make_bursts.py and
 * tools/seed_device.sh, which put screenshots under DCIM/Screenshots that must be excluded.
 */
@RunWith(AndroidJUnit4::class)
class MediaStoreRepositoryTest {
    @get:Rule val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.READ_MEDIA_IMAGES)

    private val repo = MediaStoreRepository(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test
    fun returnsCameraPhotosOnlyWithCaptureTimes() = runTest {
        assertThat(repo.currentAccess()).isEqualTo(MediaAccess.FULL)
        val photos = repo.queryCameraPhotos()
        assumeTrue("Gallery not seeded", photos.isNotEmpty())
        photos.forEach {
            assertThat(it.relativePath).startsWith("DCIM/")
            assertThat(it.relativePath).doesNotContain("Screenshots")
            assertThat(it.dateTaken).isGreaterThan(0L)
            assertThat(it.width).isGreaterThan(0)
        }
        // Newest first, as the indexer expects.
        assertThat(photos.map { it.dateTaken }).isInOrder(Comparator.reverseOrder<Long>())
    }
}
