// CrashHandler.kt

package com.mochyy.mochymusic.utils

import android.content.Context
import android.os.Build
import com.mochyy.mochymusic.BuildConfig
import timber.log.Timber
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class CrashHandler private constructor(
    private val applicationContext: Context,
) : Thread.UncaughtExceptionHandler {

    private val defaultHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    // The one place that deliberately logs through android.util.Log instead of Timber: the process
    // is killed a few lines later, and this handler also runs for crashes that happen before (or
    // while) the application finishes planting a tree. Timber would drop the line in exactly the
    // case this exists for, so the LogNotTimber warning is waived with the reason kept here.
    @Suppress("LogNotTimber")
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val crashLog = buildCrashLog(throwable)
            Timber.e(throwable, "App crashed")
            android.util.Log.e("MochyCrash", crashLog)
            runCatching { java.io.File(applicationContext.filesDir, LAST_CRASH_FILE).writeText(crashLog) }
            MochiLogs.crash(throwable)

            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(1)
        } catch (e: Exception) {
            Timber.e(e, "Error handling crash")
            runCatching { MochiLogs.error("crash handler failed", e) }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun buildCrashLog(throwable: Throwable): String {
        val stackTrace = StringWriter().apply {
            throwable.printStackTrace(PrintWriter(this))
        }.toString()

        return buildString {
            appendLine("mochy Crash Report")
            appendLine("=".repeat(50))
            appendLine()
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Brand: ${Build.BRAND}")
            appendLine("Device: ${Build.MODEL}")
            appendLine("Product: ${Build.PRODUCT}")
            appendLine("Hardware: ${Build.HARDWARE}")
            appendLine("Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Patch: ${Build.VERSION.SECURITY_PATCH}")
            appendLine("Fingerprint: ${Build.FINGERPRINT}")
            appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("ApplicationId: ${BuildConfig.APPLICATION_ID}")
            appendLine()
            appendLine("=".repeat(50))
            appendLine("Stacktrace:")
            appendLine("=".repeat(50))
            appendLine()
            append(stackTrace)
        }
    }

    companion object {
        const val EXTRA_CRASH_LOG = "crash_log"
        const val LAST_CRASH_FILE = "last_crash.txt"

        fun install(context: Context) {
            MochiLogs.attach(context.applicationContext)
            val handler = CrashHandler(context.applicationContext)
            Thread.setDefaultUncaughtExceptionHandler(handler)
            Timber.d("CrashHandler installed")
        }
    }
}
