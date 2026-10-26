package dev.rosalogia.photoduel.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Settings(
    /** Pass 1 grouping: a gap longer than this starts a new session. */
    val sessionGapMs: Long = 60_000L,
    /** Groups screen scope: false = last 30 days, true = all time. */
    val showAllTime: Boolean = false,
    /** Pass 2 grouping: average embedding similarity needed to group two shots. */
    val similarityThreshold: Float = 0.75f,
    /** Photos moved to the trash from a duel, for the tally on the "All caught up" screen. */
    val trashedCount: Int = 0,
)

/** [dataStore] is the "settings" preferences file (on Android, the one preferencesDataStore("settings") used). */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val sessionGapMs = longPreferencesKey("session_gap_ms")
        val showAllTime = booleanPreferencesKey("show_all_time")
        val similarityThreshold = floatPreferencesKey("similarity_threshold")
        val trashedCount = intPreferencesKey("trashed_count")
    }

    val settings: Flow<Settings> = dataStore.data.map { p ->
        val defaults = Settings()
        Settings(
            sessionGapMs = p[Keys.sessionGapMs] ?: defaults.sessionGapMs,
            showAllTime = p[Keys.showAllTime] ?: defaults.showAllTime,
            similarityThreshold = p[Keys.similarityThreshold] ?: defaults.similarityThreshold,
            trashedCount = p[Keys.trashedCount] ?: defaults.trashedCount,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setShowAllTime(value: Boolean) {
        dataStore.edit { it[Keys.showAllTime] = value }
    }

    suspend fun setSimilarityThreshold(value: Float) {
        dataStore.edit { it[Keys.similarityThreshold] = value }
    }

    suspend fun addTrashed(count: Int) {
        dataStore.edit { it[Keys.trashedCount] = (it[Keys.trashedCount] ?: 0) + count }
    }

    suspend fun setSessionGapMs(value: Long) {
        dataStore.edit { it[Keys.sessionGapMs] = value }
    }
}
