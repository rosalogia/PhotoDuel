package dev.rosalogia.photoduel.core.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Database(
    entities = [
        PhotoEntity::class,
        PhotoGroupEntity::class,
        GroupMemberEntity::class,
        GroupOverrideEntity::class,
        DuelDecisionEntity::class,
        HighlightSetEntity::class,
        HighlightItemEntity::class,
        HighlightCullEntity::class,
        HighlightComparisonEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
abstract class PhotoDuelDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
    abstract fun groupDao(): GroupDao
    abstract fun overrideDao(): OverrideDao
    abstract fun duelDao(): DuelDao
    abstract fun highlightDao(): HighlightDao
}

class Converters {
    @TypeConverter fun fromStatus(s: GroupStatus): String = s.name
    @TypeConverter fun toStatus(s: String): GroupStatus = GroupStatus.valueOf(s)
    @TypeConverter fun fromKind(k: OverrideKind): String = k.name
    @TypeConverter fun toKind(k: String): OverrideKind = OverrideKind.valueOf(k)
    @TypeConverter fun fromPhase(p: HighlightPhase): String = p.name
    @TypeConverter fun toPhase(p: String): HighlightPhase = HighlightPhase.valueOf(p)
}
