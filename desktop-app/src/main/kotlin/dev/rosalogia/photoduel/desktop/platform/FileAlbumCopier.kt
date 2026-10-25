package dev.rosalogia.photoduel.desktop.platform

import dev.rosalogia.photoduel.core.common.AppLog
import dev.rosalogia.photoduel.core.data.media.AlbumCopier
import dev.rosalogia.photoduel.desktop.catalog.AlbumCopyEntity
import dev.rosalogia.photoduel.desktop.catalog.CatalogDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Copies into <Pictures>/Highlights/<album>/, keeping each file's name and modification time. */
class FileAlbumCopier(
    private val root: File,
    private val dao: CatalogDao,
    private val pathOf: suspend (Long) -> File?,
) : AlbumCopier {
    override fun albumPath(album: String): String = folder(album).path + File.separator

    fun folder(album: String) = File(root, AlbumCopier.safeName(album))

    override suspend fun copy(mediaId: Long, album: String): Long = withContext(Dispatchers.IO) {
        val source = pathOf(mediaId) ?: error("Photo $mediaId isn't in the catalog")
        val dir = folder(album).apply { mkdirs() }
        var target = File(dir, source.name)
        var n = 2
        while (target.exists()) target = File(dir, "${source.nameWithoutExtension} ($n).${source.extension}").also { n++ }
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.COPY_ATTRIBUTES)
        AppLog.i("Album", "Copied $mediaId to $target")
        dao.insertCopy(AlbumCopyEntity(path = target.path))
    }

    override suspend fun exists(copyId: Long): Boolean = withContext(Dispatchers.IO) {
        dao.copyPath(copyId)?.let { File(it).exists() } ?: false
    }
}
