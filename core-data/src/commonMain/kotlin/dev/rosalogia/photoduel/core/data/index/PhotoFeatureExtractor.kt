package dev.rosalogia.photoduel.core.data.index

class PhotoFeatures(
    /** L2-normalized image embedding. */
    val embedding: FloatArray,
    /** 64-bit difference hash. */
    val dhash: Long,
)

/** Computes the per-photo signals used for visual grouping. Implemented in core-ml. */
interface PhotoFeatureExtractor {
    suspend fun extract(mediaId: Long): PhotoFeatures
}
