package io.github.javcinema.ui.screen

/**
 * 主界面「按屏幕上下半区分配左右滑动」的手势策略。
 *
 * 规则：
 * - **上半屏**的左右滑动留给页面自己的顶部功能页 —— 影片（热门/全部/发行）、女优（女优/类别）、
 *   收藏（作品/女优）、磁力（三个源）都在内容区放了一个 `HorizontalPager`，滑动本来就归它。
 * - **下半屏**的左右滑动用来切换底部功能页（影片/女优/搜索/收藏/设置）。
 *
 * 之所以要把「下半屏」显式抢过来：顶部 `HorizontalPager` 铺满整个内容区（上下半屏都算），
 * 不抢的话下半屏的滑动会被它吃掉，变成「上半屏下半屏都在切顶部页」。
 *
 * 抽成纯函数是为了能单测 —— 手势代码在模拟器上很难稳定复现边界情况。
 */

/** 上下半区的分界比例。0.5 = 屏幕正中间。 */
internal const val SWIPE_ZONE_SPLIT = 0.5f

/** 触发翻页所需的拖动距离 = 屏幕宽度的这个比例。 */
internal const val SWIPE_TRIGGER_FRACTION = 0.18f

/** 屏幕很窄时的绝对下限，避免轻轻一划就翻页。 */
internal const val SWIPE_TRIGGER_MIN_DP = 56f

internal enum class SwipeZone { UPPER, LOWER }

internal enum class SwipeDirection { NONE, PREV, NEXT }

/** 按下点落在哪个半区。[height] 为 0 等退化情况一律算下半区（宁可抢，也别让手势悬空）。 */
internal fun swipeZoneOf(y: Float, height: Float, split: Float = SWIPE_ZONE_SPLIT): SwipeZone =
    if (height > 0f && y < height * split) SwipeZone.UPPER else SwipeZone.LOWER

/** 把「屏幕宽度的比例」与「绝对下限」取较大者，换算成像素。 */
internal fun swipeTriggerPx(
    widthPx: Float,
    density: Float,
    fraction: Float = SWIPE_TRIGGER_FRACTION,
    minDp: Float = SWIPE_TRIGGER_MIN_DP
): Float = maxOf(widthPx * fraction, minDp * density)

/**
 * 松手时按累计位移判定方向。
 *
 * 向左拖（负位移）= 看下一个（与 `HorizontalPager` 的方向一致），向右拖 = 看上一个。
 */
internal fun decideSwipe(dragX: Float, triggerPx: Float): SwipeDirection = when {
    triggerPx <= 0f -> SwipeDirection.NONE
    dragX <= -triggerPx -> SwipeDirection.NEXT
    dragX >= triggerPx -> SwipeDirection.PREV
    else -> SwipeDirection.NONE
}

/**
 * 目标下标；**不环绕** —— 已经在第一个还往前滑、或在最后一个还往后滑时返回 `null`（什么都不做）。
 */
internal fun swipeTargetIndex(current: Int, direction: SwipeDirection, count: Int): Int? {
    if (current !in 0 until count) return null
    return when (direction) {
        SwipeDirection.NONE -> null
        SwipeDirection.PREV -> if (current > 0) current - 1 else null
        SwipeDirection.NEXT -> if (current < count - 1) current + 1 else null
    }
}
