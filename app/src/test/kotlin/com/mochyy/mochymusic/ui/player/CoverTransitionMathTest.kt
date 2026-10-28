package com.mochyy.mochymusic.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverTransitionMathTest {
    @Test fun crossfadeUsesOpaqueBaseAndIncomingFraction() {
        assertEquals(1f, coverCrossfadeAlpha(4.25f, 4, true), 0.001f)
        assertEquals(0.25f, coverCrossfadeAlpha(4.25f, 5, true), 0.001f)
        assertEquals(0f, coverCrossfadeAlpha(4.25f, 3, true), 0.001f)
    }

    @Test fun negativePositionAndQueueEdgesAreSafe() {
        assertEquals(1f, coverCrossfadeAlpha(-0.25f, -1, true), 0.001f)
        assertEquals(0.75f, coverCrossfadeAlpha(-0.25f, 0, true), 0.001f)
        assertEquals(1f, coverCrossfadeAlpha(-0.25f, 0, false), 0.001f)
    }
}
