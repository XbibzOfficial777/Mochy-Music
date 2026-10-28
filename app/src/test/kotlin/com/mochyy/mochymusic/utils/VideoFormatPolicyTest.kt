package com.mochyy.mochymusic.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoFormatPolicyTest {
    private data class Candidate(
        val width: Int?, val height: Int?, val bitrate: Int = 100,
        val url: Boolean = true, val supported: Boolean = true,
    )

    private fun rank(cap: Int, vararg candidates: Candidate) = rankVideoFormats(
        candidates.toList(), cap, Candidate::width, Candidate::height, Candidate::bitrate,
        Candidate::url, Candidate::supported,
    )

    @Test fun capNeverExceedsChosenResolution() {
        val low = Candidate(640, 360)
        val high = Candidate(1280, 720)
        val tooHigh = Candidate(3840, 2160)
        assertEquals(listOf(high, low), rank(720, low, tooHigh, high))
        assertEquals(emptyList<Candidate>(), rank(144, tooHigh))
    }

    @Test fun portraitResolutionUsesShortEdge() {
        val portrait = Candidate(720, 1280)
        assertEquals(listOf(portrait), rank(720, portrait))
        assertEquals(emptyList<Candidate>(), rank(480, portrait))
    }

    @Test fun unsupportedMissingAndInvalidFormatsAreExcluded() {
        val good = Candidate(1280, 720)
        assertEquals(listOf(good), rank(2160, Candidate(3840, 2160, supported = false),
            Candidate(1920, 1080, url = false), Candidate(null, 720), Candidate(0, 720), good))
    }

    @Test fun prefersBitrateOnlyWithinSameResolution() {
        val lowRate = Candidate(1280, 720, 1_000)
        val highRate = Candidate(1280, 720, 2_000)
        val lowerResolution = Candidate(640, 360, 9_000)
        assertEquals(listOf(highRate, lowRate, lowerResolution), rank(720, lowerResolution, lowRate, highRate))
    }

    @Test fun mapsElementaryCodecsWithoutGuessingUnknownContainers() {
        assertEquals("video/avc", videoSampleMime("video/mp4; codecs=\"avc1.640028\""))
        assertEquals("video/hevc", videoSampleMime("video/mp4; codecs=\"hvc1.1.6\""))
        assertEquals("video/av01", videoSampleMime("video/mp4; codecs=\"av01.0.08M.08\""))
        assertEquals("video/x-vnd.on2.vp9", videoSampleMime("video/webm; codecs=\"vp09.00.51.08\""))
        assertEquals("video/x-vnd.on2.vp9", videoSampleMime("video/webm; codecs=\"vp9\""))
        assertNull(videoSampleMime("video/mp4"))
        assertNull(videoSampleMime("video/mp4; codecs=\"futureCodec\""))
    }

    @Test fun failedVideoFallsBackWithoutRetryLoop() {
        assertEquals(1440, nextLowerVideoLimit(2160, 2160))
        assertEquals(480, nextLowerVideoLimit(720, 2160))
        assertEquals(480, nextLowerVideoLimit(1080, 720))
        assertNull(nextLowerVideoLimit(144, 144))
    }
}
