package com.mochyy.mochymusic.api

/** Fix the old OpenRouter base-URL default while preserving explicit custom endpoints. */
internal fun openRouterEndpoint(baseUrl: String): String {
    val value = baseUrl.trim().trimEnd('/')
    return when {
        value.isEmpty() || value.equals("https://openrouter.ai/api/v1", ignoreCase = true) ->
            "https://openrouter.ai/api/v1/chat/completions"
        else -> value
    }
}
