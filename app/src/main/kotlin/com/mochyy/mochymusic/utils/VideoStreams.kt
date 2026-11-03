package com.mochyy.mochymusic.utils

import com.music.innertube.models.response.PlayerResponse

// one youtube video's pictures, each track the same picture at another size, with what the requests have to carry
data class VideoStreams(
    val videoId: String,
    // smallest first
    val tracks: List<VideoTrack>,
    val headers: Map<String, String>,
    val expiresAtMs: Long,
    val durationMs: Long,
) {
    // a minute early, so a video that is just starting doesn't run into a dead link halfway
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAtMs - 60_000L

    // every size can be fetched in small pieces, so the player can switch between them as the connection changes
    val chunked: Boolean get() = tracks.isNotEmpty() && tracks.all { it.chunked }

    /**
     * Pick one decoder family for DASH. Prefer the highest supported size under the user's
     * ceiling, then H.264 for a tie. A 4K VP9/AV1 family can therefore beat 1080p H.264,
     * while a 720p choice keeps the efficient H.264 track when it is available.
     */
    fun tracksFor(shortSide: Int): List<VideoTrack> = tracks
        .filter { it.shortSide in 1..shortSide }
        .groupBy { videoFamily(it.codecs, it.mimeType) }
        .values
        .sortedWith(compareByDescending<List<VideoTrack>> { family -> family.maxOf { it.shortSide } }
            .thenBy { family -> videoFamilyRank(family.first().codecs) })
        .firstOrNull()
        ?.sortedBy { it.shortSide }
        .orEmpty()

    // The chosen size is a ceiling, never an instruction to download a bigger track.
    fun trackFor(shortSide: Int): VideoTrack? = tracksFor(shortSide).lastOrNull()
}

data class VideoTrack(
    val itag: Int,
    val url: String,
    val mimeType: String,
    val codecs: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrate: Int,
    val initRange: LongRange?,
    val indexRange: LongRange?,
    val contentLength: Long?,
) {
    // what "720p" means for a landscape and a portrait video alike
    val shortSide: Int get() = minOf(width, height)

    val chunked: Boolean get() = initRange != null && indexRange != null
}

private val CodecsPattern = Regex("codecs=\"([^\"]+)\"")

internal fun PlayerResponse.StreamingData.Format.containerType(): String = mimeType.substringBefore(';').trim()

internal fun PlayerResponse.StreamingData.Format.codecs(): String =
    CodecsPattern.find(mimeType)?.groupValues?.get(1).orEmpty()

internal fun PlayerResponse.StreamingData.Format.ByteRange.toLongRange(): LongRange? {
    val first = start.toLongOrNull() ?: return null
    val last = end.toLongOrNull() ?: return null
    return if (last >= first) first..last else null
}

// Families must remain separate in the DASH manifest. Profiles of a family can coexist;
// MIME is also part of the key so WebM and MP4 are never put in the same adaptation set.
internal fun videoFamily(codecs: String, container: String): String {
    val codec = codecs.lowercase()
    val name = when {
        codec.startsWith("avc1") || codec.startsWith("avc3") -> "avc"
        codec.startsWith("vp9") || codec.startsWith("vp09") -> "vp9"
        codec.startsWith("av01") -> "av1"
        codec.startsWith("hvc1") || codec.startsWith("hev1") -> "hevc"
        codec.startsWith("vp8") || codec.startsWith("vp08") -> "vp8"
        else -> codec.substringBefore('.').ifBlank { "unknown" }
    }
    return "$name:${container.lowercase()}"
}

internal fun videoFamilyRank(codecs: String): Int = when (videoFamily(codecs, "").substringBefore(':')) {
    "avc" -> 0
    "vp9" -> 1
    "av1" -> 2
    "hevc" -> 3
    "vp8" -> 4
    else -> 5
}

/**
 * Keep alternate codecs and sizes, not just the first H.264 family. Multiple formats at one
 * size remain candidates so the resolver can try another if a signed URL cannot be obtained.
 * The decoder predicate is injected for deterministic tests and is checked on the real device.
 */
internal fun videoFormatCandidateGroups(
    formats: List<PlayerResponse.StreamingData.Format>,
    supported: (PlayerResponse.StreamingData.Format) -> Boolean = { true },
): List<List<PlayerResponse.StreamingData.Format>> = formats.asSequence()
    .filter { !it.isAudio && (it.width ?: 0) > 0 && (it.height ?: 0) > 0 && it.bitrate > 0 }
    .filter { !it.url.isNullOrBlank() || !it.signatureCipher.isNullOrBlank() || !it.cipher.isNullOrBlank() }
    .filter(supported)
    .distinctBy { it.itag }
    .groupBy { videoFamily(it.codecs(), it.containerType()) to minOf(it.width!!, it.height!!) }
    .values
    .map { sameSize -> sameSize.sortedWith(compareBy<PlayerResponse.StreamingData.Format> { it.fps ?: 30 }.thenByDescending { it.bitrate }) }
    .sortedWith(compareBy<List<PlayerResponse.StreamingData.Format>> { minOf(it.first().width!!, it.first().height!!) }
        .thenBy { videoFamilyRank(it.first().codecs()) })
