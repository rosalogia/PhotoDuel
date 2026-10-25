package dev.rosalogia.photoduel.core.data.grouping

/** A manual correction to automatic grouping. Re-applied, in creation order, after every re-cluster. */
sealed interface GroupingOverride {
    /** Takes the photo out of whatever group it lands in. It stays ungrouped from then on. */
    data class Exclude(val mediaId: Long) : GroupingOverride

    /** Joins the groups containing any of these photos, even across time sessions. */
    data class Merge(val mediaIds: Set<Long>) : GroupingOverride

    /**
     * Splits [firstPart] off from the group containing it. Photos later added to that group go with
     * the second part.
     */
    data class Split(val firstPart: Set<Long>) : GroupingOverride
}

/**
 * Applies [overrides] in order to a partition of photos ([clusters] must cover every photo once,
 * singletons included). Returns the new partition, with each cluster ordered by [order] (capture
 * order) and clusters ordered by their first photo.
 */
fun applyOverrides(clusters: List<List<Long>>, overrides: List<GroupingOverride>, order: Map<Long, Int>): List<List<Long>> {
    if (overrides.isEmpty()) return clusters
    var next = 0
    val clusterOf = HashMap<Long, Int>()
    clusters.forEach { c -> val id = next++; c.forEach { clusterOf[it] = id } }

    for (o in overrides) {
        when (o) {
            is GroupingOverride.Exclude -> if (o.mediaId in clusterOf) clusterOf[o.mediaId] = next++
            is GroupingOverride.Merge -> {
                val targets = o.mediaIds.mapNotNull { clusterOf[it] }.toSet()
                if (targets.size > 1) {
                    val into = targets.min()
                    clusterOf.replaceAll { _, c -> if (c in targets) into else c }
                }
            }
            is GroupingOverride.Split -> {
                val affected = o.firstPart.mapNotNull { clusterOf[it] }.toSet()
                for (c in affected) {
                    val newId = next++
                    for (id in o.firstPart) if (clusterOf[id] == c) clusterOf[id] = newId
                }
            }
        }
    }

    return clusterOf.entries.groupBy({ it.value }, { it.key })
        .values
        .map { c -> c.sortedBy { order[it] ?: Int.MAX_VALUE } }
        .sortedBy { order[it.first()] ?: Int.MAX_VALUE }
}
