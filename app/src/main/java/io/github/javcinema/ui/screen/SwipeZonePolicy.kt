package io.github.javcinema.ui.screen

import io.github.javcinema.ui.navigation.NavRoutes

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
 * **例外**：页面自己**没有**顶部功能页时（搜索 / 设置），上半屏没有 pager 可切，
 * 放行等于划不动 —— 所以上半屏退化成下半屏，整屏都能切底栏。见 [effectiveSwipeZone]。
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

/**
 * 底部功能页里**没有**顶部功能页的那些路由。
 *
 * 有顶部功能页的（影片 / 女优 / 收藏）不在此列 —— 它们上半屏的滑动归自己的 `HorizontalPager`。
 * 其余路由（影片详情、影片列表、磁力、女优详情等子页）也**不在此列**：保持「放行给子级」的
 * 原有行为，不抢手势。
 *
 * ⚠️ **新增没有顶部功能页的底部功能页时，必须把它的路由加到这里。**
 * 否则上半屏的滑动会被放行给一个并不存在的 pager —— 表现为「上半屏划不动」，
 * 用户只会觉得手势坏了，而不会想到是漏登记。这与 `NavRoutes.FULLSCREEN_ROUTES`
 * （新增沉浸式页面必须登记）是同一种约定。
 */
private val BOTTOM_PAGES_WITHOUT_TOP_PAGES = listOf(NavRoutes.SEARCH, NavRoutes.SETTINGS)

/** 按下点落在哪个半区。[height] 为 0 等退化情况一律算下半区（宁可抢，也别让手势悬空）。 */
internal fun swipeZoneOf(y: Float, height: Float, split: Float = SWIPE_ZONE_SPLIT): SwipeZone =
    if (height > 0f && y < height * split) SwipeZone.UPPER else SwipeZone.LOWER

/**
 * 当前路由的页面是否**自带**顶部功能页（即内容区里那个 `HorizontalPager`）。
 *
 * 只有底部功能页里的搜索 / 设置没有（见 [BOTTOM_PAGES_WITHOUT_TOP_PAGES]）；
 * 其余一律返回 `true` —— 拿不准时选择「不抢手势」，因为抢错的后果（顶部页翻不动）
 * 比不抢（上半屏划不动）更严重，而且详情 / 列表这类子页本来就是放行给子级的。
 */
internal fun hasTopPages(route: String?): Boolean {
    val value = route ?: return true
    return BOTTOM_PAGES_WITHOUT_TOP_PAGES.none { value.startsWith(it) }
}

/**
 * 实际生效的滑动分区 —— [swipeZoneOf] 再加一条「页面没有顶部功能页」的例外。
 *
 * 搜索 / 设置页没有顶部功能页，上半屏放行等于没有接收者，用户怎么划都没反应。
 * 这种情况让上半屏退化成下半屏：整屏都是「切底栏」。
 */
internal fun effectiveSwipeZone(
    y: Float,
    height: Float,
    hasTopPages: Boolean,
    split: Float = SWIPE_ZONE_SPLIT
): SwipeZone = if (hasTopPages) swipeZoneOf(y, height, split) else SwipeZone.LOWER

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
