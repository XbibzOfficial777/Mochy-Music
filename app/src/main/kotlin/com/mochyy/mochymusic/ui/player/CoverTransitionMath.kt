package com.mochyy.mochymusic.ui.player

import kotlin.math.floor

/** Alpha of a left-to-right layered crossfade; a missing lower layer must not reveal a hole. */
internal fun coverCrossfadeAlpha(position: Float, slotStep: Int, hasLower: Boolean): Float {
    val lower = floor(position).toInt()
    val fraction = (position - lower).coerceIn(0f, 1f)
    return when (slotStep) {
        lower -> 1f
        lower + 1 -> if (hasLower) fraction else 1f
        else -> 0f
    }
}
