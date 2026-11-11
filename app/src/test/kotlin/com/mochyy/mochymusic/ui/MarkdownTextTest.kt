package com.mochyy.mochymusic.ui

import androidx.compose.ui.graphics.Color
import com.mochyy.mochymusic.ui.component.parseInlineMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTextTest {
    private fun parsed(text: String) = parseInlineMarkdown(text, Color.Blue, Color.Gray)

    @Test fun `bold and italic survive as the inner words`() {
        val text = parsed("satu **tebal** dan *miring*").text
        assertEquals("satu tebal dan miring", text)
    }

    @Test fun `a markdown link keeps the label not the url`() {
        val text = parsed("lihat [catatan](https://example.com/x) ini").text
        assertEquals("lihat catatan ini", text)
    }

    @Test fun `inline code and strike drop their marks`() {
        val text = parsed("pakai `AppMotion.Scene` lalu ~~lama~~ baru").text
        assertEquals("pakai AppMotion.Scene lalu lama baru", text)
    }

    @Test fun `a heading prefix is stripped`() {
        assertEquals("Judul rilis", parsed("## Judul rilis").text)
    }

    @Test fun `plain text is unchanged`() {
        val raw = "Tidak ada markup di sini."
        assertEquals(raw, parsed(raw).text)
        assertTrue(parsed(raw).spanStyles.isEmpty())
    }
}
