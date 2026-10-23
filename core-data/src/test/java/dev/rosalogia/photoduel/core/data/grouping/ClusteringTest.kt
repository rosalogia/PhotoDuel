package dev.rosalogia.photoduel.core.data.grouping

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ClusteringTest {
    /** Unit vector along [axis] with a small perturbation, so same-axis photos are ~0.99 similar. */
    private fun vec(axis: Int, jitter: Float = 0f) = FloatArray(8) { if (it == axis) 1f else if (it == 7) jitter else 0f }

    private fun signals(vararg pairs: Pair<Long, FloatArray?>, hashes: Map<Long, Long> = emptyMap()) =
        pairs.associate { (id, e) -> id to PhotoSignal(e, hashes[id]) }

    @Test
    fun separatesTwoScenesInOneSession() {
        val s = signals(1L to vec(0), 2L to vec(0, 0.1f), 3L to vec(0, 0.05f), 4L to vec(1), 5L to vec(1, 0.1f))
        val clusters = clusterSession(listOf(1L, 2L, 3L, 4L, 5L), s)
        assertThat(clusters).containsExactly(listOf(1L, 2L, 3L), listOf(4L, 5L)).inOrder()
    }

    @Test
    fun dissimilarPhotosStaySingletons() {
        val s = signals(1L to vec(0), 2L to vec(1), 3L to vec(2))
        assertThat(clusterSession(listOf(1L, 2L, 3L), s)).containsExactly(listOf(1L), listOf(2L), listOf(3L)).inOrder()
    }

    @Test
    fun nearIdenticalHashesJoinDespiteEmbedding() {
        val s = signals(1L to vec(0), 2L to vec(1), hashes = mapOf(1L to 0b1111L, 2L to 0b1110L))
        assertThat(clusterSession(listOf(1L, 2L), s)).containsExactly(listOf(1L, 2L))
    }

    @Test
    fun unindexedSessionFallsBackToTimeOnly() {
        val s = signals(1L to vec(0), 2L to null, 3L to vec(1))
        assertThat(clusterSession(listOf(1L, 2L, 3L), s)).containsExactly(listOf(1L, 2L, 3L))
    }

    @Test
    fun failedPhotoBecomesSingletonWithoutBlockingTheRest() {
        val s = signals(1L to vec(0), 3L to vec(0, 0.1f)) + (2L to PhotoSignal(null, null, indexed = true))
        assertThat(clusterSession(listOf(1L, 2L, 3L), s)).containsExactly(listOf(1L, 3L), listOf(2L)).inOrder()
    }

    @Test
    fun interleavedScenesKeepCaptureOrderWithinCluster() {
        val s = signals(1L to vec(0), 2L to vec(1), 3L to vec(0, 0.1f), 4L to vec(1, 0.1f))
        assertThat(clusterSession(listOf(1L, 2L, 3L, 4L), s))
            .containsExactly(listOf(1L, 3L), listOf(2L, 4L)).inOrder()
    }

    @Test
    fun averageLinkageDoesNotChainThroughAMiddlePhoto() {
        // a ~ b and b ~ c, but a and c are far apart: average linkage must not pull c in.
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0.94f, 0.34f)
        val c = floatArrayOf(0.77f, 0.64f)
        val s = signals(1L to a, 2L to b, 3L to c)
        assertThat(cosine(a, b)).isAtLeast(0.85f)
        assertThat(cosine(b, c)).isAtLeast(0.85f)
        val clusters = clusterSession(listOf(1L, 2L, 3L), s, ClusterParams(similarityThreshold = 0.9f))
        assertThat(clusters).hasSize(2)
        assertThat(clusters.map { it.size }).containsExactly(2, 1)
    }

    @Test
    fun embeddingBytesRoundTrip() {
        val v = floatArrayOf(0.5f, -1.25f, 3e-7f)
        assertThat(v.toBytes().toFloats().toList()).containsExactlyElementsIn(v.toList()).inOrder()
    }
}

class OverridesTest {
    private val order = (1L..10L).associateWith { it.toInt() }

    @Test
    fun excludeMakesPhotoASingleton() {
        val out = applyOverrides(listOf(listOf(1L, 2L, 3L)), listOf(GroupingOverride.Exclude(2)), order)
        assertThat(out).containsExactly(listOf(1L, 3L), listOf(2L))
    }

    @Test
    fun mergeJoinsGroupsAcrossSessions() {
        val out = applyOverrides(listOf(listOf(1L, 2L), listOf(3L), listOf(4L, 5L)), listOf(GroupingOverride.Merge(setOf(1, 2, 4, 5))), order)
        assertThat(out).containsExactly(listOf(1L, 2L, 4L, 5L), listOf(3L)).inOrder()
    }

    @Test
    fun mergeStillAppliesWhenGroupsGainPhotos() {
        // After the merge was recorded, photo 6 joined the second group's cluster.
        val out = applyOverrides(listOf(listOf(1L, 2L), listOf(4L, 5L, 6L)), listOf(GroupingOverride.Merge(setOf(1, 2, 4, 5))), order)
        assertThat(out).containsExactly(listOf(1L, 2L, 4L, 5L, 6L))
    }

    @Test
    fun splitSeparatesFirstPart_newPhotosGoWithSecond() {
        val out = applyOverrides(listOf(listOf(1L, 2L, 3L, 4L, 7L)), listOf(GroupingOverride.Split(setOf(1, 2))), order)
        assertThat(out).containsExactly(listOf(1L, 2L), listOf(3L, 4L, 7L)).inOrder()
    }

    @Test
    fun laterOverridesWin() {
        val overrides = listOf(GroupingOverride.Split(setOf(1, 2)), GroupingOverride.Merge(setOf(1, 3)))
        val out = applyOverrides(listOf(listOf(1L, 2L, 3L, 4L)), overrides, order)
        assertThat(out).containsExactly(listOf(1L, 2L, 3L, 4L))
    }

    @Test
    fun overridesForMissingPhotosAreIgnored() {
        val overrides = listOf(GroupingOverride.Exclude(9), GroupingOverride.Merge(setOf(9, 10)), GroupingOverride.Split(setOf(9)))
        val clusters = listOf(listOf(1L, 2L))
        assertThat(applyOverrides(clusters, overrides, order)).isEqualTo(clusters)
    }
}
