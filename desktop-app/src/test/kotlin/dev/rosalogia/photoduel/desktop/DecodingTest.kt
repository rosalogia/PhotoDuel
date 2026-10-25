package dev.rosalogia.photoduel.desktop

import com.google.common.truth.Truth.assertThat
import dev.rosalogia.photoduel.desktop.image.Decoding
import dev.rosalogia.photoduel.desktop.image.LibHeif
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DecodingTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun fixture(name: String): File =
        tmp.newFile(name).also { f -> javaClass.getResourceAsStream("/fixtures/$name")!!.use { f.outputStream().use(it::copyTo) } }

    private fun isRed(argb: Int) = (argb shr 16 and 255) > 180 && (argb shr 8 and 255) < 90 && (argb and 255) < 90

    private fun assertUprightWithMarker(file: File) {
        val bitmap = Decoding.decode(file, 4096)
        assertThat(bitmap.width to bitmap.height).isEqualTo(64 to 48)
        val px = Decoding.argb(bitmap)
        assertThat(isRed(px[2 * 64 + 2])).isTrue()
        assertThat(isRed(px[2 * 64 + 61])).isFalse()
        assertThat(isRed(px[45 * 64 + 2])).isFalse()
    }

    @Test
    fun jpegDecodesUpright() = assertUprightWithMarker(fixture("with_offset.jpg"))

    @Test
    fun exifOrientationIsApplied() = assertUprightWithMarker(fixture("rotated.jpg"))

    @Test
    fun heicDecodesThroughLibheif() {
        assumeTrue("libheif not installed", LibHeif.available)
        assertUprightWithMarker(fixture("heic_photo.heic"))
    }

    @Test
    fun scalesDownToTheLongEdge() {
        val bitmap = Decoding.decode(fixture("rotated.jpg"), 32)
        assertThat(bitmap.width to bitmap.height).isEqualTo(32 to 24)
    }
}
