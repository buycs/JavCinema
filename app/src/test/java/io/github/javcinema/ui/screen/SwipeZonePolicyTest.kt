package io.github.javcinema.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    // ---------- 没有顶部功能页的页面：上半屏什么都不触发 ----------
    // 搜索 / 设置页没有顶部功能页，上半屏的滑动**必须**保持 UPPER（放行给页面），
    // 而不是退化成 LOWER（切底栏）。曾经为这两个页面加过退化特判，已按用户要求撤掉：
    // 那里本来就没有可切的东西，退化等于凭空多出一个用户没预期的手势区。
    // 所以 `SwipeZonePolicy` 现在**不按路由做任何特判**，只按坐标分区。

    /**
     * ⚠️ 回归守卫：搜索 / 设置这类「没有顶部功能页」的页面，上半屏仍是 UPPER ——
     * 放行给子级后没有接收者，结果就是**什么都不触发**，这正是要的。
     * 一旦被改成 LOWER，这两个页面上下半屏都会切底栏，用户就会觉得手势乱跑。
     */
    @Test
    fun pagesWithoutTopPagesLeaveTheirUpperHalfUnhandled() {
        assertEquals(SwipeZone.UPPER, swipeZoneOf(y = 0f, height = height))
        assertEquals(SwipeZone.UPPER, swipeZoneOf(y = 600f, height = height))
        // 下半屏照旧归底栏 —— 与页面有没有顶部功能页无关
        assertEquals(SwipeZone.LOWER, swipeZoneOf(y = 1800f, height = height))
    }
}
