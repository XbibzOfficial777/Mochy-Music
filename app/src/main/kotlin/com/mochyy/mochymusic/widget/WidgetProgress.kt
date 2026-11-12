package com.mochyy.mochymusic.widget

object WidgetProgress {
    const val MAX = 1000

    fun formatMs(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        val m = total / 60L
        val s = total % 60L
        return "$m:${s.toString().padStart(2, '0')}"
    }

    fun level(position: Long, duration: Long, max: Int = MAX): Int {
        if (duration <= 0L) return 0
        return ((position.toDouble() / duration.toDouble()) * max).toInt().coerceIn(0, max)
    }

    fun positionFromLevel(level: Int, duration: Long, max: Int = MAX): Long {
        if (duration <= 0L || max <= 0) return 0L
        return (duration * level.coerceIn(0, max).toLong()) / max
    }

    fun segmentLevel(index: Int, segments: Int = 10, max: Int = MAX): Int {
        if (segments <= 0 || max <= 0) return 0
        val i = index.coerceIn(0, segments - 1)
        return ((i * 2 + 1) * max) / (segments * 2)
    }
}
