package com.mochyy.mochymusic.utils

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.DisplayMetrics
import android.view.WindowManager
import com.mochyy.mochymusic.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * Writes errors and crashes to `/sdcard/Mochi-Logs` with a full device dump.
 *
 * On Android 11+ that public folder needs all-files access (already declared for
 * downloads). If it cannot be created, the same files go to the app's external
 * files dir, then to internal files — never silently dropped.
 */
object MochiLogs {
    const val DIR_NAME = MochiLogFormat.DIR_NAME

    @Volatile
    private var app: Context? = null

    private val io = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "mochi-logs").apply { isDaemon = true }
    }

    fun attach(context: Context) {
        app = context.applicationContext
        io.execute {
            runCatching { write(dir(), "device.txt", deviceReport(), append = false) }
            val last = File(context.applicationContext.filesDir, CrashHandler.LAST_CRASH_FILE)
            if (last.isFile && last.length() > 0L) {
                runCatching { last.copyTo(File(dir(), "last_crash.txt"), overwrite = true) }
            }
        }
    }

    fun error(message: String, throwable: Throwable? = null) {
        writeLevel("ERROR", message, throwable)
    }

    fun warn(message: String, throwable: Throwable? = null) {
        writeLevel("WARN", message, throwable)
    }

    fun crash(throwable: Throwable) {
        val body = buildString {
            appendLine(deviceReport())
            appendLine()
            appendLine("=== crash ===")
            appendLine(stack(throwable))
        }
        val name = "crash-${stamp("yyyyMMdd-HHmmss")}.txt"
        io.execute {
            runCatching { write(dir(), name, body, append = false) }
            runCatching { write(dir(), "errors-${stamp("yyyy-MM-dd")}.txt", body + "\n", append = true) }
        }
    }

    fun dir(): File {
        val ctx = app
        val candidates = buildList {
            add(File("/sdcard/$DIR_NAME"))
            runCatching { add(File(Environment.getExternalStorageDirectory(), DIR_NAME)) }
            if (ctx != null) {
                ctx.getExternalFilesDir(null)?.let { add(File(it, DIR_NAME)) }
                add(File(ctx.filesDir, DIR_NAME))
            }
        }
        for (candidate in candidates.distinctBy { it.absolutePath }) {
            if (ensureWritable(candidate)) return candidate
        }
        return File("/sdcard/$DIR_NAME")
    }

    @Suppress("DEPRECATION")
    fun deviceReport(): String {
        val ctx = app
        return buildString {
            appendLine("Mochi log")
            appendLine("=".repeat(60))
            appendLine("time: ${stamp("yyyy-MM-dd HH:mm:ss.SSS")} ${TimeZone.getDefault().id}")
            appendLine("app: ${BuildConfig.APPLICATION_ID}")
            appendLine("version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("upstream: ${BuildConfig.UPSTREAM_VERSION}")
            appendLine("buildType: ${BuildConfig.BUILD_TYPE} debug=${BuildConfig.DEBUG}")
            appendLine("manufacturer: ${Build.MANUFACTURER}")
            appendLine("brand: ${Build.BRAND}")
            appendLine("model: ${Build.MODEL}")
            appendLine("device: ${Build.DEVICE}")
            appendLine("product: ${Build.PRODUCT}")
            appendLine("board: ${Build.BOARD}")
            appendLine("hardware: ${Build.HARDWARE}")
            appendLine("android: ${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT} preview=${Build.VERSION.PREVIEW_SDK_INT}")
            appendLine("codename: ${Build.VERSION.CODENAME}")
            appendLine("incremental: ${Build.VERSION.INCREMENTAL}")
            appendLine("securityPatch: ${Build.VERSION.SECURITY_PATCH}")
            appendLine("display: ${Build.DISPLAY}")
            appendLine("fingerprint: ${Build.FINGERPRINT}")
            appendLine("abis: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("abi32: ${Build.SUPPORTED_32_BIT_ABIS.joinToString()}")
            appendLine("abi64: ${Build.SUPPORTED_64_BIT_ABIS.joinToString()}")
            appendLine("locale: ${Locale.getDefault()}")
            if (ctx != null) {
                appendLine("package: ${ctx.packageName}")
                runCatching {
                    val pm = ctx.packageManager
                    val info = if (Build.VERSION.SDK_INT >= 33) {
                        pm.getPackageInfo(ctx.packageName, PackageManager.PackageInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(ctx.packageName, 0)
                    }
                    appendLine("firstInstall: ${info.firstInstallTime}")
                    appendLine("lastUpdate: ${info.lastUpdateTime}")
                    appendLine("installer: ${pm.getInstallerPackageName(ctx.packageName)}")
                }
                val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val mem = ActivityManager.MemoryInfo()
                am?.getMemoryInfo(mem)
                if (am != null) {
                    appendLine("ramTotalMb: ${mem.totalMem / (1024 * 1024)}")
                    appendLine("ramAvailMb: ${mem.availMem / (1024 * 1024)}")
                    appendLine("ramLow: ${mem.lowMemory}")
                }
                runCatching {
                    val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    val metrics = DisplayMetrics()
                    @Suppress("DEPRECATION")
                    wm.defaultDisplay.getRealMetrics(metrics)
                    appendLine("screen: ${metrics.widthPixels}x${metrics.heightPixels} dpi=${metrics.densityDpi} density=${metrics.density}")
                }
            }
        }
    }

    internal fun formatEntry(level: String, message: String, throwable: Throwable?): String =
        MochiLogFormat.entry(level, stamp("yyyy-MM-dd HH:mm:ss.SSS"), message, throwable?.let { stack(it) })

    private fun writeLevel(level: String, message: String, throwable: Throwable?) {
        val text = formatEntry(level, message, throwable)
        io.execute {
            runCatching { write(dir(), "errors-${stamp("yyyy-MM-dd")}.txt", text, append = true) }
        }
    }

    private fun stack(throwable: Throwable): String =
        StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()

    private fun stamp(pattern: String): String =
        SimpleDateFormat(pattern, Locale.US).format(Date())

    @SuppressLint("SetWorldWritable")
    private fun ensureWritable(dir: File): Boolean {
        return runCatching {
            if (!dir.exists() && !dir.mkdirs()) return false
            if (!dir.isDirectory) return false
            val probe = File(dir, ".write-probe")
            probe.writeText("ok")
            probe.delete()
            true
        }.getOrDefault(false)
    }

    private fun write(dir: File, name: String, body: String, append: Boolean) {
        if (!ensureWritable(dir)) return
        val file = File(dir, name)
        if (append) file.appendText(body) else file.writeText(body)
    }
}
