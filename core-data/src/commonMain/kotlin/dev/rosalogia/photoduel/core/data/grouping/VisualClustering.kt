package dev.rosalogia.photoduel.core.data.grouping

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

data class ClusterParams(
    /** Average cosine similarity needed to join two clusters. */
    val similarityThreshold: Float = 0.75f,
    /** dHash Hamming distance at or below which two photos are near-duplicates whatever the embedding says. */
    val nearDuplicateBits: Int = 6,
    /** Sessions longer than this are clustered in consecutive chunks to bound the O(n³) cost. */
    val maxChunk: Int = 300,
)

/** [indexed] with a null [embedding] means feature extraction failed for this photo. */
class PhotoSignal(val embedding: FloatArray?, val dhash: Long?, val indexed: Boolean = embedding != null)

fun cosine(a: FloatArray, b: FloatArray): Float {
    var dot = 0f
    var na = 0f
    var nb = 0f
    for (i in a.indices) {
        dot += a[i] * b[i]
        na += a[i] * a[i]
        nb += b[i] * b[i]
    }
    val d = sqrt(na) * sqrt(nb)
    return if (d == 0f) 0f else dot / d
}

fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

/**
 * Pass 2 of grouping: splits one time session (ordered by capture time) into visual clusters by
 * average-linkage agglomerative clustering. Returns every cluster, singletons included, each in
 * session order. If any photo in the session isn't indexed yet, the whole session is returned as
 * one cluster: time-only grouping until its features arrive. Photos whose extraction failed are
 * left as singletons.
 */
fun clusterSession(ids: List<Long>, signals: Map<Long, PhotoSignal>, params: ClusterParams = ClusterParams()): List<List<Long>> {
    if (ids.size < 2) return listOf(ids)
    if (ids.any { signals[it]?.indexed != true }) return listOf(ids)
    val failed = ids.filter { signals.getValue(it).embedding == null }
    if (failed.isNotEmpty()) {
        val position = ids.withIndex().associate { (i, id) -> id to i }
        return (clusterSession(ids - failed.toSet(), signals, params) + failed.map { listOf(it) })
            .filter { it.isNotEmpty() }
            .sortedBy { position.getValue(it.first()) }
    }
    if (ids.size > params.maxChunk) return ids.chunked(params.maxChunk).flatMap { clusterSession(it, signals, params) }

    val n = ids.size
    val emb = ids.map { signals.getValue(it).embedding!! }
    val hash = ids.map { signals.getValue(it).dhash }
    val sim = Array(n) { FloatArray(n) }
    for (i in 0 until n) for (j in i + 1 until n) {
        var s = cosine(emb[i], emb[j])
        val hi = hash[i]
        val hj = hash[j]
        if (hi != null && hj != null && hamming(hi, hj) <= params.nearDuplicateBits) s = 1f
        sim[i][j] = s
        sim[j][i] = s
    }

    // Lance–Williams update for average linkage: s(A∪B, C) = (|A|·s(A,C) + |B|·s(B,C)) / (|A|+|B|).
    val members = Array(n) { mutableListOf(it) }
    val alive = BooleanArray(n) { true }
    while (true) {
        var best = -2f
        var bi = -1
        var bj = -1
        for (i in 0 until n) {
            if (!alive[i]) continue
            for (j in i + 1 until n) {
                if (alive[j] && sim[i][j] > best) {
                    best = sim[i][j]
                    bi = i
                    bj = j
                }
            }
        }
        if (bi < 0 || best < params.similarityThreshold) break
        val sa = members[bi].size.toFloat()
        val sb = members[bj].size.toFloat()
        for (k in 0 until n) {
            if (!alive[k] || k == bi || k == bj) continue
            val s = (sa * sim[bi][k] + sb * sim[bj][k]) / (sa + sb)
            sim[bi][k] = s
            sim[k][bi] = s
        }
        members[bi].addAll(members[bj])
        alive[bj] = false
    }
    return (0 until n).filter { alive[it] }
        .map { c -> members[c].sorted().map { ids[it] } }
        .sortedBy { ids.indexOf(it.first()) }
}

/**
 * One-line similarity summary of how a session was clustered, for tuning the threshold from shared
 * logs: the weakest pair inside any cluster and the strongest pair across clusters.
 */
fun describeClustering(clusters: List<List<Long>>, signals: Map<Long, PhotoSignal>): String {
    val emb = clusters.flatten().associateWith { signals[it]?.embedding }
    if (emb.values.any { it == null }) return "n=${emb.size} unindexed"
    var withinMin = Float.NaN
    var betweenMax = Float.NaN
    val all = clusters.flatMapIndexed { ci, c -> c.map { it to ci } }
    for (i in all.indices) for (j in i + 1 until all.size) {
        val s = cosine(emb.getValue(all[i].first)!!, emb.getValue(all[j].first)!!)
        if (all[i].second == all[j].second) {
            if (withinMin.isNaN() || s < withinMin) withinMin = s
        } else if (betweenMax.isNaN() || s > betweenMax) {
            betweenMax = s
        }
    }
    fun f(x: Float) = if (x.isNaN()) "-" else "%.3f".format(x)
    return "n=${emb.size} clusters=${clusters.map { it.size }} withinMin=${f(withinMin)} betweenMax=${f(betweenMax)}"
}

fun FloatArray.toBytes(): ByteArray =
    ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN).also { b -> forEach { b.putFloat(it) } }.array()

fun ByteArray.toFloats(): FloatArray {
    val b = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    return FloatArray(size / 4) { b.getFloat() }
}
