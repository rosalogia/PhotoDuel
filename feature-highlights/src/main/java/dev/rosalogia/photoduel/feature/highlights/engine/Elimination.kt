package dev.rosalogia.photoduel.feature.highlights.engine

import kotlin.math.abs

data class Comparison(val winner: Long, val loser: Long)

/**
 * Narrows [items] down to [target] with head-to-head picks. A photo is out after losing twice, so
 * one unlucky matchup against an even better shot doesn't cost a great photo its place.
 *
 * Pairing is deterministic (it depends only on the items and the picks so far), which lets a
 * stored history be replayed. Each step pairs the photo that has played least with an opponent
 * with the same record, preferring one it hasn't met, then one taken close in time.
 */
class Elimination(val items: List<Long>, val target: Int) {
    private val index = items.withIndex().associate { it.value to it.index }
    private val losses = HashMap<Long, Int>()
    private val played = HashMap<Long, Int>()
    private val met = HashSet<Pair<Long, Long>>()
    private val _history = ArrayList<Comparison>()
    val history: List<Comparison> get() = _history

    val active: List<Long> get() = items.filter { lossesOf(it) < LIVES }
    val isFinished: Boolean get() = active.size <= target

    fun lossesOf(id: Long): Int = losses[id] ?: 0

    private fun winsOf(id: Long): Int = (played[id] ?: 0) - lossesOf(id)

    /** The next pair to compare, or null once only [target] photos are left. */
    fun next(): Pair<Long, Long>? {
        if (isFinished) return null
        val pool = active
        val a = pool.minWith(compareBy<Long>({ played[it] ?: 0 }, { index.getValue(it) }))
        val b = (pool - a).minWith(
            compareBy<Long>(
                { if (lossesOf(it) == lossesOf(a)) 0 else 1 },
                { abs(winsOf(it) - winsOf(a)) },
                { if (key(a, it) in met) 1 else 0 },
                { played[it] ?: 0 },
                { abs(index.getValue(it) - index.getValue(a)) },
                { index.getValue(it) },
            ),
        )
        return a to b
    }

    fun record(winner: Long, loser: Long) {
        val pair = checkNotNull(next()) { "Already finished" }
        require(setOf(winner, loser) == setOf(pair.first, pair.second)) { "Not the current pair" }
        losses[loser] = lossesOf(loser) + 1
        played[winner] = (played[winner] ?: 0) + 1
        played[loser] = (played[loser] ?: 0) + 1
        met += key(winner, loser)
        _history += Comparison(winner, loser)
    }

    /**
     * A rough count of picks still needed. Every pick hands out one loss: two for each photo cut,
     * plus, as simulation shows, a loss for about [SURVIVOR_LOSS_RATE] of the photos that stay.
     * Never less than the losses the closest-to-out photos still need.
     */
    fun remainingEstimate(): Int {
        val pool = active
        val need = pool.size - target
        if (need <= 0) return 0
        val atLeast = pool.map { LIVES - lossesOf(it) }.sorted().take(need).sum()
        val expectedTotal = LIVES * (items.size - target) + SURVIVOR_LOSS_RATE * target
        return maxOf(atLeast, Math.round(expectedTotal - _history.size).toInt())
    }

    private fun key(x: Long, y: Long) = if (x < y) x to y else y to x

    companion object {
        const val LIVES = 2
        private const val SURVIVOR_LOSS_RATE = 0.7

        /** Replays [history] as far as it still fits; returns the engine and how many were used. */
        fun replay(items: List<Long>, target: Int, history: List<Comparison>): Pair<Elimination, Int> {
            val e = Elimination(items, target)
            var used = 0
            for (c in history) {
                val pair = e.next() ?: break
                if (setOf(c.winner, c.loser) != setOf(pair.first, pair.second)) break
                e.record(c.winner, c.loser)
                used++
            }
            return e to used
        }
    }
}
