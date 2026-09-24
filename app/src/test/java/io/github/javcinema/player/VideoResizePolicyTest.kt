package io.github.javcinema.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VideoResizePolicyTest {

    @Test
    fun defaultModeIsFit() {
        // 默认必须是「适应」：站点上 16:9 / 4:3 / 竖屏片源混在一起，
        // 只有它保证任何一种都不变形、不丢画面。
        assertEquals(VideoResizeMode.FIT, VideoResizeMode.entries.first())
    }

    @Test
    fun cyclesFitZoomFillAndWraps() {
        assertEquals(VideoResizeMode.ZOOM, nextVideoResizeMode(VideoResizeMode.FIT))
        assertEquals(VideoResizeMode.FILL, nextVideoResizeMode(VideoResizeMode.ZOOM))
        assertEquals(VideoResizeMode.FIT, nextVideoResizeMode(VideoResizeMode.FILL))
    }

    @Test
    fun everyModeHasDistinctNonBlankLabel() {
        // 标签直接显示在按钮上；重名或空白会让用户以为按钮坏了。
        val labels = VideoResizeMode.entries.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
        labels.forEach { label -> assertNotEquals("", label.trim()) }
    }
}
