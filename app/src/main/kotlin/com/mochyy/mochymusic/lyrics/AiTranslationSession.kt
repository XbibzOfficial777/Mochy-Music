package com.mochyy.mochymusic.lyrics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Explicit, process-only OpenRouter credentials. They are never written to preferences, backups,
 * logs or the database. Process death clears the key; the listener can also clear it in the sheet.
 */
internal data class AiTranslationConfig(val apiKey: String, val model: String) {
    override fun toString(): String = "AiTranslationConfig(apiKey=[redacted], model=$model)"
}

internal object AiTranslationSession {
    private val current = MutableStateFlow<AiTranslationConfig?>(null)
    val config = current.asStateFlow()

    fun set(apiKey: String, model: String) {
        require(apiKey.isNotBlank() && model.isNotBlank())
        current.value = AiTranslationConfig(apiKey.trim(), model.trim())
    }

    fun clear() {
        current.value = null
    }
}

/** Keep AI translations separate from Google and from other models in the existing lyrics cache. */
internal fun aiTranslationTag(provider: String, model: String, sourceAndTarget: String): String =
    "@ai:${provider.lowercase()}:${model.trim()}:$sourceAndTarget"
