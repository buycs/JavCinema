package io.github.javcinema.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerGesturePolicyTest {

    private val width = 1000f

    @Test
    fun leftSideSeeksBackAndRightSideSeeksForward() {
        assertEquals(-DOUBLE_TAP_STEP_MS, doubleTapSeekDelta(x = 100f, width = width))
        assertEquals(DOUBLE_TAP_STEP_MS, doubleTapSeekDelta(x = 900f, width = width))
    }

    @Test
    fun middleBandIgnoresDoubleTap() {
        // 屏幕正中是用户最容易随手点的地方，留一条不响应双击的带子，
        // 免得「想看控件」变成「跳了 10 秒」。
        assertEquals(0L, doubleTapSeekDelta(x = 450f, width = width))
        assertEquals(0L, doubleTapSeekDelta(x = 500f, width = width))
        assertEquals(0L, doubleTapSeekDelta(x = 550f, width = width))
    }

    @Test
    fun degenerateWidthDoesNotSeek() {
        // 布局还没测出来时 width 可能是 0；这时不能瞎跳。
        assertEquals(0L, doubleTapSeekDelta(x = 0f, width = 0f))
        assertEquals(0L, doubleTapSeekDelta(x = 100f, width = -1f))
    }

    @Test
    fun doubleTapWindowMatchesSystemTimeout() {
        assertTrue(isDoubleTap(0L))
        assertTrue(isDoubleTap(DOUBLE_TAP_TIMEOUT_MS))
        assertFalse(isDoubleTap(DOUBLE_TAP_TIMEOUT_MS + 1))
    }

    @Test
    fun clockGoingBackwardsIsNotADoubleTap() {
        // 负的时间差只可能来自时钟异常，绝不能被当成双击 —— 否则会莫名其妙跳进度。
        assertFalse(isDoubleTap(-1L))
        assertFalse(isDoubleTap(Long.MIN_VALUE))
    }

    @Test
    fun longPressNeedsFullTimeout() {
        assertFalse(isLongPress(LONG_PRESS_TIMEOUT_MS - 1))
        assertTrue(isLongPress(LONG_PRESS_TIMEOUT_MS))
    }

    @Test
    fun clampKeepsTargetInsideDuration() {
        assertEquals(0L, clampSeekPosition(-5000L, 100_000L))
        assertEquals(100_000L, clampSeekPosition(999_999L, 100_000L))
        assertEquals(30_000L, clampSeekPosition(30_000L, 100_000L))
    }

    @Test
    fun unknownDurationOnlyClampsToNonNegative() {
        // ⚠️ 时长未知时**不能**把目标夹成 0：双击快进会把进度直接拉回开头。
        assertEquals(10_000L, clampSeekPosition(10_000L, 0L))
        assertEquals(0L, clampSeekPosition(-1L, 0L))
    }

    @Test
    fun scrubMapsTouchPositionToTime() {
        val duration = 100_000L
        assertEquals(0L, scrubPositionFor(x = 0f, width = width, durationMs = duration))
        assertEquals(50_000L, scrubPositionFor(x = 500f, width = width, durationMs = duration))
        assertEquals(duration, scrubPositionFor(x = width, width = width, durationMs = duration))
    }

    @Test
    fun scrubClampsOutOfRangeTouches() {
        val duration = 100_000L
        assertEquals(duration, scrubPositionFor(x = 1500f, width = width, durationMs = duration))
        assertEquals(0L, scrubPositionFor(x = -50f, width = width, durationMs = duration))
    }

    @Test
    fun scrubWithoutDurationOrWidthStaysAtZero() {
        assertEquals(0L, scrubPositionFor(x = 500f, width = width, durationMs = 0L))
        assertEquals(0L, scrubPositionFor(x = 500f, width = 0f, durationMs = 100_000L))
    }

    @Test
    fun seekFlashShowsSignAndSeconds() {
        assertEquals("+10秒", formatSeekFlash(10_000L))
        assertEquals("-10秒", formatSeekFlash(-10_000L))
    }

    @Test
    fun longPressSpeedNeedsAFingerOnTheVideo() {
        assertTrue(shouldStartLongPressSpeed(false, true, false, GestureMode.NONE))
        // 手指已抬起 / 已被锁定 / 已经进入某个手势（拖动、亮度、音量）都不该启动。
        assertFalse(shouldStartLongPressSpeed(false, false, false, GestureMode.NONE))
        assertFalse(shouldStartLongPressSpeed(true, true, false, GestureMode.NONE))
        assertFalse(shouldStartLongPressSpeed(false, true, false, GestureMode.SEEK))
    }

    /**
     * ⚠️ 回归：**拖进度条时按住不动，不能触发长按快放。**
     *
     * 拖进度条时手指本来就按着不动 —— 不加 [isScrubbing] 判断的话，拖到一半
     * 停顿 0.5s 就会被当成「长按快放」，实际播放速度被切到 3x
     * （实测复现：按住进度条 0.6s，顶部弹出「快放中 3.0x」）。
     */
    @Test
    fun scrubbingSuppressesLongPressSpeed() {
        assertFalse(shouldStartLongPressSpeed(false, true, true, GestureMode.NONE))
        assertFalse(shouldStartLongPressSpeed(false, true, true, GestureMode.SEEK))
    }
}
