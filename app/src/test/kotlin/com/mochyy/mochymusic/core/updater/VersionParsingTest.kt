package com.mochyy.mochymusic.core.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionParsingTest {
    @Test
    fun forkRevisionsCompareWithinTheSameUpstreamVersion() {
        val installed = parseFullVersion("8.2.3-xbibz.1")
        val next = parseFullVersion("v8.2.3-xbibz.2")
        assertEquals(FullVersion(8, 2, 3, 1), installed)
        assertEquals(FullVersion(8, 2, 3, 2), next)
        assertTrue(next > installed)
    }

    @Test
    fun displayVersionRetainsForkRevision() {
        assertEquals(parseFullVersion("v8.2.3-xbibz.1"), parseFullVersion("8.2.3 (1)"))
        assertTrue(parseFullVersion("v8.2.4-xbibz.1") > parseFullVersion("v8.2.3-xbibz.99"))
    }

    @Test
    fun upstreamBuildNumbersStillParse() {
        assertEquals(FullVersion(8, 2, 3, 1188), parseFullVersion("8.2.3 build#1188"))
        assertEquals(FullVersion(8, 2, 3, 0), parseFullVersion("v8.2.3"))
    }
}
