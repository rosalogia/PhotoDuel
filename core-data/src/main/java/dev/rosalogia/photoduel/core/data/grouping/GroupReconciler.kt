package dev.rosalogia.photoduel.core.data.grouping

/**
 * What to do to the stored unresolved groups so they match a freshly computed grouping.
 *
 * Re-grouping runs on every library change, so a group that only gained or lost a photo keeps its
 * ID (and with it any duel history); only genuinely new clusters get new IDs.
 */
data class ReconcilePlan(
    /** Existing group ID to its new ordered member list. */
    val keep: Map<Long, List<Long>>,
    /** New groups to insert, each an ordered member list. */
    val create: List<List<Long>>,
    /** Existing group IDs that no longer correspond to any group. */
    val delete: Set<Long>,
)

/**
 * Matches each new group to the old group it overlaps most, greedily by overlap size. Each old
 * group can be claimed by at most one new group.
 */
fun reconcileGroups(old: Map<Long, Set<Long>>, new: List<List<Long>>): ReconcilePlan {
    val ownerOf = HashMap<Long, Long>()
    old.forEach { (gid, members) -> members.forEach { ownerOf[it] = gid } }

    data class Candidate(val newIndex: Int, val oldId: Long, val overlap: Int)

    val candidates = new.flatMapIndexed { i, members ->
        members.mapNotNull { ownerOf[it] }.groupingBy { it }.eachCount()
            .map { (oldId, n) -> Candidate(i, oldId, n) }
    }.sortedWith(compareByDescending<Candidate> { it.overlap }.thenBy { it.oldId }.thenBy { it.newIndex })

    val assignedNew = HashMap<Int, Long>()
    val claimedOld = HashSet<Long>()
    for (c in candidates) {
        if (c.newIndex in assignedNew || c.oldId in claimedOld) continue
        assignedNew[c.newIndex] = c.oldId
        claimedOld += c.oldId
    }

    val keep = assignedNew.entries.associate { (i, oldId) -> oldId to new[i] }
    val create = new.indices.filter { it !in assignedNew }.map { new[it] }
    val delete = old.keys - claimedOld
    return ReconcilePlan(keep, create, delete)
}
