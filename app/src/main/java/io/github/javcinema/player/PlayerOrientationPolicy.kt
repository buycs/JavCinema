package io.github.javcinema.player

import android.content.res.Configuration
import android.content.pm.ActivityInfo

/**
 * 播放器进出时的屏幕方向策略。
 *
 * ## 为什么需要这个策略（血泪）
 *
 * 播放器会把 Activity 的 `requestedOrientation` 改成 `SENSOR_LANDSCAPE` 强制横屏。
 * 退出时必须还回去，但**不能简单还原成 `UNSPECIFIED`**：
 *
 * 当系统「自动旋转」是**关闭**的（`Settings.System.ACCELEROMETER_ROTATION == 0`）时，
 * `UNSPECIFIED` 的语义不是「回竖屏」，而是「保持当前朝向」——于是退出播放器后
 * 整个应用仍然是横屏。更糟的是系统会顺手把当前朝向写进
 * `Settings.System.USER_ROTATION`（**系统级**设置），于是应用重启也还是横屏，
 * 用户会以为「整个应用被改成横屏了」。
 *
 * 所以：自动旋转关着时，必须**显式**指定一个方向；用进入播放器前记下的物理朝向
 * 来决定是竖还是横，这样手机和平板都不会被弄错。
 */
internal object PlayerOrientationPolicy {

    /**
     * 算出退出播放器时应该恢复成的 `requestedOrientation`。
     *
     * @param previousRequested 进入播放器前 `Activity.requestedOrientation` 的原始值。
     *   只要不是 `UNSPECIFIED`（说明应用/其它页面自己声明过固定方向），就原样还回去，
     *   不替它做决定。
     * @param autoRotateEnabled 系统自动旋转是否开启。
     * @param orientationAtEntry 进入播放器那一刻的物理朝向，
     *   取值 `Configuration.ORIENTATION_PORTRAIT` / `ORIENTATION_LANDSCAPE`。
     */
    fun restoreOrientation(
        previousRequested: Int,
        autoRotateEnabled: Boolean,
        orientationAtEntry: Int
    ): Int = when {
        previousRequested != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED -> previousRequested

        // 自动旋转开着：交回系统跟随重力传感器，不会粘住。
        autoRotateEnabled -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        // 自动旋转关着：必须显式还回进入时的朝向（见类注释）。
        // 用 LANDSCAPE/PORTRAIT 而不是 SENSOR_* —— 自动旋转关着时用户就是要
        // 「锁死在这个方向」，用传感器方向反而会违背用户意图。
        orientationAtEntry == Configuration.ORIENTATION_LANDSCAPE ->
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}
