package com.mochyy.mochymusic.ui.player.customize

internal const val AppleBlobCount = 6
internal const val AppleBlobCycleMs = 72_000

/** Whole numbers of orbits per cycle prevent a visible jump when the phase wraps. */
internal fun appleBlobAngle(phase: Float, index: Int): Float =
    phase * (2 + index / 2) + index * 1.05f
