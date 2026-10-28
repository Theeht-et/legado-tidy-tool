package androidx.collection

/**
 * 平台桩：实现 androidx.collection.LruCache 的公开语义。
 *
 * 引擎用它缓存编译后的 JS 脚本（`JsSourceEngine` 注释说明：选它是因为
 * **内部方法自带 synchronized**，多源并发下不会被同一格挤爆）。
 * 所以这里的 synchronized 不是装饰 —— 去掉就可能在并发抓取时损坏缓存。
 */
open class LruCache<K : Any, V : Any>(private val maxSize: Int) {
    private val map = object : LinkedHashMap<K, V>(0, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean =
            size > maxSize
    }
    private var size = 0

    init {
        require(maxSize > 0) { "maxSize <= 0" }
    }

    @Synchronized
    operator fun get(key: K): V? {
        val v = map[key] ?: return null
        hitCount++
        return v
    }

    @Synchronized
    open fun put(key: K, value: V): V? {
        putCount++
        size += safeSizeOf(key, value)
        val previous = map.put(key, value)
        if (previous != null) size -= safeSizeOf(key, previous)
        trimToSize(maxSize)
        return previous
    }

    @Synchronized
    open fun remove(key: K): V? {
        val previous = map.remove(key)
        if (previous != null) size -= safeSizeOf(key, previous)
        return previous
    }

    @Synchronized
    open fun evictAll() {
        trimToSize(-1)
    }

    @Synchronized
    open fun size(): Int = size

    @Synchronized
    open fun snapshot(): MutableMap<K, V> = LinkedHashMap(map)

    private var hitCount = 0
    private var putCount = 0

    private fun safeSizeOf(key: K, value: V): Int =
        maxOf(0, sizeOf(key, value))

    private fun trimToSize(max: Int) {
        while (size > max) {
            val eldest = map.entries.firstOrNull() ?: break
            val key = eldest.key
            val value = eldest.value
            map.remove(key)
            size -= safeSizeOf(key, value)
            entryRemoved(true, key, value, null)
        }
    }

    protected open fun sizeOf(key: K, value: V): Int = 1

    protected open fun entryRemoved(evicted: Boolean, key: K, oldValue: V, newValue: V?) = Unit
}
