package com.mochyy.mochymusic.ui.player.customize

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleBlobMotionTest {
    @Test
    fun eachOrbitIsContinuousAcrossTheLoopBoundary() {
        val end = (2.0 * PI).toFloat()
        repeat(AppleBlobCount) { i ->
            val startAngle = appleBlobAngle(0f, i)
            val endAngle = appleBlobAngle(end, i)
            assertEquals(cos(startAngle), cos(endAngle), 0.00001f)
            assertEquals(sin(startAngle), sin(endAngle), 0.00001f)
            // Adjacent frames on either side of the wrap stay close too.
            val before = appleBlobAngle(end - 0.001f, i)
            val after = appleBlobAngle(0.001f, i)
            assertTrue(kotlin.math.abs(cos(before) - cos(after)) < 0.01f)
            assertTrue(kotlin.math.abs(sin(before) - sin(after)) < 0.01f)
        }
    }

    @Test
    fun blobsStillMoveAtDifferentGentleRates() {
        val velocities = (0 until AppleBlobCount).map { i -> appleBlobAngle(1f, i) - appleBlobAngle(0f, i) }
        listOf(2f, 2f, 3f, 3f, 4f, 4f).zip(velocities).forEach { (expected, actual) ->
            assertEquals(expected, actual, 0.000001f)
        }
        assertEquals(72_000, AppleBlobCycleMs)
    }
}
