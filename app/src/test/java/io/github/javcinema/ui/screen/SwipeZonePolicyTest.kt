package io.github.javcinema.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeZonePolicyTest {

    // 1080x2400 / 420dpi 的模拟器：density = 2.625
    private val density = 2.625f
    private val width = 1080f
    private val height = 2400f

    /** 按下点在屏幕上半部分 → 归顶部功能页。 */
    @Test
    fun upperHalfBelongsToTopTabs() {
        assertEquals(SwipeZone.UPPER, swipeZoneOf(y = 0f, height = height))
        assertEquals(SwipeZone.UPPER, swipeZoneOf(y = 1199f, height = height))
    }

    /** 正好在中线上算下半部分，保证上下半区无缝且不重叠。 */
    @Test
    fun exactMidpointBelongsToLowerHalf() {
        assertEquals(SwipeZone.LOWER, swipeZoneOf(y = 1200f, height = height))
        assertEquals(SwipeZone.LOWER, swipeZoneOf(y = 2399f, height = height))
    }

    /** 高度还没量出来时按下半部分处理，避免手势被静默吞掉。 */
    @Test
    fun unknownHeightFallsBackToLowerHalf() {
        assertEquals(SwipeZone.LOWER, swipeZoneOf(y = 10f, height = 0f))
    }

    @Test
    fun splitIsConfigurable() {
        assertEquals(SwipeZone.UPPER, swipeZoneOf(y = 100f, height = 1000f, split = 0.3f))
        assertEquals(SwipeZone.LOWER, swipeZoneOf(y = 400f, height = 1000f, split = 0.3f))
    }

    /** 宽屏（1080px）下比例值更大 → 用比例。 */
    @Test
    fun wideScreenUsesFraction() {
        // 0.18 * 1080 = 194.4 > 56 * 2.625 = 147
        assertEquals(194.4f, swipeTriggerPx(widthPx = width, density = density), 0.01f)
    }

    /** 窄屏（720px）下比例值不足下限 → 用下限，避免一划就翻页。 */
    @Test
    fun narrowScreenUsesMinimum() {
        // 0.18 * 720 = 129.6 < 147
        assertEquals(147f, swipeTriggerPx(widthPx = 720f, density = density), 0.01f)
    }

    @Test
    fun lowDensityScreenUsesFraction() {
        // 0.18 * 1440 = 259.2 > 56 * 1.0 = 56
        assertEquals(259.2f, swipeTriggerPx(widthPx = 1440f, density = 1f), 0.01f)
    }

    /** 向左拖够距离 → 下一个（与 HorizontalPager 方向一致）。 */
    @Test
    fun dragLeftGoesToNext() {
        val trigger = swipeTriggerPx(widthPx = width, density = density)
        assertEquals(SwipeDirection.NEXT, decideSwipe(dragX = -300f, triggerPx = trigger))
    }

    /** 向右拖够距离 → 上一个。 */
    @Test
    fun dragRightGoesToPrevious() {
        val trigger = swipeTriggerPx(widthPx = width, density = density)
        assertEquals(SwipeDirection.PREV, decideSwipe(dragX = 300f, triggerPx = trigger))
    }

    /** 没拖够 → 不翻页（此时页面只是被摸了一下）。 */
    @Test
    fun shortDragDoesNothing() {
        val trigger = swipeTriggerPx(widthPx = width, density = density)
        assertEquals(SwipeDirection.NONE, decideSwipe(dragX = -50f, triggerPx = trigger))
        assertEquals(SwipeDirection.NONE, decideSwipe(dragX = 50f, triggerPx = trigger))
        assertEquals(SwipeDirection.NONE, decideSwipe(dragX = 0f, triggerPx = trigger))
    }

    /** 正好等于阈值就算翻页（边界不要出现「差一点」的死区）。 */
    @Test
    fun exactThresholdCounts() {
        val trigger = swipeTriggerPx(widthPx = width, density = density)
        assertEquals(SwipeDirection.NEXT, decideSwipe(dragX = -trigger, triggerPx = trigger))
        assertEquals(SwipeDirection.PREV, decideSwipe(dragX = trigger, triggerPx = trigger))
    }

    /** 阈值退化成 0（尺寸还没量出来）时不翻页，避免乱跳。 */
    @Test
    fun nonPositiveTriggerNeverSwipes() {
        assertEquals(SwipeDirection.NONE, decideSwipe(dragX = -500f, triggerPx = 0f))
        assertEquals(SwipeDirection.NONE, decideSwipe(dragX = 500f, triggerPx = -1f))
    }

    @Test
    fun targetIndexMovesWithinRange() {
        assertEquals(3, swipeTargetIndex(current = 2, direction = SwipeDirection.NEXT, count = 5))
        assertEquals(1, swipeTargetIndex(current = 2, direction = SwipeDirection.PREV, count = 5))
        assertEquals(1, swipeTargetIndex(current = 0, direction = SwipeDirection.NEXT, count = 5))
        assertEquals(3, swipeTargetIndex(current = 4, direction = SwipeDirection.PREV, count = 5))
    }

    /** 不环绕：第一个再往前、最后一个再往后都什么都不做。 */
    @Test
    fun targetIndexDoesNotWrapAround() {
        assertNull(swipeTargetIndex(current = 0, direction = SwipeDirection.PREV, count = 5))
        assertNull(swipeTargetIndex(current = 4, direction = SwipeDirection.NEXT, count = 5))
    }

    @Test
    fun targetIndexIgnoresNoopAndInvalidInput() {
        assertNull(swipeTargetIndex(current = 2, direction = SwipeDirection.NONE, count = 5))
        assertNull(swipeTargetIndex(current = 5, direction = SwipeDirection.NEXT, count = 5))
        assertNull(swipeTargetIndex(current = -1, direction = SwipeDirection.PREV, count = 5))
        assertNull(swipeTargetIndex(current = 0, direction = SwipeDirection.NEXT, count = 0))
    }

    // ---------- 没有顶部功能页的页面：上半屏退化成切底栏 ----------

    /**
     * 搜索页两种路由写法（裸名 `"search"` 与导航图注册的 `"search?query={query}"`）
     * 都必须被认出来 —— 少认一个，搜索页的上半屏就划不动。
     */
    @Test
    fun searchPageHasNoTopPages() {
        assertFalse(hasTopPages("search"))
        assertFalse(hasTopPages("search?query={query}"))
    }

    @Test
    fun settingsPageHasNoTopPages() {
        assertFalse(hasTopPages("settings"))
    }

    /** 影片 / 女优 / 收藏都有顶部功能页，上半屏必须继续留给它们。 */
    @Test
    fun pagerPagesKeepTheirTopPages() {
        assertTrue(hasTopPages("home"))
        assertTrue(hasTopPages("actresses"))
        assertTrue(hasTopPages("favourite"))
    }

    /** 子页（详情 / 影片列表 / 磁力）保持「放行给子级」的原行为，不抢手势。 */
    @Test
    fun subPagesKeepPassingThrough() {
        assertTrue(hasTopPages("movie_detail/{movieCode}?link={link}&coverUrl={coverUrl}"))
        assertTrue(hasTopPages("movie_list/{title}/{url}"))
        assertTrue(hasTopPages("actress_detail/{starId}?name={name}&imageUrl={imageUrl}"))
        assertTrue(hasTopPages("download/{keyword}"))
    }

    /** 拿不准时（导航图未就绪 / 空路由）选择不抢 —— 抢错的后果比不抢更严重。 */
    @Test
    fun unknownRouteKeepsPassingThrough() {
        assertTrue(hasTopPages(null))
        assertTrue(hasTopPages(""))
        assertTrue(hasTopPages("something_new"))
    }

    /** 没有顶部功能页时，上半屏也归切底栏 —— 否则上半屏划不动，用户会以为手势坏了。 */
    @Test
    fun upperHalfDegradesToBottomSwipeWhenNoTopPages() {
        assertEquals(SwipeZone.LOWER, effectiveSwipeZone(y = 0f, height = height, hasTopPages = false))
        assertEquals(SwipeZone.LOWER, effectiveSwipeZone(y = 1199f, height = height, hasTopPages = false))
        assertEquals(SwipeZone.LOWER, effectiveSwipeZone(y = 1200f, height = height, hasTopPages = false))
    }

    /** 有顶部功能页时行为不变：上半屏仍然归顶部 pager。 */
    @Test
    fun upperHalfStillBelongsToTopPagerWhenItHasOne() {
        assertEquals(SwipeZone.UPPER, effectiveSwipeZone(y = 0f, height = height, hasTopPages = true))
        assertEquals(SwipeZone.UPPER, effectiveSwipeZone(y = 1199f, height = height, hasTopPages = true))
        assertEquals(SwipeZone.LOWER, effectiveSwipeZone(y = 1200f, height = height, hasTopPages = true))
    }

    /** 下半屏两种情况下都是切底栏，不受这个开关影响。 */
    @Test
    fun lowerHalfIsAlwaysBottomSwipe() {
        assertEquals(SwipeZone.LOWER, effectiveSwipeZone(y = 2399f, height = height, hasTopPages = true))
        assertEquals(SwipeZone.LOWER, effectiveSwipeZone(y = 2399f, height = height, hasTopPages = false))
    }
}
