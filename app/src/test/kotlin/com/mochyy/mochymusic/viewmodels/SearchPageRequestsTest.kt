package com.mochyy.mochymusic.viewmodels

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchPageRequestsTest {
    @Test
    fun repeatedScrollEventsOnlyStartOneRequestPerFilter() {
        val requests = SearchPageRequests()
        assertTrue(requests.begin("songs"))
        assertFalse(requests.begin("songs"))
        assertTrue(requests.begin("albums"))
        assertFalse(requests.begin("songs"))
    }

    @Test
    fun completingOrFailingRequestAllowsRetryWithoutUnlockingOtherFilters() {
        val requests = SearchPageRequests()
        assertTrue(requests.begin("songs"))
        assertTrue(requests.begin("albums"))
        requests.end("songs")
        assertTrue(requests.begin("songs"))
        assertFalse(requests.begin("albums"))
        requests.end("songs")
        requests.end("songs")
        assertTrue(requests.begin("songs"))
    }
}
