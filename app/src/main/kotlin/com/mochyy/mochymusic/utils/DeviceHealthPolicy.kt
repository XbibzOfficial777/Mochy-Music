package com.mochyy.mochymusic.utils

import java.util.Locale

/**
 * Pure RAM / thermal policy. Android APIs live in [DeviceHealth]; this is what
 * the unit tests pin so a toggle cannot ship as a no-op.
 */
object DeviceHealthPolicy {
    const val COIL_MEMORY_NORMAL = 0.30
    const val COIL_MEMORY_OPTIMIZED = 0.12

    const val EXO_MIN_BUFFER_NORMAL_MS = 50_000
    const val EXO_MIN_BUFFER_OPTIMIZED_MS = 15_000
    const val EXO_MAX_BUFFER_OPTIMIZED_MS = 30_000
    const val EXO_PLAYBACK_BUFFER_OPTIMIZED_MS = 1_000
    const val EXO_REBUFFER_OPTIMIZED_MS = 2_000

    const val WIDGET_TICK_NORMAL_MS = 1_000L
    const val WIDGET_TICK_SAVING_MS = 2_500L
    const val WIDGET_TICK_COOL_MS = 5_000L

    /** PowerManager.THERMAL_STATUS_MODERATE */
    const val THERMAL_MODERATE = 2

    fun coilMaxSizePercent(ramOptimize: Boolean): Double =
        if (ramOptimize) COIL_MEMORY_OPTIMIZED else COIL_MEMORY_NORMAL

    fun exoMinBufferMs(ramOptimize: Boolean): Int =
        if (ramOptimize) EXO_MIN_BUFFER_OPTIMIZED_MS else EXO_MIN_BUFFER_NORMAL_MS

    fun widgetIntervalMs(ramOptimize: Boolean, thermalCool: Boolean): Long = when {
        ramOptimize && thermalCool -> WIDGET_TICK_COOL_MS
        ramOptimize || thermalCool -> WIDGET_TICK_SAVING_MS
        else -> WIDGET_TICK_NORMAL_MS
    }

    fun allowHighRefresh(thermalCool: Boolean): Boolean = !thermalCool

    fun allowHeavyVisuals(thermalCool: Boolean): Boolean = !thermalCool

    fun suppressHaptics(thermalCool: Boolean, thermalStatus: Int): Boolean =
        thermalCool && thermalStatus >= THERMAL_MODERATE

    fun aggressiveTrim(ramOptimize: Boolean, trimLevel: Int): Boolean {
        if (!ramOptimize) return trimLevel >= 20 // UI_HIDDEN
        return trimLevel >= 10 // running moderate or worse, or UI hidden
    }

    fun ramUsedBytes(totalMem: Long, availMem: Long): Long =
        (totalMem - availMem).coerceAtLeast(0L)

    fun formatBytes(bytes: Long): String {
        if (bytes < 0L) return "0 B"
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return "${kb.toInt()} KB"
        val mb = kb / 1024.0
        if (mb < 1024.0) return "${"%.1f".format(Locale.US, mb)} MB"
        return "${"%.2f".format(Locale.US, mb / 1024.0)} GB"
    }
}
