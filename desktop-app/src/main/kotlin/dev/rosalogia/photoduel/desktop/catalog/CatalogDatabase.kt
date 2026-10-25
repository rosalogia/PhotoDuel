package dev.rosalogia.photoduel.desktop.catalog

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update

/**
 * One photo file on disk. The row ID is the photo's media ID everywhere else in the app, so it
 * must never be reused: AUTOINCREMENT guarantees that even after deletes.
 */
@Entity(tableName = "file", indices = [Index(value = ["path"], unique = true)])
data class FileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val size: Long,
    /** File modification time, ms. With [size], tells whether the file changed since it was read. */
    val modifiedMs: Long,
    /** Capture time from EXIF, falling back to [modifiedMs]. */
    val dateTaken: Long,
    /** As displayed, after EXIF rotation; 0 if unknown. */
    val width: Int,
    val height: Int,
)

/** A highlight album copy, so the shared code can ask whether it still exists. */
@Entity(tableName = "album_copy")
data class AlbumCopyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
)

data class FileStamp(val id: Long, val path: String, val size: Long, val modifiedMs: Long)

@Dao
interface CatalogDao {
    @Query("SELECT id, path, size, modifiedMs FROM file")
    suspend fun stamps(): List<FileStamp>

    @Query("SELECT * FROM file ORDER BY dateTaken DESC")
    suspend fun all(): List<FileEntity>

    @Query("SELECT * FROM file WHERE id = :id")
    suspend fun get(id: Long): FileEntity?

    @Query("SELECT path FROM file WHERE id = :id")
    suspend fun path(id: Long): String?

    @Insert
    suspend fun insert(files: List<FileEntity>)

    @Update
    suspend fun update(files: List<FileEntity>)

    @Query("DELETE FROM file WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Insert
    suspend fun insertCopy(copy: AlbumCopyEntity): Long

    @Query("SELECT path FROM album_copy WHERE id = :id")
    suspend fun copyPath(id: Long): String?
}

@Database(entities = [FileEntity::class, AlbumCopyEntity::class], version = 1, exportSchema = true)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun dao(): CatalogDao
}
