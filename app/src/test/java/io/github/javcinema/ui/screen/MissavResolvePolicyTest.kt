package io.github.javcinema.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MissavResolvePolicyTest {

    private val searchPage = """
        <html><head><title>Search result of ssis-001 - MissAV</title></head><body>
        <a href="/en/ssis-001">SSIS-001 After Abstaining From Sex 2:27:06</a>
        </body></html>
    """.trimIndent()

    /**
     * 把合成页面撑到**真实量级**。
     *
     * 真实搜索结果页实测 **252,977 字节**（标定见 `looksLikeMissavPage`），而该判据要求页面达到
     * [MISSAV_MIN_PAGE_LENGTH] 才算「真的加载出来了」—— 合成夹具若只有几百字节，判据校准就会
     * 失真，把「正常但无结果」误判成「页面没加载出来」。这里补一段填充，只为把体积撑到真实量级。
     */
    private fun realisticPage(head: String, body: String): String = buildString {
        append("<html><head>").append(head).append("</head><body>").append(body)
        // 真实页面的导航 / 页脚 / 内联脚本占了绝大部分体积。
        repeat(200) { append("<div class=\"nav\">missav.ws navigation footer</div>") }
        append("</body></html>")
    }

    private val emptySearchPage = realisticPage(
        head = "<title>Search result of ssis-999 - MissAV</title>",
        body = "<p>No results found.</p>"
    )

    private val challengePage = """
        <html><head><title>Just a moment...</title></head><body>
        <div id="challenge-running"></div>
        <form id="challenge-form" action="/cdn-cgi/challenge-platform/h/b/orchestrate/chl_page/v1"></form>
        </body></html>
    """.trimIndent()

    @Test
    fun playsWhenCandidatesFound() {
        val decision = decideSearchOutcome(searchPage, "SSIS-001", attempt = 0)
        assertEquals(MissavResolveAction.PLAY, decision.action)
        assertEquals("https://missav.ws/en/ssis-001", decision.url)
        assertEquals(1, decision.candidates)
    }

    /** 验证插页不能被当成「未收录」—— 过一次验证就能继续，报未收录就把路堵死了。 */
    @Test
    fun challengePageIsReportedAsChallengeNotNotFound() {
        val decision = decideSearchOutcome(challengePage, "SSIS-001", attempt = 0)
        assertEquals(MissavResolveAction.CHALLENGE, decision.action)
        assertEquals(null, decision.url)
    }

    /** 列表是异步渲染的：第一轮空要再给一次机会，别急着报「未收录」。 */
    @Test
    fun emptyFirstAttemptRetriesBeforeGivingUp() {
        assertEquals(
            MissavResolveAction.RETRY,
            decideSearchOutcome(emptySearchPage, "SSIS-999", attempt = 0).action
        )
        assertEquals(
            MissavResolveAction.NOT_FOUND,
            decideSearchOutcome(emptySearchPage, "SSIS-999", attempt = 1).action
        )
    }

    /** 用户指定的文案，别顺手改。 */
    @Test
    fun notFoundMessageMatchesSpec() {
        assertEquals("资源库还未收录，播放失败", MISSAV_NOT_FOUND_MESSAGE)
    }

    /** 端到端：无码优先在解析决策这一层也生效。 */
    @Test
    fun decisionPicksUncensoredVariant() {
        val html = """
            <html><body>
            <a href="/en/ssis-001">SSIS-001 本体 2:27:06</a>
            <a href="/en/ssis-001-uncensored-leak">Uncensored 2:27:06</a>
            </body></html>
        """.trimIndent()
        val decision = decideSearchOutcome(html, "SSIS-001", attempt = 0)
        assertEquals(MissavResolveAction.PLAY, decision.action)
        assertEquals("https://missav.ws/en/ssis-001-uncensored-leak", decision.url)
    }

    /**
     * 回归：带 Cloudflare JS Detections 的正常页面不能被判成验证页 ——
     * 判据用错的话，明明搜到结果却把人送去过验证。
     */
    @Test
    fun normalPageWithCloudflareJsDetectionStillPlays() {
        val html = """
            <html><head>
            <title>Search result of ssis-001 - MissAV</title>
            <script src="/cdn-cgi/challenge-platform/scripts/jsd/main.js"></script>
            </head><body><a href="/en/ssis-001">SSIS-001 2:27:06</a></body></html>
        """.trimIndent()
        assertEquals(
            MissavResolveAction.PLAY,
            decideSearchOutcome(html, "SSIS-001", attempt = 0).action
        )
    }

    /** 正常页面解析不出候选时，只能判未收录，绝不能因为「长得像验证页」被判 CHALLENGE。 */
    @Test
    fun emptyNormalPageIsNotFoundNotChallenge() {
        val html = realisticPage(
            head = "<title>Search result of ssis-999 - MissAV</title>" +
                "<script src=\"/cdn-cgi/challenge-platform/scripts/jsd/main.js\"></script>",
            body = "<p>No results.</p>"
        )
        assertEquals(
            MissavResolveAction.NOT_FOUND,
            decideSearchOutcome(html, "SSIS-999", attempt = 1).action
        )
    }

    /** 验证是否已通过，要同时看 URL 与标题。 */
    @Test
    fun challengePassedOnlyWhenUrlAndTitleAreBothClean() {
        assertTrue(
            isChallengePassed(
                "https://missav.ws/en/search/ssis-001",
                "Search result of ssis-001 - MissAV"
            )
        )
        assertFalse(
            isChallengePassed(
                "https://missav.ws/en/search/ssis-001?__cf_chl_tk=abc",
                "Search result of ssis-001"
            )
        )
        assertFalse(isChallengePassed("https://missav.ws/en/search/ssis-001", "请稍候…"))
        assertFalse(isChallengePassed("https://missav.ws/en/search/ssis-001", "Just a moment..."))
    }

    /** 还在验证页上就继续等 —— 验证得用户亲手点，程序不该替他决定「已经过了」。 */
    @Test
    fun stillOnChallengePageKeepsWaiting() {
        assertEquals(
            MissavChallengeOutcome.WAIT,
            decideChallengeOutcome(passed = false, restarts = 0)
        )
        assertEquals(
            MissavChallengeOutcome.WAIT,
            decideChallengeOutcome(passed = false, restarts = MISSAV_CHALLENGE_MAX_RESTARTS)
        )
    }

    /** 验证通过就重走整个解析流程 —— 这是「首次验证后播放」与「再次播放」体验一致的关键。 */
    @Test
    fun passedChallengeRestartsResolve() {
        assertEquals(
            MissavChallengeOutcome.RESTART,
            decideChallengeOutcome(passed = true, restarts = 0)
        )
    }

    /** 边界：预算还没用完（用掉 max-1 次）时仍要重走，别提前放弃。 */
    @Test
    fun restartAllowedUntilBudgetIsExhausted() {
        assertEquals(
            MissavChallengeOutcome.RESTART,
            decideChallengeOutcome(passed = true, restarts = MISSAV_CHALLENGE_MAX_RESTARTS - 1)
        )
        assertEquals(
            MissavChallengeOutcome.GIVE_UP,
            decideChallengeOutcome(passed = true, restarts = MISSAV_CHALLENGE_MAX_RESTARTS)
        )
    }

    /**
     * 验证判据是否定式的（「当前页不像验证页」），万一被误判成已通过，
     * 重走会再次撞上验证页 —— 所以必须有上限，超了就回退站点而不是无限空转。
     */
    @Test
    fun repeatedFalsePassEventuallyGivesUp() {
        assertEquals(
            MissavChallengeOutcome.GIVE_UP,
            decideChallengeOutcome(passed = true, restarts = MISSAV_CHALLENGE_MAX_RESTARTS + 5)
        )
    }

    /** 预算上限可注入，便于以后调参时不必改测试。 */
    @Test
    fun maxRestartsIsConfigurable() {
        assertEquals(
            MissavChallengeOutcome.RESTART,
            decideChallengeOutcome(passed = true, restarts = 0, maxRestarts = 1)
        )
        assertEquals(
            MissavChallengeOutcome.GIVE_UP,
            decideChallengeOutcome(passed = true, restarts = 1, maxRestarts = 1)
        )
    }

    /** 上限必须是个正数，否则「通过即放弃」，等于验证功能白做。 */
    @Test
    fun challengeRestartBudgetIsPositive() {
        assertTrue(MISSAV_CHALLENGE_MAX_RESTARTS > 0)
    }

    // ── 页面没加载出来 ≠ 站点没收录 ────────────────────────────────────────
    // 用户可见症状与 UZU-040 相同（「有资源却提示没有资源」），但根因不同：
    // 这里是网络故障 / Cloudflare 5xx 的错误页被当成了「没有结果的正常页」。

    /** 站点自己的页面无论有没有搜索结果，都必然带着自己的域名，且体积很大。 */
    @Test
    fun looksLikeMissavPage_requiresSiteMarkerAndRealisticLength() {
        assertTrue(looksLikeMissavPage(realisticPage("", "<p>No results.</p>")))
        // 有域名但只是空壳 —— 页面没渲染出来
        assertFalse(looksLikeMissavPage("<html><body>missav</body></html>"))
        // 够长但没有站点标记 —— Chromium / Cloudflare 错误页
        assertFalse(looksLikeMissavPage("<html><body>" + "x".repeat(20000) + "</body></html>"))
        assertFalse(looksLikeMissavPage(""))
    }

    /** 空壳页面：最后一轮也不能报「未收录」，必须回退站点。 */
    @Test
    fun emptyShellPageFallsBackInsteadOfNotFound() {
        assertEquals(
            MissavResolveAction.RETRY,
            decideSearchOutcome("", "SSIS-999", attempt = 0).action
        )
        assertEquals(
            MissavResolveAction.FALLBACK,
            decideSearchOutcome("", "SSIS-999", attempt = 1).action
        )
    }

    /** Cloudflare 5xx 错误页：够长但不含站点标记 → 同样回退站点，不能报未收录。 */
    @Test
    fun cloudflareErrorPageFallsBackInsteadOfNotFound() {
        val html = "<html><head><title>502 Bad Gateway</title></head><body>" +
            "<h1>Error 502</h1><p>cloudflare</p>" + "x".repeat(20000) + "</body></html>"
        assertEquals(
            MissavResolveAction.FALLBACK,
            decideSearchOutcome(html, "SSIS-999", attempt = 1).action
        )
    }

    /** 反例守卫：真实的「无结果」页必须**仍然**报未收录，别为了兜底把正常结论也吞掉。 */
    @Test
    fun realNoResultPageStillReportsNotFound() {
        assertEquals(
            MissavResolveAction.NOT_FOUND,
            decideSearchOutcome(emptySearchPage, "SSIS-999", attempt = 1).action
        )
    }
}
