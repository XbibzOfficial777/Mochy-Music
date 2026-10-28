package com.mochyy.mochymusic.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiTranslationSessionTest {
    @Test fun credentialsAreSessionOnlyAndRedacted() {
        AiTranslationSession.clear()
        AiTranslationSession.set("secret-for-test", "example/model")
        val config = AiTranslationSession.config.value!!
        assertEquals("example/model", config.model)
        assertFalse(config.toString().contains("secret-for-test"))
        AiTranslationSession.clear()
        assertNull(AiTranslationSession.config.value)
    }

    @Test fun providerModelAndLanguageDoNotShareGoogleCacheTags() {
        val tag = aiTranslationTag("OpenRouter", "example/model", "ja>en")
        assertNotEquals("ja>en", tag)
        assertNotEquals(tag, aiTranslationTag("OpenRouter", "other/model", "ja>en"))
        assertNotEquals(tag, aiTranslationTag("OpenRouter", "example/model", "ko>en"))
    }
}
