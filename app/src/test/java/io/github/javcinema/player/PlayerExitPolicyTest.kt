package io.github.javcinema.player

import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerExitPolicyTest {

    private val portrait = Configuration.ORIENTATION_PORTRAIT
    private val landscape = Configuration.ORIENTATION_LANDSCAPE
    private val undefined = Configuration.ORIENTATION_UNDEFINED

    /** 窗口已经转回竖屏 → 立刻退出，不要再等。 */
    @Test
    fun popsAsSoonAsWindowIsPortrait() {
        assertEquals(
            PlayerExitDecision.POP,
            decidePlayerExit(orientation = portrait, waitedMs = 0)
        )
    }

    /** 还是横屏 → 继续等，这是修复「退出时画面撕裂」的关键分支。 */
    @Test
    fun waitsWhileStillLandscape() {
        assertEquals(
            PlayerExitDecision.WAIT,
            decidePlayerExit(orientation = landscape, waitedMs = 0)
        )
        assertEquals(
            PlayerExitDecision.WAIT,
            decidePlayerExit(orientation = landscape, waitedMs = 900)
        )
    }

    /** 朝向未知时按「还没转完」处理 —— 宁可多等一帧，也别抢在旋转中间 pop。 */
    @Test
    fun treatsUnknownOrientationAsStillRotating() {
        assertEquals(
            PlayerExitDecision.WAIT,
            decidePlayerExit(orientation = undefined, waitedMs = 0)
        )
    }

    /** 兜底：方向请求没生效时不能把用户困在播放页。 */
    @Test
    fun givesUpAfterTimeout() {
        assertEquals(
            PlayerExitDecision.POP,
            decidePlayerExit(orientation = landscape, waitedMs = PLAYER_EXIT_ORIENTATION_TIMEOUT_MS)
        )
        assertEquals(
            PlayerExitDecision.POP,
            decidePlayerExit(orientation = landscape, waitedMs = PLAYER_EXIT_ORIENTATION_TIMEOUT_MS + 500)
        )
    }

    /** 超时前一刻仍要等，边界不能提前放弃。 */
    @Test
    fun waitsUntilTimeoutIsReached() {
        assertEquals(
            PlayerExitDecision.WAIT,
            decidePlayerExit(
                orientation = landscape,
                waitedMs = PLAYER_EXIT_ORIENTATION_TIMEOUT_MS - 1
            )
        )
    }

    /** 超时可注入，便于以后调参。 */
    @Test
    fun timeoutIsConfigurable() {
        assertEquals(
            PlayerExitDecision.POP,
            decidePlayerExit(orientation = landscape, waitedMs = 200, timeoutMs = 200)
        )
        assertEquals(
            PlayerExitDecision.WAIT,
            decidePlayerExit(orientation = landscape, waitedMs = 199, timeoutMs = 200)
        )
    }

    /** 兜底时限必须是个正数，否则等于「一按返回就 pop」，修复就白做了。 */
    @Test
    fun timeoutIsPositive() {
        assertTrue(PLAYER_EXIT_ORIENTATION_TIMEOUT_MS > 0)
    }
}
