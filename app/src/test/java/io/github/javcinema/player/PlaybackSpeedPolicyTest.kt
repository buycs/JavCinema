package io.github.javcinema.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSpeedPolicyTest {

    @Test
    fun cyclesThroughAllStepsAndWrapsAround() {
        // 从默认 1.0x 起连点一整圈：每一档都必须走到，最后回到起点 —— 不能卡住也不能漏档。
        val visited = mutableListOf(DEFAULT_PLAYBACK_SPEED)
        var speed = DEFAULT_PLAYBACK_SPEED
        repeat(PLAYBACK_SPEED_STEPS.size) {
            speed = nextPlaybackSpeed(speed)
            visited += speed
        }
        assertEquals(DEFAULT_PLAYBACK_SPEED, speed)
        assertEquals(PLAYBACK_SPEED_STEPS.toSet(), visited.toSet())

        // 最后一档再点一次回到第一档
        assertEquals(PLAYBACK_SPEED_STEPS.first(), nextPlaybackSpeed(PLAYBACK_SPEED_STEPS.last()))
    }

    @Test
    fun unknownSpeedFallsBackToNearestOneX() {
        // 长按快放会把播放器设成 3.0x，那个值不在档位表里。
        // 此时按钮点一下**必须**有反应 —— 落到 1.0x 这一档，而不是原地不动。
        assertEquals(DEFAULT_PLAYBACK_SPEED, nextPlaybackSpeed(LONG_PRESS_SPEED))
        assertEquals(DEFAULT_PLAYBACK_SPEED, nextPlaybackSpeed(999f))
    }

    @Test
    fun emptyStepsDoesNotCrash() {
        assertEquals(DEFAULT_PLAYBACK_SPEED, nextPlaybackSpeed(1.0f, emptyList()))
    }

    @Test
    fun floatStepsMatchDespiteFloatImprecision() {
        // 0.75 / 1.25 这类值来自常量表，比较必须带容差，否则会走「未知值」分支
        // （表现就是按钮点了没反应）。这里顺带钉住档位顺序。
        assertEquals(1.0f, nextPlaybackSpeed(0.75f))
        assertEquals(1.5f, nextPlaybackSpeed(1.25f))
        assertEquals(0.5f, nextPlaybackSpeed(2.0f))
    }

    @Test
    fun formatsWholeSpeedsWithOneDecimal() {
        assertEquals("1.0x", formatPlaybackSpeed(1.0f))
        assertEquals("2.0x", formatPlaybackSpeed(2.0f))
    }

    @Test
    fun formatsFractionalSpeedsAsIs() {
        assertEquals("0.5x", formatPlaybackSpeed(0.5f))
        assertEquals("0.75x", formatPlaybackSpeed(0.75f))
        assertEquals("1.25x", formatPlaybackSpeed(1.25f))
        assertEquals("1.5x", formatPlaybackSpeed(1.5f))
    }

    @Test
    fun everyStepHasReadableLabel() {
        // 标签会直接显示在按钮上，任何一档都不该出现 "1.0x" 以外的怪格式（比如 1.2500001x）。
        val labels = PLAYBACK_SPEED_STEPS.map(::formatPlaybackSpeed)
        assertEquals(listOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x"), labels)
    }
}
