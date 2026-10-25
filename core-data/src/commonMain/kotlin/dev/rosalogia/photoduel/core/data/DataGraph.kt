package dev.rosalogia.photoduel.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dev.rosalogia.photoduel.core.data.db.PhotoDuelDatabase
import dev.rosalogia.photoduel.core.data.highlights.HighlightRepository
import dev.rosalogia.photoduel.core.data.index.FeatureIndexer
import dev.rosalogia.photoduel.core.data.index.IndexScheduler
import dev.rosalogia.photoduel.core.data.index.PhotoFeatureExtractor
import dev.rosalogia.photoduel.core.data.index.PhotoQualityScorer
import dev.rosalogia.photoduel.core.data.media.AlbumCopier
import dev.rosalogia.photoduel.core.data.media.PhotoSource
import dev.rosalogia.photoduel.core.data.settings.SettingsRepository

/**
 * The data layer's singletons, wired by hand. Each app builds one from its platform's pieces:
 * the database, the settings file, where photos come from, and the ML that reads them.
 */
class DataGraph(
    val db: PhotoDuelDatabase,
    settingsStore: DataStore<Preferences>,
    val photos: PhotoSource,
    val albums: AlbumCopier,
    val scheduler: IndexScheduler,
    featureExtractor: PhotoFeatureExtractor,
    qualityScorer: PhotoQualityScorer,
) {
    val settings = SettingsRepository(settingsStore)
    val overrides = OverrideRepository(db)
    val indexer = LibraryIndexer(photos, db, settings, overrides, scheduler)
    val coordinator = IndexingCoordinator(indexer, photos, settings)
    val groups = GroupRepository(db)
    val duels = DuelRepository(db)
    val editor = GroupEditor(db, overrides, indexer)
    val preScores = PreScoreRepository(db, qualityScorer)
    val highlights = HighlightRepository(db)
    val featureIndexer = FeatureIndexer(db, featureExtractor, indexer, preScores)
}
