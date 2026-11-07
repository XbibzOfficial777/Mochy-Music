// OnlineSearchViewModel.kt

package com.mochyy.mochymusic.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.models.filterYoutubeShorts
import com.music.innertube.pages.SearchSummaryPage
import com.mochyy.mochymusic.constants.HideExplicitKey
import com.mochyy.mochymusic.constants.HideVideoSongsKey
import com.mochyy.mochymusic.constants.HideYoutubeShortsKey
import com.mochyy.mochymusic.models.ItemsPage
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.get
import com.mochyy.mochymusic.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

@HiltViewModel
class OnlineSearchViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val query = try {
        URLDecoder.decode(savedStateHandle.get<String>("query")!!, "UTF-8")
    } catch (e: IllegalArgumentException) {
        savedStateHandle.get<String>("query")!!
    }
    val filter = MutableStateFlow<YouTube.SearchFilter?>(null)
    var summaryPage by mutableStateOf<SearchSummaryPage?>(null)
    val viewStateMap = mutableStateMapOf<String, ItemsPage?>()

    private val retryRequest = MutableStateFlow(0)
    private val pageRequests = SearchPageRequests()
    val errors = mutableStateMapOf<String, Boolean>()

    private fun errorKey(filter: YouTube.SearchFilter?) = filter?.value ?: "summary"

    fun retry() {
        retryRequest.value += 1
    }

    init {
        viewModelScope.launch {
            combine(filter, retryRequest) { filter, _ -> filter }.collectLatest(::loadInitial)
        }
    }

    private suspend fun loadInitial(filter: YouTube.SearchFilter?) {
        val key = errorKey(filter)
        errors.remove(key)
        if (filter == null && summaryPage != null) return
        if (filter != null && viewStateMap[filter.value] != null) return
        try {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
            val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, true)
            if (filter == null) {
                summaryPage = YouTube.searchSummary(query).getOrThrow()
                    .filterExplicit(hideExplicit)
                    .filterVideoSongs(hideVideoSongs)
                    .filterYoutubeShorts(hideYoutubeShorts)
            } else {
                val result = YouTube.search(query, filter).getOrThrow()
                viewStateMap[filter.value] = ItemsPage(
                    result.items.distinctBy { it.id }
                        .filterExplicit(hideExplicit)
                        .filterVideoSongs(hideVideoSongs)
                        .filterYoutubeShorts(hideYoutubeShorts),
                    result.continuation,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errors[key] = true
            reportException(error)
        }
    }

    fun loadMore() {
        val selectedFilter = filter.value ?: return
        val key = selectedFilter.value
        val page = viewStateMap[key] ?: return
        val continuation = page.continuation ?: return
        // Claim before launching: scroll/layout notifications may arrive in the same frame.
        if (!pageRequests.begin(key)) return
        viewModelScope.launch {
            try {
                errors.remove(key)
                val searchResult = YouTube.searchContinuation(continuation).getOrThrow()
                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
                val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, true)
                val newItems = searchResult.items
                    .filterExplicit(hideExplicit)
                    .filterVideoSongs(hideVideoSongs)
                    .filterYoutubeShorts(hideYoutubeShorts)
                viewStateMap[key] = ItemsPage(
                    (page.items + newItems).distinctBy { it.id },
                    // A repeated token must not trigger an endless pagination loop.
                    searchResult.continuation?.takeUnless { it == continuation },
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errors[key] = true
                reportException(error)
            } finally {
                pageRequests.end(key)
            }
        }
    }
}
