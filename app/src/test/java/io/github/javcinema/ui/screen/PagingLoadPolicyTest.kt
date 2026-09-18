package io.github.javcinema.ui.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagingLoadPolicyTest {

    @Test
    fun idleWithMore_starts() {
        assertTrue(shouldStartLoadMore(isLoadingMore = false, hasMore = true, isPrimaryLoadActive = false))
    }

    @Test
    fun alreadyLoadingMore_blocked() {
        assertFalse(shouldStartLoadMore(isLoadingMore = true, hasMore = true, isPrimaryLoadActive = false))
    }

    @Test
    fun noMorePages_blocked() {
        assertFalse(shouldStartLoadMore(isLoadingMore = false, hasMore = false, isPrimaryLoadActive = false))
    }

    @Test
    fun primaryLoadActive_blocked() {
        assertFalse(shouldStartLoadMore(isLoadingMore = false, hasMore = true, isPrimaryLoadActive = true))
    }
}
