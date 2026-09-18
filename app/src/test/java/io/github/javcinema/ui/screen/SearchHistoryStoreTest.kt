package io.github.javcinema.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHistoryStoreTest {

    @Test
    fun scopesUseDifferentKeys() {
        assertEquals("history_movies", searchHistoryKey(SearchScope.MOVIES))
        assertEquals("history_actresses", searchHistoryKey(SearchScope.ACTRESSES))
        assertEquals("history_favorites", searchHistoryKey(SearchScope.FAVORITES))
    }

    @Test
    fun prependKeepsLatestFirstAndDropsDuplicates() {
        val updated = prependSearchHistory(listOf("旧词", "三上"), "三上")
        assertEquals(listOf("三上", "旧词"), updated)
    }
}
