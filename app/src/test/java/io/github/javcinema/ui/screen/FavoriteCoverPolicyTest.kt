package io.github.javcinema.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteCoverPolicyTest {

    private val thumbSmall = "https://img.site/covers/ssis001/ps.jpg"
    private val detailLarge = "https://img.site/covers/ssis001/pl.jpg"
    private val apiSmall = "https://api.site/poster/small/123.jpg"
    private val apiLarge = "https://api.site/poster/large/123.jpg"

    @Test
    fun listThumbnailWins_whenItIsAlreadySmall() {
        assertEquals(thumbSmall, favoriteCoverUrl(thumbSmall, apiSmall, detailLarge))
    }

    @Test
    fun legacyLargeThumbnail_isReplacedByRegisteredSmall() {
        // 老数据里存过大图：从收藏页再进详情时缩略图就是那张大图，不能照原样再存一遍。
        assertEquals(apiSmall, favoriteCoverUrl(detailLarge, apiSmall, apiLarge))
    }

    @Test
    fun legacyLargeThumbnail_derivesSmallFromPlSuffix() {
        // HTML 源没有注册表，只能按 ps/pl 成对的命名反推。
        assertEquals(thumbSmall, favoriteCoverUrl(detailLarge, null, apiLarge))
    }

    @Test
    fun noThumbnail_usesRegisteredSmallOverDetailLarge() {
        assertEquals(apiSmall, favoriteCoverUrl(null, apiSmall, apiLarge))
    }

    @Test
    fun noThumbnailNoRegistry_derivesSmallFromDetailCover() {
        assertEquals(thumbSmall, favoriteCoverUrl(null, null, detailLarge))
    }

    @Test
    fun onlyALargeWithoutSuffix_isUsedAsLastResort() {
        assertEquals(apiLarge, favoriteCoverUrl(null, null, apiLarge))
    }

    @Test
    fun blankRegisteredSmall_isIgnored() {
        // 接口回空字符串时不能把收藏封面清空。
        assertEquals(thumbSmall, favoriteCoverUrl(thumbSmall, "  ", detailLarge))
        assertEquals(thumbSmall, favoriteCoverUrl(null, "", thumbSmall))
    }

    @Test
    fun nothingKnown_staysNull() {
        assertNull(favoriteCoverUrl(null, null, null))
    }
}
