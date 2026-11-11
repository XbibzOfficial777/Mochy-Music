package com.mochyy.mochymusic.ui.screens.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ChangelogMarkdownTest {
    @Test
    fun `a pipe table is parsed as columns not a raw paragraph`() {
        val md = """
            ## Pilih APK

            | Berkas | Perangkat | Ukuran |
            | --- | --- | --- |
            | arm64-v8a | HP 64-bit | kecil |
            | universal | semua | besar |
        """.trimIndent()
        val headers = changelogTableHeaders(md)
        assertEquals(listOf("Berkas", "Perangkat", "Ukuran"), headers)
        assertFalse(changelogSummaryLine(md).contains("| ---"))
    }

    @Test
    fun `indonesian locale picks the id block`() {
        val body = """
            <!--lang:id-->
            Perbaikan ekualiser.
            <!--lang:en-->
            Equalizer crash fix.
        """.trimIndent()
        assertEquals("Perbaikan ekualiser.", pickReleaseNotes(body, "in"))
        assertEquals("Perbaikan ekualiser.", pickReleaseNotes(body, "id"))
        assertEquals("Equalizer crash fix.", pickReleaseNotes(body, "en"))
    }

    @Test
    fun `body without markers is left whole`() {
        val body = "Tidak ada penanda bahasa."
        assertEquals(body, pickReleaseNotes(body, "in"))
        assertEquals(body, pickReleaseNotes(body, "en"))
    }
}
