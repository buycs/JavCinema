package io.github.javcinema.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoLayoutPolicyTest {

    private val delta = 0.0001f

    @Test
    fun landscapeVideoKeepsItsOwnAspectRatio() {
        assertEquals(16f / 9f, VideoLayoutPolicy.displayAspectRatio(1920, 1080), delta)
    }

    @Test
    fun portraitVideoIsNotMistakenForLandscape() {
        val ratio = VideoLayoutPolicy.displayAspectRatio(1080, 1920)
        assertEquals(9f / 16f, ratio, delta)
        assertTrue("竖屏比例必须小于 1", ratio < 1f)
    }

    @Test
    fun ninetyDegreeRotationSwapsWidthAndHeight() {
        // 解码器还没旋转时，1920x1080 实际是竖着放的 1080x1920。
        assertEquals(
            9f / 16f,
            VideoLayoutPolicy.displayAspectRatio(1920, 1080, unappliedRotationDegrees = 90),
            delta
        )
        assertEquals(
            9f / 16f,
            VideoLayoutPolicy.displayAspectRatio(1920, 1080, unappliedRotationDegrees = 270),
            delta
        )
    }

    @Test
    fun oneEightyDegreeRotationDoesNotSwap() {
        assertEquals(
            16f / 9f,
            VideoLayoutPolicy.displayAspectRatio(1920, 1080, unappliedRotationDegrees = 180),
            delta
        )
    }

    @Test
    fun nonSquarePixelsAreApplied() {
        // 变形像素：4:3 的 720x480 若按 1:1 像素算会得到 1.5，
        // 乘上 0.8889 的像素比后才是真实的 1.333（4:3）。
        assertEquals(
            4f / 3f,
            VideoLayoutPolicy.displayAspectRatio(720, 480, pixelWidthHeightRatio = 8f / 9f),
            0.001f
        )
    }

    @Test
    fun unknownSizeFallsBackToFillSentinel() {
        assertEquals(
            VideoLayoutPolicy.UNKNOWN_ASPECT_RATIO,
            VideoLayoutPolicy.displayAspectRatio(0, 0),
            delta
        )
        assertEquals(
            VideoLayoutPolicy.UNKNOWN_ASPECT_RATIO,
            VideoLayoutPolicy.displayAspectRatio(1920, 0),
            delta
        )
        assertEquals(
            VideoLayoutPolicy.UNKNOWN_ASPECT_RATIO,
            VideoLayoutPolicy.displayAspectRatio(-1, 1080),
            delta
        )
    }

    @Test
    fun bogusPixelRatioFallsBackToSquarePixels() {
        // 有些容器会把 pixelWidthHeightRatio 填成 0 / 负数，不能因此把比例算成 0。
        assertEquals(
            16f / 9f,
            VideoLayoutPolicy.displayAspectRatio(1920, 1080, pixelWidthHeightRatio = 0f),
            delta
        )
        assertEquals(
            16f / 9f,
            VideoLayoutPolicy.displayAspectRatio(1920, 1080, pixelWidthHeightRatio = -1f),
            delta
        )
    }

    @Test
    fun unknownSentinelMeansFillNotCrop() {
        // 哨兵值必须是 0：AspectRatioFrameLayout 见到 <= 0 会跳过比例测量、
        // 直接按父容器铺满。若改成别的值，比例未知时就会走进裁剪分支。
        assertEquals(0f, VideoLayoutPolicy.UNKNOWN_ASPECT_RATIO, delta)
    }
}
