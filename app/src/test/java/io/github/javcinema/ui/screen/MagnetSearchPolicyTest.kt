package io.github.javcinema.ui.screen

import io.github.javcinema.data.model.DownloadLink
import io.github.javcinema.data.model.MagnetFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MagnetSearchPolicyTest {

    @Test
    fun failure_becomesError() {
        val ui = toMagnetSourceUi(false, emptyList(), "timeout")
        assertTrue(ui is MagnetSourceUi.Error)
        assertEquals("timeout", (ui as MagnetSourceUi.Error).message)
    }

    @Test
    fun emptySuccess_isNotError() {
        val ui = toMagnetSourceUi(true, emptyList(), "ignored")
        assertTrue(ui is MagnetSourceUi.Success)
        assertTrue((ui as MagnetSourceUi.Success).items.isEmpty())
    }

    @Test
    fun shouldLoadFiles_onlyWhenMissingAndIdle() {
        assertTrue(shouldLoadFiles(null, alreadyLoading = false))
        assertFalse(shouldLoadFiles(listOf(MagnetFile()), alreadyLoading = false))
        assertFalse(shouldLoadFiles(null, alreadyLoading = true))
        val unused = DownloadLink()
        assertTrue(unused.files == null)
    }

    private fun link(title: String) = DownloadLink().apply { this.title = title }

    @Test
    fun searchResults_dropAdTitlesWhenCleanOnesExist() {
        val items = listOf(
            link("最新地址获取 www.98t.la"),
            link("SSIS-001 一ヶ月間の禁欲の果てに"),
            link("更多资源 http://ad.example.com/")
        )
        val visible = visibleSearchResults(items)
        assertEquals(listOf("SSIS-001 一ヶ月間の禁欲の果てに"), visible.map { it.title })
    }

    @Test
    fun searchResults_keepEverythingWhenAllLookLikeAds() {
        // ⚠️ 关键兜底：广告规则可能整组误伤，此时宁可全带上，也不能让「有结果」变成「未找到结果」。
        val items = listOf(
            link("最新地址获取 www.98t.la"),
            link("更多资源 http://ad.example.com/")
        )
        val visible = visibleSearchResults(items)
        assertEquals(items.map { it.title }, visible.map { it.title })
    }

    @Test
    fun searchResults_keepTheOnlyCandidateEvenIfItLooksLikeAnAd() {
        // UZU-040 同型事故：唯一候选被广告规则命中 → 必须保留。
        val items = listOf(link("SSIS-001 www.98t.la"))
        assertEquals(1, visibleSearchResults(items).size)
    }

    @Test
    fun searchResults_emptyStaysEmpty() {
        assertTrue(visibleSearchResults(emptyList()).isEmpty())
    }
}
