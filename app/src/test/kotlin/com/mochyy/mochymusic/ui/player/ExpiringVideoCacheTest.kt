package com.mochyy.mochymusic.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpiringVideoCacheTest {
    @Test fun expiresAndDoesNotCacheFailedOrZeroLifetimeEntries() {
        var now = 1_000L
        val cache = ExpiringVideoCache<String>(nowMs = { now })
        cache.put("video:720", "url", 500)
        cache.put("video:144", "expired", 0)
        assertEquals("url", cache.get("video:720"))
        assertNull(cache.get("video:144"))
        now = 1_500L
        assertNull(cache.get("video:720"))
    }

    @Test fun keepsQualitiesSeparateAndEvictsLeastRecentlyUsed() {
        var now = 0L
        val cache = ExpiringVideoCache<String>(maxSize = 2, nowMs = { now })
        cache.put("song:720", "720 url", 1_000)
        cache.put("song:2160", "4k url", 1_000)
        assertEquals("720 url", cache.get("song:720"))
        cache.put("another:720", "new url", 1_000)
        assertNull(cache.get("song:2160"))
        assertEquals("720 url", cache.get("song:720"))
        cache.remove("song:720")
        assertNull(cache.get("song:720"))
        now = 1_001
        assertNull(cache.get("another:720"))
    }
}
