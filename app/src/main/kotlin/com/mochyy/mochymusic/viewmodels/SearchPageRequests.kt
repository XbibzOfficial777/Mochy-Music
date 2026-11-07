package com.mochyy.mochymusic.viewmodels

// Accessed on the main thread, just like the search page state.
internal class SearchPageRequests {
    private val activeFilters = mutableSetOf<String>()

    fun begin(filter: String): Boolean = activeFilters.add(filter)

    fun end(filter: String) {
        activeFilters.remove(filter)
    }
}
