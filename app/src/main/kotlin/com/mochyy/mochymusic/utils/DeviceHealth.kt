package com.mochyy.mochymusic.utils

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.getSystemService
import coil3.SingletonImageLoader
import com.mochyy.mochymusic.constants.RamOptimizeKey
import com.mochyy.mochymusic.constants.ThermalOptimizeKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Real device RAM and thermal controls for this process.
 *
 * RAM optimize trims Coil's in-memory cover cache and shrinks ExoPlayer's
 * ahead-buffer. Thermal optimize drops this app's extra GPU/CPU work (high
 * refresh, canvas, extra lyrics bloom) and listens to
 * [PowerManager.getCurrentThermalStatus] so haptics stop when the SoC is
 * already warm. It cannot cool other apps or force the kernel's thermal
 * governor; it only reduces what Mochy itself is doing.
 */
@SuppressLint("StaticFieldLeak")
object DeviceHealth {

    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var app: Context? = null

    var ramOptimize by mutableStateOf(false)
        private set

    var thermalOptimize by mutableStateOf(false)
        private set

    var thermalStatus by mutableIntStateOf(0)
        private set

    fun attach(context: Context, scope: CoroutineScope) {
        val application = context.applicationContext
        app = application
        ramOptimize = application.dataStore.get(RamOptimizeKey, false)
        thermalOptimize = application.dataStore.get(ThermalOptimizeKey, false)
        readThermalStatus()
        registerThermalListener(application)
        scope.launch(Dispatchers.IO) {
            application.dataStore.data
                .map { it[RamOptimizeKey] ?: false }
                .distinctUntilChanged()
                .collect { on ->
                    ramOptimize = on
                    if (on) trimImageMemory(aggressive = true)
                }
        }
        scope.launch(Dispatchers.IO) {
            application.dataStore.data
                .map { it[ThermalOptimizeKey] ?: false }
                .distinctUntilChanged()
                .collect { on ->
                    thermalOptimize = on
                    if (on) trimImageMemory(aggressive = false)
                }
        }
    }

    val suppressHaptics: Boolean
        get() = DeviceHealthPolicy.suppressHaptics(thermalOptimize, thermalStatus)

    val allowHighRefresh: Boolean
        get() = DeviceHealthPolicy.allowHighRefresh(thermalOptimize)

    val allowHeavyVisuals: Boolean
        get() = DeviceHealthPolicy.allowHeavyVisuals(thermalOptimize)

    fun widgetIntervalMs(): Long =
        DeviceHealthPolicy.widgetIntervalMs(ramOptimize, thermalOptimize)

    fun exoMinBufferMs(): Int = DeviceHealthPolicy.exoMinBufferMs(ramOptimize)

    fun coilMaxSizePercent(): Double = DeviceHealthPolicy.coilMaxSizePercent(ramOptimize)

    fun onTrimMemory(level: Int) {
        if (!DeviceHealthPolicy.aggressiveTrim(ramOptimize, level)) return
        // Numeric ComponentCallbacks2 levels: RUNNING_LOW=10, COMPLETE=80 (named fields are deprecated).
        val aggressive = ramOptimize && level >= 10
        trimImageMemory(aggressive = aggressive || level >= 80)
    }

    /**
     * Drops Coil's decoded-cover memory cache. Returns how many bytes it held
     * before the drop, so the settings row can show a real figure.
     */
    fun freeAppMemory(): Long {
        val ctx = app ?: return 0L
        val cache = SingletonImageLoader.get(ctx).memoryCache
        val before = cache?.size ?: 0L
        cache?.clear()
        Timber.i("DeviceHealth: cleared Coil memory cache (%s)", DeviceHealthPolicy.formatBytes(before))
        return before
    }

    fun ramLine(context: Context): String {
        val snapshot = snapshot(context)
        return context.getString(
            com.mochyy.mochymusic.R.string.settings_ram_status_line,
            DeviceHealthPolicy.formatBytes(snapshot.usedMem),
            DeviceHealthPolicy.formatBytes(snapshot.totalMem),
            DeviceHealthPolicy.formatBytes(snapshot.appMem),
        )
    }

    data class Snapshot(
        val availMem: Long,
        val totalMem: Long,
        val usedMem: Long,
        val appMem: Long,
        val lowMemory: Boolean,
    )

    fun snapshot(context: Context): Snapshot {
        val am = context.applicationContext.getSystemService<ActivityManager>()
        val info = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(info)
        val runtime = Runtime.getRuntime()
        val heap = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
        val native = Debug.getNativeHeapAllocatedSize().coerceAtLeast(0L)
        val total = info.totalMem
        val avail = info.availMem
        return Snapshot(
            availMem = avail,
            totalMem = total,
            usedMem = DeviceHealthPolicy.ramUsedBytes(total, avail),
            appMem = heap + native,
            lowMemory = info.lowMemory,
        )
    }

    private fun trimImageMemory(aggressive: Boolean) {
        val ctx = app ?: return
        val cache = SingletonImageLoader.get(ctx).memoryCache ?: return
        val target = if (aggressive) 0L else cache.maxSize / 4
        cache.trimToSize(target)
    }

    private fun readThermalStatus() {
        val ctx = app ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            thermalStatus = 0
            return
        }
        val pm = ctx.getSystemService<PowerManager>() ?: return
        thermalStatus = runCatching { pm.currentThermalStatus }.getOrDefault(0)
    }

    private fun registerThermalListener(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val pm = context.getSystemService<PowerManager>() ?: return
        val listener = PowerManager.OnThermalStatusChangedListener { status ->
            thermalStatus = status
            if (DeviceHealthPolicy.suppressHaptics(thermalOptimize, status)) {
                trimImageMemory(aggressive = false)
            }
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.addThermalStatusListener(context.mainExecutor, listener)
            } else {
                @Suppress("DEPRECATION")
                pm.addThermalStatusListener(listener)
            }
        }.onFailure { Timber.w(it, "DeviceHealth: thermal listener not available") }
    }

    fun thermalLabel(context: Context, status: Int): String {
        val id = when (status) {
            0 -> com.mochyy.mochymusic.R.string.thermal_status_none
            1 -> com.mochyy.mochymusic.R.string.thermal_status_light
            2 -> com.mochyy.mochymusic.R.string.thermal_status_moderate
            3 -> com.mochyy.mochymusic.R.string.thermal_status_severe
            4 -> com.mochyy.mochymusic.R.string.thermal_status_critical
            5 -> com.mochyy.mochymusic.R.string.thermal_status_emergency
            6 -> com.mochyy.mochymusic.R.string.thermal_status_shutdown
            else -> com.mochyy.mochymusic.R.string.thermal_status_unknown
        }
        return context.getString(id)
    }
}
