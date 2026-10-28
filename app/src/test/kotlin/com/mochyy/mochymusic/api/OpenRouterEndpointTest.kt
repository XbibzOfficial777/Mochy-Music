package com.mochyy.mochymusic.api

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenRouterEndpointTest {
    @Test fun legacyBaseResolvesToChatCompletions() {
        val expected = "https://openrouter.ai/api/v1/chat/completions"
        assertEquals(expected, openRouterEndpoint(""))
        assertEquals(expected, openRouterEndpoint("https://openrouter.ai/api/v1/"))
        assertEquals(expected, openRouterEndpoint(expected))
    }

    @Test fun explicitCustomEndpointIsNotRewritten() {
        assertEquals("https://example.org/custom", openRouterEndpoint("https://example.org/custom"))
    }
}
