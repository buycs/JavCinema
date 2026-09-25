package io.github.javcinema.ui.screen

import kotlin.math.abs

/**
 * 主界面「按屏幕上下半区分配左右滑动」的手势策略。
 *
 * 规则：
 * - **上半屏**的左右滑动留给页面自己的顶部功能页 —— 影片（热门/全部/发行）、女优（女优/类别）、
 *   收藏（作品/女优）都在内容区放了一个 `HorizontalPager`，滑动本来就归它。
 * - **下半屏**的左右滑动用来切换底部功能页（影片/女优/搜索/收藏/设置）。
 *
 * 之所以要把「下半屏」显式抢过来：顶部 `HorizontalPager` 铺满整个内容区（上下半屏都算），
 * 不抢的话下半屏的滑动会被它吃掉，变成「上半屏下半屏都在切顶部页」。
 *
 * ⚠️ **页面自己没有顶部功能页时（搜索 / 设置），上半屏就什么都不触发** —— 这是有意的，
 * 不是漏登记。那里本来就没有可切的东西，让上半屏「退化成切底栏」等于凭空多出一个
 * 用户没预期的手势区。所以这里**不按路由做任何特判**，只按坐标分区：
 * 放行给一个不存在的 pager，结果自然就是没反应。
 * （曾经为搜索 / 设置加过「上半屏退化成下半屏」的逻辑，已按用户要求撤掉，别再改回去。）
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

/**
 * 上半屏放行之后，这次拖动**该不该被兜底消费掉**。
 *
 * 背景：上半屏交给页面自己的顶部 `HorizontalPager`（见 `swipeZoneOf`）。但搜索 / 设置页
 * **没有**顶部功能页，`MainScreen` 又主动放行，于是这次横向拖动**没有任何接收者**。
 * 而 Compose 的 `clickable` / `combinedClickable` 只判「DOWN 落在节点内」+「UP 时未超长按阈值」，
 * **全程不看移动距离** —— 拖动就这么漏到了「按下点所在的那一行」的点击上（实测复现）。
 *
 * 修法是让 `MainScreen` 在上半屏的 `PointerEventPass.Final` 阶段把这类拖动**吃掉但不做任何事**：
 * 行的 `clickable` 在自己的 Final 检查里看到 `isConsumed`，就会取消这次点击。
 *
 * ⚠️ **判据必须与下半屏翻页一致**（横向越 slop 且明显大于纵向），不能「一有位移就消费」：
 * 否则「按下时手指抖 2px」的正常点击也会被吃掉，变成整片区域点不动。
 *
 * ⚠️ **只在 Final 阶段用这个判据。** Initial 阶段消费会把顶部 pager 的翻页一起吞掉。
 *
 * @param dragX 累计横向位移（带符号）。
 * @param dragY 累计纵向位移（带符号，只比较绝对值）。
 * @param slop 触摸阈值，通常取 `viewConfiguration.touchSlop`。
 */
internal fun shouldSwallowUpperHalfDrag(dragX: Float, dragY: Float, slop: Float): Boolean =
    slop > 0f && abs(dragX) > slop && abs(dragX) > abs(dragY)
