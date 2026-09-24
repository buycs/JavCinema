package io.github.javcinema.player

/**
 * 播放器手势的纯逻辑：双击快进/快退、长按快放、进度条拖拽的时间换算。
 *
 * 抽成纯函数是为了能单测 —— 这些阈值和换算在模拟器上很难稳定复现边界
 * （尤其是「两次点击隔多久才算双击」）。
 */

/** 双击快进 / 快退的步长。 */
internal const val DOUBLE_TAP_STEP_MS = 10_000L

/**
 * 两次抬起间隔小于它就当成双击。
 *
 * 取值对齐 Compose 的 `ViewConfiguration.doubleTapTimeoutMillis`（300ms）——
 * 系统判定双击用的也是这个数，不一致会出现「系统算双击、我们算单击」的错位。
 */
internal const val DOUBLE_TAP_TIMEOUT_MS = 300L

/** 按住不动超过它就进入长按快放。同样对齐系统的 `longPressTimeoutMillis`。 */
internal const val LONG_PRESS_TIMEOUT_MS = 500L

/** 长按期间的临时倍速。松手要还回用户自己选的倍速，不能留在 3x。 */
internal const val LONG_PRESS_SPEED = 3.0f

/**
 * 双击快进/快退浮层的停留时长。
 *
 * 取 1 秒：短到不挡画面，长到连续双击时浮层不会闪断 —— 每次双击都会重置计时
 * （靠 `seekFlashTick` 这个重放计数，见 `SimpleVideoPlayer.seekFlashTick`）。
 */
internal const val SEEK_FLASH_DURATION_MS = 1_000L

/**
 * 双击生效的左右边缘比例：左右各 40%，**中间 20% 不响应双击**。
 *
 * 留出中间带是为了不和「单击切换控件」抢：屏幕正中是用户最容易随手点的地方，
 * 那里做双击快进很容易误触。
 */
internal const val DOUBLE_TAP_EDGE_FRACTION = 0.4f

/**
 * 双击位置 → 快进/快退的毫秒数。
 *
 * 正数 = 快进，负数 = 快退，**0 = 不处理**（落在中间带，或宽度还没测出来）。
 */
internal fun doubleTapSeekDelta(
    x: Float,
    width: Float,
    stepMs: Long = DOUBLE_TAP_STEP_MS
): Long = when {
    width <= 0f -> 0L
    x < width * DOUBLE_TAP_EDGE_FRACTION -> -stepMs
    x > width * (1f - DOUBLE_TAP_EDGE_FRACTION) -> stepMs
    else -> 0L
}

/** 两次抬起的时间差是否够成双击。负值（时钟回拨等）一律不算。 */
internal fun isDoubleTap(gapMs: Long): Boolean = gapMs in 0..DOUBLE_TAP_TIMEOUT_MS

/** 按住时长是否够成长按。 */
internal fun isLongPress(holdMs: Long): Boolean = holdMs >= LONG_PRESS_TIMEOUT_MS

/**
 * 长按快放该不该启动。
 *
 * ⚠️ [isScrubbing] 这一条**必须挡**：拖动进度条时手指本来就按着不动，
 * 拖到一半停顿 0.5s 就会被当成「长按快放」，实际播放速度被切到 3x
 * （实测复现：按住进度条 0.6s，顶部弹出「快放中 3.0x」）。
 * 长按快放是给「盯着画面快速掠过」用的，和拖进度条是两回事。
 *
 * 其余两条来自「计时器和手指状态之间有竞态」：计时到点时手指可能已经抬起、
 * 或者已经进入了某个手势模式，所以调用方必须在到点后**再自查一遍**。
 */
internal fun shouldStartLongPressSpeed(
    isLocked: Boolean,
    isTouching: Boolean,
    isScrubbing: Boolean,
    gestureMode: GestureMode
): Boolean = !isLocked && isTouching && !isScrubbing && gestureMode == GestureMode.NONE

/**
 * 把目标位置夹到合法区间。
 *
 * 时长未知（直播 / 还没拿到 metadata）时只保证非负 —— 此时**不能**夹成 0，
 * 否则双击快进会把进度直接拉回开头。
 */
internal fun clampSeekPosition(targetMs: Long, durationMs: Long): Long =
    if (durationMs > 0L) targetMs.coerceIn(0L, durationMs) else targetMs.coerceAtLeast(0L)

/**
 * 进度条上触摸点 → 目标时间。用于「点哪跳哪」和拖拽。
 *
 * 时长未知时返回 0（没有可跳的坐标系）；`width` 为 0 等退化情况也返回 0。
 */
internal fun scrubPositionFor(x: Float, width: Float, durationMs: Long): Long {
    if (width <= 0f || durationMs <= 0L) return 0L
    val fraction = (x / width).coerceIn(0f, 1f)
    return (durationMs * fraction).toLong().coerceIn(0L, durationMs)
}

/** 双击浮层的文案，例如 `+10秒` / `-10秒`。 */
internal fun formatSeekFlash(deltaMs: Long): String {
    val seconds = deltaMs / 1000
    return if (seconds >= 0L) "+${seconds}秒" else "${seconds}秒"
}
