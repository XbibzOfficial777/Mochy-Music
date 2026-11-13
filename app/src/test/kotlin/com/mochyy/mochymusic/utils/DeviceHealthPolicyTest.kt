package com.mochyy.mochymusic.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceHealthPolicyTest {
    @Test
    fun ramOptimizeShrinksCoilAndBuffers() {
        assertEquals(0.30, DeviceHealthPolicy.coilMaxSizePercent(false), 0.0)
        assertEquals(0.12, DeviceHealthPolicy.coilMaxSizePercent(true), 0.0)
        assertEquals(50_000, DeviceHealthPolicy.exoMinBufferMs(false))
        assertEquals(15_000, DeviceHealthPolicy.exoMinBufferMs(true))
    }

    @Test
    fun widgetTicksSlowWhenSaving() {
        assertEquals(1_000L, DeviceHealthPolicy.widgetIntervalMs(false, false))
        assertEquals(2_500L, DeviceHealthPolicy.widgetIntervalMs(true, false))
        assertEquals(5_000L, DeviceHealthPolicy.widgetIntervalMs(true, true))
    }

    @Test
    fun thermalCoolDropsExtras() {
        assertTrue(DeviceHealthPolicy.allowHighRefresh(false))
        assertFalse(DeviceHealthPolicy.allowHighRefresh(true))
        assertFalse(DeviceHealthPolicy.allowHeavyVisuals(true))
        assertFalse(DeviceHealthPolicy.suppressHaptics(true, 0))
        assertTrue(DeviceHealthPolicy.suppressHaptics(true, 2))
    }

    @Test
    fun ramUsedAndFormat() {
        assertEquals(3_000L, DeviceHealthPolicy.ramUsedBytes(10_000, 7_000))
        assertEquals("0 B", DeviceHealthPolicy.formatBytes(0))
        assertEquals("512 KB", DeviceHealthPolicy.formatBytes(512 * 1024L))
    }
}
