package io.github.javcinema.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheCleanupPolicyTest {

    @Test
    fun allSucceeded_reportsPlainSuccess() {
        val outcome = CacheClearOutcome(
            imageCacheCleared = true,
            webViewCacheCleared = true,
            memoryCacheCleared = true
        )
        assertTrue(outcome.allSucceeded)
        assertTrue(outcome.anySucceeded)
        assertEquals("缓存已清理", buildCacheClearMessage(outcome))
    }

    @Test
    fun partialSuccess_listsOnlyFailedItems() {
        val outcome = CacheClearOutcome(
            imageCacheCleared = true,
            webViewCacheCleared = false,
            memoryCacheCleared = true
        )
        assertFalse(outcome.allSucceeded)
        assertTrue(outcome.anySucceeded)
        assertEquals("部分清理完成，失败：网页", buildCacheClearMessage(outcome))
    }

    @Test
    fun multipleFailures_areJoined() {
        val outcome = CacheClearOutcome(
            imageCacheCleared = false,
            webViewCacheCleared = false,
            memoryCacheCleared = true
        )
        assertEquals("部分清理完成，失败：图片、网页", buildCacheClearMessage(outcome))
    }

    @Test
    fun totalFailure_reportsFailure() {
        val outcome = CacheClearOutcome(
            imageCacheCleared = false,
            webViewCacheCleared = false,
            memoryCacheCleared = false
        )
        assertFalse(outcome.anySucceeded)
        assertEquals("清理失败: 图片、网页、内存", buildCacheClearMessage(outcome))
    }
}
