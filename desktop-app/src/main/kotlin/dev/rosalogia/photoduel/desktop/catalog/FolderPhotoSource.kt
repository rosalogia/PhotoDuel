package dev.rosalogia.photoduel.desktop.catalog

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.jpeg.JpegDirectory
import dev.rosalogia.photoduel.core.common.AppLog
import dev.rosalogia.photoduel.core.data.media.MediaAccess
import dev.rosalogia.photoduel.core.data.media.MediaPhoto
import dev.rosalogia.photoduel.core.data.media.PhotoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Scan progress, for the UI: how many files were found and how many of the new ones were read so far. */
data class ScanState(val running: Boolean = false, val found: Int = 0, val read: Int = 0, val toRead: Int = 0)

/**
 * Camera photos from the folders the user chose. Each scan walks the folders, reads EXIF from new
 * or changed files only, and mirrors the result into the catalog, whose row IDs are the media IDs.
 */
class FolderPhotoSource(
    private val catalog: CatalogDatabase,
    private val folders: StateFlow<List<File>>,
    /** Never indexed, even inside a chosen folder: where highlight albums are written. */
    private val excluded: () -> List<File>,
) : PhotoSource {
    private val dao = catalog.dao()
    private val mutex = Mutex()
    private val watcher = FolderWatcher()
    private val _scan = MutableStateFlow(ScanState())
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    override fun currentAccess(): MediaAccess = if (folders.value.isEmpty()) MediaAccess.NONE else MediaAccess.FULL

    override suspend fun queryCameraPhotos(): List<MediaPhoto> = mutex.withLock {
        withContext(Dispatchers.IO) { rescan() }
        dao.all().map { f ->
            MediaPhoto(
                id = f.id,
                dateTaken = f.dateTaken,
                dateModified = f.modifiedMs / 1000,
                width = f.width,
                height = f.height,
                relativePath = File(f.path).parent,
            )
        }
    }

    override fun changes(): Flow<Unit> = merge(watcher.changes(folders), folders.toUnitChanges())

    suspend fun path(mediaId: Long): File? = dao.path(mediaId)?.let(::File)

    private suspend fun rescan() {
        val started = System.currentTimeMillis()
        val roots = folders.value.filter { it.isDirectory }
        val skip = excluded().map { it.absoluteFile.toPath().normalize() }
        _scan.value = ScanState(running = true)
        val onDisk = HashMap<String, Pair<Long, Long>>()
        for (root in roots) walk(root.toPath(), skip) { path, size, modified -> onDisk[path] = size to modified }

        val known = dao.stamps().associateBy { it.path }
        val removed = known.values.filter { it.path !in onDisk }.map { it.id }
        val toRead = onDisk.filter { (path, stamp) -> known[path]?.let { it.size to it.modifiedMs } != stamp }
        _scan.value = ScanState(running = true, found = onDisk.size, toRead = toRead.size)

        var read = 0
        val readStarted = System.currentTimeMillis()
        // Reading EXIF is mostly I/O and parsing; a few at a time keeps disks and cores busy.
        toRead.entries.chunked(256).forEach { chunk ->
            val entities = coroutineScope {
                chunk.map { (path, stamp) ->
                    async(Dispatchers.IO) { readFile(path, stamp.first, stamp.second, known[path]?.id ?: 0) }
                }.awaitAll()
            }
            val (updates, inserts) = entities.partition { it.id != 0L }
            if (inserts.isNotEmpty()) dao.insert(inserts)
            if (updates.isNotEmpty()) dao.update(updates)
            read += chunk.size
            _scan.value = _scan.value.copy(read = read)
        }
        removed.chunked(500).forEach { dao.delete(it) }
        _scan.value = ScanState(running = false, found = onDisk.size, read = read, toRead = toRead.size)
        AppLog.i(
            TAG,
            "Scanned ${roots.size} folders: ${onDisk.size} photos, ${toRead.size} new/changed " +
                "(read in ${System.currentTimeMillis() - readStarted}ms), ${removed.size} removed, " +
                "${System.currentTimeMillis() - started}ms total",
        )
    }

    private fun walk(root: Path, skip: List<Path>, onPhoto: (String, Long, Long) -> Unit) {
        val stack = ArrayDeque<Path>().apply { add(root) }
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val normalized = dir.toAbsolutePath().normalize()
            if (skip.any { normalized.startsWith(it) }) continue
            val entries = runCatching { Files.newDirectoryStream(dir).use { it.toList() } }
                .onFailure { AppLog.w(TAG, "Can't list $dir", it) }
                .getOrNull() ?: continue
            for (p in entries) {
                val name = p.fileName.toString()
                if (name.startsWith(".")) continue
                if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                    if (!SKIP_DIRS.any { name.equals(it, ignoreCase = true) }) stack.add(p)
                    continue
                }
                if (name.substringAfterLast('.', "").lowercase() !in EXTENSIONS) continue
                if (CloudFiles.isPlaceholder(p)) continue
                val attrs = runCatching { Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes::class.java) }.getOrNull() ?: continue
                onPhoto(p.toString(), attrs.size(), attrs.lastModifiedTime().toMillis())
            }
        }
    }

    private fun readFile(path: String, size: Long, modified: Long, id: Long): FileEntity {
        var taken: Long? = null
        var width = 0
        var height = 0
        try {
            val metadata = ImageMetadataReader.readMetadata(File(path))
            val exif = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
            val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            taken = exif?.let { captureTime(it) }
            width = exif?.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH)
                ?: metadata.getFirstDirectoryOfType(JpegDirectory::class.java)?.imageWidth ?: 0
            height = exif?.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT)
                ?: metadata.getFirstDirectoryOfType(JpegDirectory::class.java)?.imageHeight ?: 0
            val orientation = ifd0?.getInteger(ExifIFD0Directory.TAG_ORIENTATION) ?: 1
            if (orientation in 5..8) width = height.also { height = width }
        } catch (e: Exception) {
            AppLog.d(TAG, "No metadata for $path: ${e.message}")
        }
        return FileEntity(id = id, path = path, size = size, modifiedMs = modified, dateTaken = taken ?: modified, width = width, height = height)
    }

    /** DateTimeOriginal in its recorded offset if there is one, else in this computer's time zone, plus subseconds. */
    private fun captureTime(exif: ExifSubIFDDirectory): Long? {
        val text = exif.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)?.trim() ?: return null
        val local = runCatching { LocalDateTime.parse(text, EXIF_TIME) }.getOrNull() ?: return null
        val offset = exif.getString(ExifSubIFDDirectory.TAG_TIME_ZONE_ORIGINAL)?.trim()
            ?.let { runCatching { ZoneOffset.of(it) }.getOrNull() }
        val instant = if (offset != null) local.toInstant(offset) else local.atZone(ZoneId.systemDefault()).toInstant()
        val subsec = exif.getString(ExifSubIFDDirectory.TAG_SUBSECOND_TIME_ORIGINAL)?.trim()?.takeWhile { it.isDigit() }
        val ms = subsec?.takeIf { it.isNotEmpty() }?.padEnd(3, '0')?.take(3)?.toLong() ?: 0
        return instant.toEpochMilli() + ms
    }

    private fun StateFlow<List<File>>.toUnitChanges(): Flow<Unit> = kotlinx.coroutines.flow.flow {
        var last: List<File>? = null
        collect { if (last != null && it != last) emit(Unit); last = it }
    }

    private companion object {
        const val TAG = "Folders"
        val EXTENSIONS = setOf("jpg", "jpeg", "heic", "heif")
        /** Like the phone app, screenshots aren't camera photos. */
        val SKIP_DIRS = listOf("Screenshots", "\$RECYCLE.BIN", "System Volume Information")
        val EXIF_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
    }
}
