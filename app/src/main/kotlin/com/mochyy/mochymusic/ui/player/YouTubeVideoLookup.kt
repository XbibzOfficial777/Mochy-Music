// YouTubeVideoLookup.kt

package com.mochyy.mochymusic.ui.player

import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import com.mochyy.mochymusic.utils.YTPlayerUtils

data class OfficialMusicVideo(
    val videoId: String,
    val stream: YTPlayerUtils.ResolvedVideoStream,
) {
    val streamUrl: String get() = stream.url
}

object YouTubeVideoUrlCache {
    private val cache = ExpiringVideoCache<OfficialMusicVideo>()
    private const val MAX_CACHE_MS = 4 * 60 * 60 * 1000L

    fun key(mediaId: String, maxHeight: Int, self: Boolean): String =
        "${if (self) "self" else "search"}:$maxHeight:$mediaId"

    fun get(key: String): OfficialMusicVideo? = cache.get(key)

    fun put(key: String, video: OfficialMusicVideo?) {
        // A temporary failure must be retryable. URLs expire, so never cache those indefinitely.
        video ?: return
        val lifetime = ((video.stream.expiresInSeconds.toLong() - 90L).coerceAtLeast(0L) * 1000L)
            .coerceAtMost(MAX_CACHE_MS)
        cache.put(key, video, lifetime)
    }

    fun remove(key: String) = cache.remove(key)
}

suspend fun findOfficialMusicVideo(title: String, artist: String, maxHeight: Int): OfficialMusicVideo? {
    if (title.isBlank() || artist.isBlank()) return null

    val results = YouTube.search(query = "$title $artist", filter = YouTube.SearchFilter.FILTER_VIDEO)
        .getOrNull()?.items.orEmpty()

    val normalizedTitle = normalizeCanvasSongTitle(title)
    val normalizedArtist = normalizeCanvasArtistName(artist)

    val match = results
        .filterIsInstance<SongItem>()
        .filter { it.musicVideoType == MUSIC_VIDEO_TYPE_OMV }
        .firstOrNull { candidate ->
            val candidateTitle = normalizeCanvasSongTitle(candidate.title)
            val candidateArtist = normalizeCanvasArtistName(candidate.artists.joinToString { it.name })
            (candidateTitle.contains(normalizedTitle, ignoreCase = true) || normalizedTitle.contains(candidateTitle, ignoreCase = true)) &&
                (candidateArtist.contains(normalizedArtist, ignoreCase = true) || normalizedArtist.contains(candidateArtist, ignoreCase = true))
        }
        ?: return null

    val stream = YTPlayerUtils.resolveVideoStream(match.id, maxHeight).getOrNull() ?: return null
    return OfficialMusicVideo(videoId = match.id, stream = stream)
}
