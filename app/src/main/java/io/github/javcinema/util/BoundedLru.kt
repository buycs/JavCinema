package io.github.javcinema.util

class BoundedLruMap<K, V>(private val maxSize: Int) {
    private val map = object : LinkedHashMap<K, V>(maxSize.coerceAtLeast(1), 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxSize
        }
    }

    @Synchronized
    operator fun get(key: K): V? = map[key]

    @Synchronized
    operator fun set(key: K, value: V) {
        map[key] = value
    }

    @Synchronized
    fun clear() {
        map.clear()
    }

    @Synchronized
    fun size(): Int = map.size

    @Synchronized
    fun containsKey(key: K): Boolean = map.containsKey(key)

    @Synchronized
    fun remove(key: K): V? = map.remove(key)
}

class BoundedLruSet<T>(maxSize: Int) {
    private val map = BoundedLruMap<T, Unit>(maxSize)

    fun contains(value: T): Boolean = map.containsKey(value)

    fun add(value: T) {
        map[value] = Unit
    }

    fun clear() {
        map.clear()
    }

    fun size(): Int = map.size()
}
