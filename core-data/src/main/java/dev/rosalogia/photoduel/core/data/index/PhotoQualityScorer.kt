package dev.rosalogia.photoduel.core.data.index

/**
 * Heuristic "how good is this shot" score in [0, 1], used only to order a duel so strong candidates
 * meet early. It never decides the winner. Implemented in core-ml.
 */
interface PhotoQualityScorer {
    suspend fun preScore(mediaId: Long): Float
}
