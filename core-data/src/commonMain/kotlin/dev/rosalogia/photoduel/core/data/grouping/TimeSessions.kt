package dev.rosalogia.photoduel.core.data.grouping

data class TimedPhoto(val mediaId: Long, val time: Long)

/**
 * Pass 1 of grouping: split photos into sessions wherever consecutive capture times are more
 * than [maxGapMs] apart. Returns every session, including singletons, each ordered by time.
 */
fun splitIntoSessions(photos: List<TimedPhoto>, maxGapMs: Long): List<List<TimedPhoto>> {
    if (photos.isEmpty()) return emptyList()
    val sorted = photos.sortedWith(compareBy<TimedPhoto> { it.time }.thenBy { it.mediaId })
    val sessions = mutableListOf<MutableList<TimedPhoto>>()
    var current = mutableListOf(sorted.first())
    for (i in 1 until sorted.size) {
        val p = sorted[i]
        if (p.time - current.last().time > maxGapMs) {
            sessions += current
            current = mutableListOf()
        }
        current += p
    }
    sessions += current
    return sessions
}
