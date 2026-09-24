package io.github.javcinema.player

import kotlin.math.abs
import kotlin.math.round

/**
 * 倍速播放的档位与循环规则。
 *
 * 抽成纯函数是为了能单测 —— 倍速按钮是个「点一下换一档」的循环按钮，
 * 档位表、边界回绕、标签格式都不该靠手点去验。
 */

/** 可选倍速档位，从慢到快。按这个顺序循环。 */
internal val PLAYBACK_SPEED_STEPS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

/** 默认倍速。 */
internal const val DEFAULT_PLAYBACK_SPEED = 1.0f

/** 浮点比较容差：0.75f 这类值直接 `==` 比不可靠。 */
private const val SPEED_EPSILON = 0.001f

/**
 * 下一个档位，到末尾**回绕到第一个**。
 *
 * `current` 不在档位表里时（比如长按快放期间的 3.0x、或以后改了档位表）回到最接近 1x 的档位，
 * 而不是抛异常或原地不动 —— 按钮点一下必须有反应，否则用户会以为坏了。
 */
internal fun nextPlaybackSpeed(
    current: Float,
    steps: List<Float> = PLAYBACK_SPEED_STEPS
): Float {
    if (steps.isEmpty()) return DEFAULT_PLAYBACK_SPEED
    val index = steps.indexOfFirst { abs(it - current) < SPEED_EPSILON }
    if (index < 0) return steps.firstOrNull { it >= DEFAULT_PLAYBACK_SPEED } ?: steps.first()
    return steps[(index + 1) % steps.size]
}

/**
 * 按钮上的文案。整数倍速补一位小数（`1.0x`），其余保留两位（`1.25x`）。
 *
 * 补 `1.0x` 而不是 `1x`：一列 `1x` / `1.25x` / `2x` 高矮不齐，补成小数位宽更整齐。
 */
internal fun formatPlaybackSpeed(speed: Float): String {
    val rounded = round(speed * 100f) / 100f
    return if (rounded == rounded.toInt().toFloat()) {
        "${rounded.toInt()}.0x"
    } else {
        "${rounded}x"
    }
}
