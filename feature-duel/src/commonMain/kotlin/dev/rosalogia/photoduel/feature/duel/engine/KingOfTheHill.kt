package dev.rosalogia.photoduel.feature.duel.engine

data class Decision(val winner: Long, val loser: Long)

data class Matchup(val king: Long, val challenger: Long, /** 0-based. */ val step: Int, val totalSteps: Int)

/**
 * King-of-the-hill over [order]: the first photo starts as king and every other photo challenges
 * the current king exactly once, in order. Finds a winner in exactly n - 1 decisions.
 *
 * Immutable: [pick] and [undo] return new instances, so undo is just dropping the last decision.
 */
class KingOfTheHill private constructor(
    val order: List<Long>,
    val decisions: List<Decision>,
) {
    init {
        require(order.size >= 2) { "Need at least two photos, got ${order.size}" }
        require(order.toSet().size == order.size) { "Duplicate photos in $order" }
        require(decisions.size <= order.size - 1)
    }

    val king: Long = decisions.lastOrNull()?.winner ?: order.first()

    val totalSteps: Int get() = order.size - 1

    val isFinished: Boolean get() = decisions.size == totalSteps

    val current: Matchup?
        get() = if (isFinished) null else Matchup(king, order[decisions.size + 1], decisions.size, totalSteps)

    /** The photo after the current challenger, for prefetching. */
    val nextChallenger: Long? get() = order.getOrNull(decisions.size + 2)

    val winner: Long? get() = if (isFinished) king else null

    val canUndo: Boolean get() = decisions.isNotEmpty()

    fun pick(keepChallenger: Boolean): KingOfTheHill {
        val m = checkNotNull(current) { "Duel is finished" }
        val d = if (keepChallenger) Decision(m.challenger, m.king) else Decision(m.king, m.challenger)
        return KingOfTheHill(order, decisions + d)
    }

    fun undo(): KingOfTheHill {
        check(canUndo) { "Nothing to undo" }
        return KingOfTheHill(order, decisions.dropLast(1))
    }

    /**
     * Photos that lost head-to-head against the final winner: the strongest of the rest, and so the
     * candidates for a runner-up round. In original order.
     */
    fun runnerUpCandidates(): List<Long> {
        val w = checkNotNull(winner) { "Duel is not finished" }
        val beaten = decisions.filter { it.winner == w }.mapTo(HashSet()) { it.loser }
        return order.filter { it in beaten }
    }

    companion object {
        fun start(order: List<Long>) = KingOfTheHill(order, emptyList())

        /**
         * Rebuilds a duel from stored decisions, keeping the longest prefix that is still valid for
         * [order] (photos may have been added or removed since). Returns the duel and how many of
         * [stored] were used.
         */
        fun replay(order: List<Long>, stored: List<Decision>): Pair<KingOfTheHill, Int> {
            var duel = start(order)
            var used = 0
            for (d in stored) {
                val m = duel.current ?: break
                duel = when (d) {
                    Decision(m.challenger, m.king) -> duel.pick(keepChallenger = true)
                    Decision(m.king, m.challenger) -> duel.pick(keepChallenger = false)
                    else -> break
                }
                used++
            }
            return duel to used
        }
    }
}
