package dev.rosalogia.photoduel.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class Settings(
    /** Pass 1 grouping: a gap longer than this starts a new session. */
    val sessionGapMs: Long = 60_000L,
    /** Groups screen scope: false = last 30 days, true = all time. */
    val showAllTime: Boolean = false,
    /** Pass 2 grouping: average embedding similarity needed to group two shots. */
    val similarityThreshold: Float = 0.75f,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val sessionGapMs = longPreferencesKey("session_gap_ms")
        val showAllTime = booleanPreferencesKey("show_all_time")
        val similarityThreshold = floatPreferencesKey("similarity_threshold")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        val defaults = Settings()
        Settings(
            sessionGapMs = p[Keys.sessionGapMs] ?: defaults.sessionGapMs,
            showAllTime = p[Keys.showAllTime] ?: defaults.showAllTime,
            similarityThreshold = p[Keys.similarityThreshold] ?: defaults.similarityThreshold,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setShowAllTime(value: Boolean) {
        context.dataStore.edit { it[Keys.showAllTime] = value }
    }

    suspend fun setSimilarityThreshold(value: Float) {
        context.dataStore.edit { it[Keys.similarityThreshold] = value }
    }

    suspend fun setSessionGapMs(value: Long) {
        context.dataStore.edit { it[Keys.sessionGapMs] = value }
    }
}
