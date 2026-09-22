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

    private val emptySearchPage = """
        <html><head><title>Search result of ssis-999 - MissAV</title></head><body>
        <p>No results found.</p>
        </body></html>
    """.trimIndent()

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
        val html = """
            <html><head>
            <title>Search result of ssis-999 - MissAV</title>
            <script src="/cdn-cgi/challenge-platform/scripts/jsd/main.js"></script>
            </head><body><p>No results.</p></body></html>
        """.trimIndent()
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
}
