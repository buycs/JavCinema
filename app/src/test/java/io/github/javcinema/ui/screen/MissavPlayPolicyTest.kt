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

    /**
     * 回归：`evaluateJavascript` 回传的是 JSON 字符串字面量，Android 会把 `<` `>` `&` `=` `'`
     * 转义成 `\u003C` 这类 Unicode 转义。早期版本只 replace 了 `\n` / `\"` / `\/`，
     * 漏解 `\uXXXX`，于是 Jsoup 收到满屏 `\u003Cdiv>`，一个标签都认不出来。
     */
    @Test
    fun unescapesAndroidUnicodeEscapesInJsResult() {
        val raw = "\"\\u003Cdiv\\u003Ea\\u0026b\\u003Dc\\u0027d\\u003C/div\\u003E\""
        assertEquals("<div>a&b=c'd</div>", unescapeJsString(raw))
        // 常见的反斜杠转义仍要正常解
        assertEquals("a\"b\nc", unescapeJsString("\"a\\\"b\\nc\""))
        assertEquals("https://x/y.m3u8", unescapeJsString("\"https:\\/\\/x\\/y.m3u8\""))
        assertEquals("", unescapeJsString(null))
        assertEquals("", unescapeJsString("null"))
    }

    /**
     * 端到端回归：真实搜索结果页的卡片长这样 —— `<a href>` 指向
     * `/dm<id>/<lang>/<slug>`（missav 的镜像路径前缀），番号信息全在 slug 里。
     *
     * 片段取自 missav 真实搜索结果页（番号 SSIS-001）。
     */
    @Test
    fun parsesRealMissavSearchCardAfterUnescaping() {
        val realCard = """
            <div x-data="" class="grid grid-cols-2 gap-5">
              <div>
                <div @mouseenter="setPreview('2f372120')" @click="clickPreview('2f372120')" class="thumbnail group">
                  <div class="relative aspect-w-16 aspect-h-9 rounded overflow-hidden shadow-lg">
                    <a href="https://missav.ws/dm52/en/ssis-001-uncensored-leak" alt="ssis-001-uncensored-leak">
                      <video class="preview hidden" data-src="https://fourhoi.com/ssis-001-uncensored-leak/preview.mp4"></video>
                      <img class="w-full" data-src="https://fourhoi.com/ssis-001-uncensored-leak/cover-t.jpg" alt="After Abstaining From Sex For A Month, I Lost My Mind Having Nothing But Infidelity Sex With My Girlfriend's 2 Roommates While She Was Away For 3 Days. Tsukasa Aoi Sayaka Otoshiro">
                    </a>
                    <a href="https://missav.ws/dm52/en/ssis-001-uncensored-leak" alt="ssis-001-uncensored-leak">
                      <span class="absolute bottom-1 left-1">Uncensored</span>
                    </a>
                    <a href="https://missav.ws/dm52/en/ssis-001-uncensored-leak" alt="ssis-001-uncensored-leak">
                      <span class="absolute bottom-1 right-1">2:27:06</span>
                    </a>
                  </div>
                </div>
              </div>
            </div>
        """.trimIndent()

        // 模拟 Android 对 evaluateJavascript 结果的转义
        val raw = "\"" + realCard
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("<", "\\u003C")
            .replace(">", "\\u003E")
            .replace("&", "\\u0026")
            .replace("=", "\\u003D")
            .replace("'", "\\u0027") + "\""

        val results = parseMissavSearchResults(unescapeJsString(raw), "SSIS-001")
        assertEquals(1, results.size)
        assertEquals("https://missav.ws/dm52/en/ssis-001-uncensored-leak", results[0].url)
        assertEquals("2:27:06", results[0].duration)
        assertEquals("无码", results[0].badge)
        assertEquals("https://fourhoi.com/ssis-001-uncensored-leak/cover-t.jpg", results[0].thumbnailUrl)
        assertTrue(results[0].title.startsWith("After Abstaining From Sex For A Month"))
        // /dm<id>/ 前缀不能影响番号识别，也不能被当成搜索页
        assertTrue(isMissavPlayUrl(results[0].url, "SSIS-001"))
        assertEquals("ssis-001-uncensored-leak", missavSlug(results[0].url))
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

    /**
     * 回归：Cloudflare 的 JS Detections 会把 `/cdn-cgi/challenge-platform/scripts/jsd/main.js`
     * 注入到**每一个正常页面**。早期版本拿 "cdn-cgi/challenge" 子串当验证页判据，
     * 于是已通过验证的正常搜索结果页被误判成验证页 —— 表现为「明明搜到了结果却不播」，
     * 且直接掉进回退分支，用户看到的是站点页面而不是播放器。
     */
    @Test
    fun normalPageCarryingCloudflareJsDetectionIsNotChallenge() {
        val html = """
            <html><head>
            <title>Search result of ssis-001 - MissAV | Watch HD JAV Online</title>
            <script src="/cdn-cgi/challenge-platform/scripts/jsd/main.js"></script>
            </head><body>
            <a href="/en/ssis-001">SSIS-001 After Abstaining From Sex 2:27:06</a>
            </body></html>
        """.trimIndent()
        assertFalse(isMissavChallengeHtml(html))
        // 关键：正常页面必须能解析出候选，不能被误判掐断
        assertEquals(1, parseMissavSearchResults(html, "SSIS-001").size)
    }

    /** 真正的 Cloudflare 插页仍然要能识别出来，否则会对着验证页干等 20 秒。 */
    @Test
    fun realChallengeInterstitialIsStillDetected() {
        val html = """
            <html><head><title>Just a moment...</title></head>
            <body>
            <div id="challenge-running"></div>
            <form id="challenge-form" action="/cdn-cgi/challenge-platform/h/b/orchestrate/chl_page/v1"></form>
            <script>window._cf_chl_opt = {cvId: '3'};</script>
            </body></html>
        """.trimIndent()
        assertTrue(isMissavChallengeHtml(html))
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
