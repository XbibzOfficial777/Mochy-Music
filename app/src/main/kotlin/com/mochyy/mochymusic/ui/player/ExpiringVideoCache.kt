package com.mochyy.mochymusic.ui.player

/** Small, synchronized LRU cache. Failed lookups are not cached and signed URLs expire. */
internal class ExpiringVideoCache<T : Any>(
    private val maxSize: Int = 64,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private data class Entry<T>(val value: T, val expiresAtMs: Long)
    private val entries = LinkedHashMap<String, Entry<T>>(maxSize, 0.75f, true)

    @Synchronized
    fun get(key: String): T? {
        val entry = entries[key] ?: return null
        if (nowMs() >= entry.expiresAtMs) {
            entries.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun put(key: String, value: T, ttlMs: Long) {
        if (key.isBlank() || ttlMs <= 0 || maxSize <= 0) return
        val now = nowMs()
        val expiry = if (now > Long.MAX_VALUE - ttlMs) Long.MAX_VALUE else now + ttlMs
        entries[key] = Entry(value, expiry)
        while (entries.size > maxSize) {
            val iterator = entries.entries.iterator()
            if (!iterator.hasNext()) break
            iterator.next()
            iterator.remove()
        }
    }

    @Synchronized
    fun remove(key: String) {
        entries.remove(key)
    }
}
