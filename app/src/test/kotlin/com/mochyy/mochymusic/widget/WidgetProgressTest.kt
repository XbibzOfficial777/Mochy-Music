package com.mochyy.mochymusic.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetProgressTest {
    @Test
    fun formatsMinutesAndSeconds() {
        assertEquals("0:00", WidgetProgress.formatMs(0))
        assertEquals("1:01", WidgetProgress.formatMs(61_000))
        assertEquals("75:13", WidgetProgress.formatMs(75 * 60_000L + 13_000L))
    }

    @Test
    fun seekRoundTrip() {
        val duration = 180_000L
        val level = WidgetProgress.level(45_000, duration)
        assertEquals(250, level)
        assertEquals(45_000L, WidgetProgress.positionFromLevel(level, duration))
    }

    @Test
    fun emptyDurationIsZero() {
        assertEquals(0, WidgetProgress.level(10, 0))
        assertEquals(0L, WidgetProgress.positionFromLevel(500, 0))
    }

    @Test
    fun segmentMidpoints() {
        assertEquals(50, WidgetProgress.segmentLevel(0))
        assertEquals(450, WidgetProgress.segmentLevel(4))
        assertEquals(950, WidgetProgress.segmentLevel(9))
    }
}
