package io.github.javcinema.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerControlsPolicyTest {

    @Test
    fun onlyPureTapTogglesControls() {
        // 回归：手势类型一旦新增，只有「没有任何位移」的那种才允许切换控件显隐，
        // 别的都必须走 HIDE 分支，否则新加的手势会把控件莫名翻出来。
        val toggling = GestureMode.entries.filter {
            PlayerControlsPolicy.afterGesture(it) == ControlsAfterGesture.TOGGLE
        }
        assertEquals(listOf(GestureMode.NONE), toggling)
    }

    @Test
    fun dragGesturesHideControls() {
        assertEquals(
            ControlsAfterGesture.HIDE,
            PlayerControlsPolicy.afterGesture(GestureMode.SEEK)
        )
        assertEquals(
            ControlsAfterGesture.HIDE,
            PlayerControlsPolicy.afterGesture(GestureMode.VOLUME)
        )
        assertEquals(
            ControlsAfterGesture.HIDE,
            PlayerControlsPolicy.afterGesture(GestureMode.BRIGHTNESS)
        )
    }

    @Test
    fun visibleIdleControlsShouldAutoHide() {
        assertTrue(PlayerControlsPolicy.shouldAutoHide(controlsVisible = true, touching = false))
    }

    @Test
    fun controlsDoNotHideUnderTheFinger() {
        // 长按超过倒计时时长时，不能把控件从手指底下抽走。
        assertFalse(PlayerControlsPolicy.shouldAutoHide(controlsVisible = true, touching = true))
    }

    @Test
    fun alreadyHiddenControlsHaveNothingToDo() {
        assertFalse(PlayerControlsPolicy.shouldAutoHide(controlsVisible = false, touching = false))
        assertFalse(PlayerControlsPolicy.shouldAutoHide(controlsVisible = false, touching = true))
    }

    @Test
    fun autoHideDelayIsPerceptibleButNotAnnoying() {
        // 太短会一闪而过，太长等于没隐藏。留个区间防止以后被随手改坏。
        assertTrue(
            "自动隐藏延时应在 1~8 秒之间，当前 ${PlayerControlsPolicy.AUTO_HIDE_DELAY_MS}ms",
            PlayerControlsPolicy.AUTO_HIDE_DELAY_MS in 1_000L..8_000L
        )
    }
}
