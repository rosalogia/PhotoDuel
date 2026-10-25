package dev.rosalogia.photoduel.core.common

/** A small thread-safe least-recently-used cache, like Android's LruCache but counting entries only. */
class LruCache<K : Any, V : Any>(private val maxEntries: Int) {
    private val map = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = size > maxEntries
    }

    @Synchronized fun get(key: K): V? = map[key]

    @Synchronized fun put(key: K, value: V) {
        map[key] = value
    }

    @Synchronized fun remove(key: K): V? = map.remove(key)
}
