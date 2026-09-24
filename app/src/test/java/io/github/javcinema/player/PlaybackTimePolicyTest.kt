package io.github.javcinema.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTimePolicyTest {

    @Test
    fun nonPositiveShowsZero() {
        // 时长还没拿到时 duration == 0；出错时可能给负数。两种都不能显示成 "-1:-1"。
        assertEquals("00:00", formatPlaybackTime(0L))
        assertEquals("00:00", formatPlaybackTime(-1L))
        assertEquals("00:00", formatPlaybackTime(Long.MIN_VALUE))
    }

    @Test
    fun underOneHourHasNoHourPart() {
        assertEquals("00:01", formatPlaybackTime(1_000L))
        assertEquals("05:30", formatPlaybackTime(330_000L))
        assertEquals("59:59", formatPlaybackTime(3_599_000L))
    }

    @Test
    fun subSecondIsTruncatedNotRounded() {
        // 999ms 还没到 1 秒，显示 00:01 会让人以为已经播了一秒。
        assertEquals("00:00", formatPlaybackTime(999L))
        assertEquals("00:01", formatPlaybackTime(1_999L))
    }

    @Test
    fun oneHourSwitchesToHourPart() {
        // ⚠️ 这条是加小时位的全部理由：125 分钟不能显示成 "125:00"。
        assertEquals("1:00:00", formatPlaybackTime(3_600_000L))
        assertEquals("1:00:01", formatPlaybackTime(3_601_000L))
        assertEquals("2:05:30", formatPlaybackTime(7_530_000L))
    }

    @Test
    fun hourPartIsNotZeroPadded() {
        // 补零会变成 "01:05:30"，多一个字符却没有信息量。
        assertEquals("9:00:00", formatPlaybackTime(32_400_000L))
    }

    @Test
    fun beyondOneDayStillCountsHours() {
        // 不折成「天」，否则 26 小时的直播录像会显示成 "2:00:00"，直接看错。
        assertEquals("26:00:00", formatPlaybackTime(93_600_000L))
    }
}
