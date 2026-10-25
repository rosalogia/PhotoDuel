package dev.rosalogia.photoduel.desktop

import com.google.common.truth.Truth.assertThat
import dev.rosalogia.photoduel.desktop.ui.displayPath
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.io.File

class DisplayPathTest {
    private val home = System.getProperty("user.home")

    @Test
    fun shortensHomeToTilde() {
        assumeFalse(AppDirs.isWindows)
        assertThat(displayPath(File(home, "Pictures/Camera"))).isEqualTo("~/Pictures/Camera")
        assertThat(displayPath(File(home))).isEqualTo("~")
    }

    @Test
    fun leavesOtherPathsAlone() {
        assertThat(displayPath(File("/mnt/photos"))).isEqualTo(File("/mnt/photos").path)
        // A sibling whose name merely starts with the home folder's isn't inside it.
        assertThat(displayPath(File(home + "2/Pictures"))).isEqualTo(File(home + "2/Pictures").path)
    }
}
