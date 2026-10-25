package dev.rosalogia.photoduel.desktop.catalog

import dev.rosalogia.photoduel.desktop.AppDirs
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * Files that only exist in the cloud until opened (OneDrive "Files On-Demand" on Windows).
 * Reading one would download it, so they're left out until the user keeps them on the device.
 */
object CloudFiles {
    // FILE_ATTRIBUTE_OFFLINE and FILE_ATTRIBUTE_RECALL_ON_DATA_ACCESS.
    private const val OFFLINE = 0x1000
    private const val RECALL_ON_DATA_ACCESS = 0x400000

    fun isPlaceholder(path: Path): Boolean {
        if (!AppDirs.isWindows) return false
        val attributes = runCatching { Files.getAttribute(path, "dos:attributes", LinkOption.NOFOLLOW_LINKS) as Int }.getOrNull() ?: return false
        return attributes and (OFFLINE or RECALL_ON_DATA_ACCESS) != 0
    }
}
