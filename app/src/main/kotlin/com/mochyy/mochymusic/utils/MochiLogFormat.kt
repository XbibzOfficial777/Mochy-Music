package com.mochyy.mochymusic.utils

object MochiLogFormat {
    const val DIR_NAME = "Mochi-Logs"

    fun entry(level: String, timestamp: String, message: String, stack: String?): String {
        return buildString {
            appendLine("----- $timestamp $level -----")
            appendLine(message)
            if (!stack.isNullOrBlank()) append(stack.trimEnd()).appendLine()
            appendLine()
        }
    }
}
