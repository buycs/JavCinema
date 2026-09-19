package io.github.javcinema.player

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerOrientationPolicyTest {

    private val unspecified = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private val portrait = Configuration.ORIENTATION_PORTRAIT
    private val landscape = Configuration.ORIENTATION_LANDSCAPE

    @Test
    fun keepsAppsOwnFixedOrientation() {
        // 应用（或清单）自己声明了固定方向时，原样还回去，不替它做决定。
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            PlayerOrientationPolicy.restoreOrientation(
                previousRequested = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                autoRotateEnabled = false,
                orientationAtEntry = portrait
            )
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            PlayerOrientationPolicy.restoreOrientation(
                previousRequested = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
                autoRotateEnabled = false,
                orientationAtEntry = landscape
            )
        )
    }

    @Test
    fun autoRotateOnHandsControlBackToSensor() {
        assertEquals(
            unspecified,
            PlayerOrientationPolicy.restoreOrientation(
                previousRequested = unspecified,
                autoRotateEnabled = true,
                orientationAtEntry = portrait
            )
        )
        assertEquals(
            unspecified,
            PlayerOrientationPolicy.restoreOrientation(
                previousRequested = unspecified,
                autoRotateEnabled = true,
                orientationAtEntry = landscape
            )
        )
    }

    @Test
    fun autoRotateOffRestoresExplicitPortrait() {
        // 核心回归：自动旋转关 + 进播放器时是竖屏 → 必须显式还回 PORTRAIT。
        // 若还成 UNSPECIFIED，系统会「保持横屏」并把它写进 USER_ROTATION，
        // 退出播放器后整个应用乃至桌面都横着。
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            PlayerOrientationPolicy.restoreOrientation(
                previousRequested = unspecified,
                autoRotateEnabled = false,
                orientationAtEntry = portrait
            )
        )
    }

    @Test
    fun autoRotateOffOnLandscapeDeviceStaysLandscape() {
        // 平板横着用、自动旋转关：不能被硬掰成竖屏。
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            PlayerOrientationPolicy.restoreOrientation(
                previousRequested = unspecified,
                autoRotateEnabled = false,
                orientationAtEntry = landscape
            )
        )
    }

    @Test
    fun unknownEntryOrientationFallsBackToPortrait() {
        // Configuration.ORIENTATION_UNDEFINED 等异常值走 else 分支，取手机最常见的方向。
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            PlayerOrientationPolicy.restoreOrientation(
                previousRequested = unspecified,
                autoRotateEnabled = false,
                orientationAtEntry = Configuration.ORIENTATION_UNDEFINED
            )
        )
    }

    @Test
    fun neverReturnsSensorLandscapeAsRestoreTarget() {
        // 播放器自己用的 SENSOR_LANDSCAPE 绝不能出现在恢复值里，否则就是没还回去。
        val candidates = listOf(portrait, landscape, Configuration.ORIENTATION_UNDEFINED)
        for (auto in listOf(true, false)) {
            for (entry in candidates) {
                val restored = PlayerOrientationPolicy.restoreOrientation(
                    previousRequested = unspecified,
                    autoRotateEnabled = auto,
                    orientationAtEntry = entry
                )
                assertEquals(
                    "autoRotate=$auto entry=$entry 不应恢复成 SENSOR_LANDSCAPE",
                    false,
                    restored == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                )
            }
        }
    }
}
