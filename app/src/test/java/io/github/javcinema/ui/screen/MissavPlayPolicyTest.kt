package io.github.javcinema.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MissavPlayPolicyTest {

    @Test
    fun searchUrlUsesEncodedCode() {
        assertEquals("https://missav.ws/en/search/PPPE-443", missavSearchUrl("PPPE-443"))
        assertTrue(isMissavSearchUrl("https://missav.ws/en/search/PPPE-443"))
    }

    @Test
    fun playUrlMatchesCodeAndVariants() {
        assertTrue(isMissavPlayUrl("https://missav.ws/en/pppe-443", "PPPE-443"))
        assertTrue(isMissavPlayUrl("https://missav.ws/en/pppe-443-uncensored-leak", "PPPE-443"))
        assertFalse(isMissavPlayUrl("https://missav.ws/en/search/PPPE-443", "PPPE-443"))
        assertFalse(isMissavPlayUrl("https://missav.ws/en/pppe-437", "PPPE-443"))
    }

    @Test
    fun parseSearchResultsFromHtml() {
        val html = """
            <html><body>
            <h1>Search result of pppe-443</h1>
            <a href="https://missav.ws/en/pppe-443-uncensored-leak">
              <img alt="Miyu Aizawa uncensored"/>Uncensored 2:00:49
            </a>
            <a href="/en/pppe-443">PPPE-443 Miyu Aizawa 2:00:49</a>
            <a href="https://go.myavlive.com/?onlineModels=ad">LIVE ad</a>
            <a href="https://missav.ws/en/pppe-437">unrelated</a>
            </body></html>
        """.trimIndent()
        val results = parseMissavSearchResults(html, "PPPE-443")
        assertEquals(2, results.size)
        assertEquals("https://missav.ws/en/pppe-443-uncensored-leak", results[0].url)
        assertEquals("无码", results[0].badge)
        assertEquals("https://missav.ws/en/pppe-443", results[1].url)
    }

    @Test
    fun blocksAdMainFrameButAllowsMissav() {
        assertTrue(isAllowedMissavNavigation("https://missav.ws/en/pppe-443"))
        assertTrue(isAllowedMissavNavigation("https://challenges.cloudflare.com/cdn-cgi/challenge-platform/x"))
        assertFalse(
            isAllowedMissavNavigation(
                "https://tsyndicate.com/api/v1/direct/c65c80b49bf04c139b29dba8137a6419?8"
            )
        )
        assertFalse(isAllowedMissavNavigation("https://go.myavlive.com/girls", mainFrame = true))
        assertTrue(isAllowedMissavNavigation("https://cdn.example.com/video.m3u8", mainFrame = false))
    }

    @Test
    fun challengeSubstringOnEvilHost_isBlocked() {
        assertFalse(isAllowedMissavNavigation("https://evil.com/?__cf_chl_tk=abc", mainFrame = true))
        assertFalse(isAllowedMissavNavigation("https://evil.com/cdn-cgi/challenge/x", mainFrame = true))
        assertFalse(isAllowedMissavNavigation("javascript:alert(1)", mainFrame = true))
        assertTrue(isAllowedMissavNavigation("about:blank", mainFrame = true))
        assertTrue(isAllowedMissavNavigation("blob:https://missav.ws/abc", mainFrame = true))
    }

    @Test
    fun challengeHtmlIsDetected() {
        assertTrue(isMissavChallengeHtml("<h2>正在进行安全验证</h2>"))
        assertTrue(isMissavChallengeHtml("Just a moment..."))
        assertFalse(isMissavChallengeHtml("<h1>Search result of pppe-443</h1>"))
        assertTrue(isMissavChallengeTitle("请稍候…"))
        assertTrue(isMissavChallengeUrl("https://missav.ws/en/search/PPPE-443?__cf_chl_tk=abc"))
        assertFalse(isMissavChallengeTitle("Search result of pppe-443 - MissAV"))
    }

    @Test
    fun slugIsLastPathSegmentLowercased() {
        assertEquals("pppe-443", missavSlug("https://missav.ws/en/PPPE-443"))
        assertEquals("pppe-443", missavSlug("https://missav.ws/en/PPPE-443/"))
        assertEquals("pppe-443-uncensored-leak", missavSlug("https://missav.ws/en/pppe-443-uncensored-leak"))
        assertEquals(null, missavSlug(null))
        assertEquals(null, missavSlug(""))
        assertEquals(null, missavSlug("https://missav.ws/"))
    }

    /**
     * 站点会把「无码流出」等变体排在番号本体前面，自动接管必须挑番号本体，
     * 否则默认播到的是变体版本。
     */
    @Test
    fun autoSelectPrefersExactCodeOverSiteOrder() {
        val html = """
            <html><body>
            <a href="https://missav.ws/en/pppe-443-uncensored-leak">
              <img alt="Miyu Aizawa uncensored"/>Uncensored 2:00:49
            </a>
            <a href="/en/pppe-443">PPPE-443 Miyu Aizawa 2:00:49</a>
            </body></html>
        """.trimIndent()
        val results = parseMissavSearchResults(html, "PPPE-443")
        assertEquals(2, results.size)
        // 站点排序第一条是变体，精确命中在第二条
        assertEquals("pppe-443-uncensored-leak", missavSlug(results[0].url))
        assertEquals("pppe-443", missavSlug(results[1].url))
        assertEquals("https://missav.ws/en/pppe-443", selectBestMissavResult(results, "PPPE-443")?.url)
    }

    @Test
    fun autoSelectFallsBackToFirstWhenNoExactMatch() {
        val onlyVariant = listOf(
            MissavSearchResult(url = "https://missav.ws/en/pppe-443-uncensored-leak", title = "变体")
        )
        assertEquals(
            "https://missav.ws/en/pppe-443-uncensored-leak",
            selectBestMissavResult(onlyVariant, "PPPE-443")?.url
        )
        // 番号大小写与空格都应被归一化后再比对
        assertEquals(
            "https://missav.ws/en/pppe-443",
            selectBestMissavResult(
                listOf(
                    MissavSearchResult("https://missav.ws/en/pppe-443-x", "变体"),
                    MissavSearchResult("https://missav.ws/en/pppe-443", "本体")
                ),
                " pppe-443 "
            )?.url
        )
    }

    @Test
    fun autoSelectHandlesEmptyInput() {
        assertEquals(null, selectBestMissavResult(emptyList(), "PPPE-443"))
        // 番号为空时不应崩，退回站点排序第一条
        val results = listOf(MissavSearchResult("https://missav.ws/en/pppe-443", "本体"))
        assertEquals("https://missav.ws/en/pppe-443", selectBestMissavResult(results, "  ")?.url)
    }
}
