package com.mochyy.mochymusic.utils

import android.media.MediaCodecList
import com.music.innertube.models.response.PlayerResponse

/**
 * Avoid knowingly requesting a resolution/codec this device cannot decode. A buggy platform query
 * must not make videos disappear: if probing itself fails, let ExoPlayer try and fall back on error.
 */
internal class AndroidVideoDecoderProbe {
    private val decoders = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filterNot { it.isEncoder }
    }.getOrNull()
    private val answers = mutableMapOf<String, Boolean>()

    fun supports(format: PlayerResponse.StreamingData.Format): Boolean {
        val mime = videoSampleMime(format.mimeType) ?: return true
        val width = format.width ?: return false
        val height = format.height ?: return false
        val fps = format.fps
        val key = "$mime:$width:$height:$fps"
        return answers.getOrPut(key) {
            val available = decoders ?: return@getOrPut true
            var queryFailed = false
            val supported = available.any { decoder ->
                try {
                    val type = decoder.supportedTypes.firstOrNull { it.equals(mime, ignoreCase = true) }
                        ?: return@any false
                    val caps = decoder.getCapabilitiesForType(type).videoCapabilities
                    if (fps != null && fps > 0) {
                        caps.areSizeAndRateSupported(width, height, fps.toDouble())
                    } else {
                        caps.isSizeSupported(width, height)
                    }
                } catch (_: Exception) {
                    queryFailed = true
                    false
                }
            }
            supported || queryFailed
        }
    }
}
