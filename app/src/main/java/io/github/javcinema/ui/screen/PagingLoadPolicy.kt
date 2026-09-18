package io.github.javcinema.ui.screen

internal fun shouldStartLoadMore(
    isLoadingMore: Boolean,
    hasMore: Boolean,
    isPrimaryLoadActive: Boolean
): Boolean = !isLoadingMore && hasMore && !isPrimaryLoadActive
