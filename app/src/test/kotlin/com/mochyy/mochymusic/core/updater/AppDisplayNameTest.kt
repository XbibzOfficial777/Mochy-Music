package com.mochyy.mochymusic.core.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDisplayNameTest {
    @Test
    fun officialStayPlain() {
        assertFalse(isPreReleaseBranding("8.3.3-xbibz.15", includePreRelease = false))
        assertEquals("Mochy Xbibz", appDisplayName("Mochy Xbibz", "8.3.3-xbibz.15", false))
    }

    @Test
    fun preReleaseChannelAddsBeta() {
        assertTrue(isPreReleaseBranding("8.3.3-xbibz.15", includePreRelease = true))
        assertEquals("Mochy Xbibz (Beta)", appDisplayName("Mochy Xbibz", "8.3.3-xbibz.15", true))
        assertEquals("Mochy Xbibz (Beta)", appDisplayName("Mochy Xbibz (Beta)", "8.3.3-xbibz.15", true))
    }

    @Test
    fun preVersionNameAddsBetaEvenIfToggleOff() {
        assertTrue(isPreReleaseBranding("8.3.3-xbibz.16-pre.1", includePreRelease = false))
        assertEquals("Mochy Xbibz (Beta)", appDisplayName("Mochy Xbibz", "8.3.3-xbibz.16-pre.1", false))
    }
}
