package com.mochyy.mochymusic.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MochiLogFormatTest {
    @Test
    fun folderNameIsPublicSdcardMochiLogs() {
        assertEquals("Mochi-Logs", MochiLogFormat.DIR_NAME)
    }

    @Test
    fun entryIncludesLevelMessageAndStack() {
        val text = MochiLogFormat.entry(
            level = "ERROR",
            timestamp = "2026-11-12 10:00:00.000",
            message = "stream failed",
            stack = "java.io.IOException: boom\n\tat a.b(c:1)\n",
        )
        assertTrue(text.contains("ERROR"))
        assertTrue(text.contains("stream failed"))
        assertTrue(text.contains("java.io.IOException: boom"))
        assertTrue(text.contains("2026-11-12 10:00:00.000"))
    }

    @Test
    fun entryWithoutStackStillHasMessage() {
        val text = MochiLogFormat.entry("WARN", "t", "only a warning", null)
        assertTrue(text.contains("only a warning"))
        assertTrue(!text.contains("java."))
    }
}
