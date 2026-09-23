package io.github.javcinema.ui.screen

import io.github.javcinema.data.model.MagnetFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MagnetFilePolicyTest {

    /** 体积单位是 1024 进制（`parseSize()` 按 KB/MB/GB × 1024 解析），测试里保持同一口径。 */
    private fun mb(value: Double): Long = (value * 1024 * 1024).toLong()

    private fun gb(value: Double): Long = (value * 1024 * 1024 * 1024).toLong()

    private fun file(name: String, size: Long) = MagnetFile().apply {
        filename = name
        this.size = size
    }

    @Test
    fun movieCodeDetection() {
        assertTrue(looksLikeMovieCode("SSIS-001"))
        assertTrue(looksLikeMovieCode("ssis001"))
        assertFalse(looksLikeMovieCode("三上悠亜"))
        assertFalse(looksLikeMovieCode(""))
    }

    @Test
    fun movieCodeDetection_acceptsRealWorldFormats() {
        assertTrue(looksLikeMovieCode("IPX-001"))
        assertTrue(looksLikeMovieCode("ipx001"))
        assertTrue(looksLikeMovieCode("MIDE-1234"))
        assertTrue(looksLikeMovieCode("SSIS-001A"))
        assertTrue(looksLikeMovieCode("  SSIS-001  ")) // 前后空白应被 trim
    }

    @Test
    fun movieCodeDetection_rejectsNonCodeLookalikes() {
        // 回归：原规则 ^[A-Za-z]{2,8}-?\d{2,6}[A-Za-z]?$ 过宽，
        // 「ab123」这类两字母短串也会被当成番号，触发无意义的磁力搜索。
        assertFalse(looksLikeMovieCode("ab123"))
        assertFalse(looksLikeMovieCode("xy12"))
        assertFalse(looksLikeMovieCode("id1234"))
        assertFalse(looksLikeMovieCode("hello world"))
        assertFalse(looksLikeMovieCode("SSIS"))
        assertFalse(looksLikeMovieCode("001"))
        assertFalse(looksLikeMovieCode("SSIS-"))
        assertFalse(looksLikeMovieCode("SSIS-001-002"))
    }

    @Test
    fun adTitleIsDetected() {
        assertTrue(isAdText("最新地址 www.example.com"))
        assertFalse(isAdText("SSIS-001 高清"))
    }

    /**
     * 展示规则**只看体积**，不看扩展名也不看名字。
     *
     * 回归：这里原先要求「媒体扩展名」，于是 `.txt`/`.md` 被挡掉 —— 但同一套逻辑也会
     * 把 `movie.mp4.part1`（分卷下载，`endsWith(".mp4")` 匹配不上）、`.rar` 打包的种子
     * 整条挡掉。体积是更可靠的信号，扩展名白名单就不再需要了。
     */
    @Test
    fun keepsOnlyEntriesAtLeastTwentyMegabytes() {
        val files = listOf(
            file("官网.txt", mb(0.1)),
            file("readme.md", mb(0.1)),
            file("cover.jpg", mb(0.8)),
            file("movie.mp4", mb(6000.0)),
            file("sample.mp4", mb(200.0))
        )
        val visible = visibleMagnetFiles(files)
        assertEquals(listOf("movie.mp4", "sample.mp4"), visible.map { it.filename })
        assertEquals(0, largestVideoIndex(visible))
    }

    /**
     * 回归：站点水印写进**正片文件名**时，不能把正片当广告隐藏。
     *
     * `www.98t.la@SSIS-001.mp4` / `www.hhd800.com@xxx.mp4` 都是常见形态，
     * 而 AD_MARKERS 里含 `www.` → 修复前 visibleMagnetFiles 会返回空列表，
     * 界面上只剩「没有文件」，用户看到的却是「这个种子明明有资源」。
     *
     * 现在的规则是**广告标记一律不参与隐藏**，所以不管有没有干净候选，正片都在。
     */
    @Test
    fun watermarkedVideoIsNeverHidden() {
        val alone = listOf(file("www.98t.la@SSIS-001.mp4", gb(6.0)))
        assertEquals(listOf("www.98t.la@SSIS-001.mp4"), visibleMagnetFiles(alone).map { it.filename })

        val withClean = listOf(
            file("www.hhd800.com@SSIS-001.mp4", gb(6.0)),
            file("SSIS-001-1080p.mp4", gb(5.0))
        )
        assertEquals(
            listOf("www.hhd800.com@SSIS-001.mp4", "SSIS-001-1080p.mp4"),
            visibleMagnetFiles(withClean).map { it.filename }
        )
    }

    /**
     * 真实种子（模拟器上 SSIS-001「无极磁链」实测抓到的文件列表）。
     *
     * 两条广告是怎么绕过名字标记的：`x u u 6 2 . c o m.mp4` 把字符用空格拆开、
     * `社 區 最 新 情 報.mp4` 用繁体且不在词表里 —— 所以隐藏**不能看名字**。
     * 但体积骗不了人：两条广告各十几 MB，正片 6.46 GB，
     * 按「不足 20 MB」筛掉后列表里只剩正片，且加粗落在它身上。
     */
    @Test
    fun realWorldTorrentHidesTinyAdClips() {
        val files = listOf(
            file("SSIS-001-UC.mp4", gb(6.46)),
            file("x u u 6 2 . c o m.mp4", mb(12.09)),
            file("社 區 最 新 情 報.mp4", mb(14.39)),
            file("最 新 位 址 獲 取.txt", 136)
        )
        val visible = visibleMagnetFiles(files)
        assertEquals(listOf("SSIS-001-UC.mp4"), visible.map { it.filename })
        assertEquals(0, largestVideoIndex(visible))
    }

    /**
     * ⚠️ 兜底：整条种子本来就小的时候，不能因为「都不到 20 MB」就全部隐藏。
     *
     * 只要「筛」这个动作存在，就必须保证它不可能把整条种子筛没 ——
     * 否则又变回用户看到的「没有文件」，正是这个模块踩过的坑。
     */
    @Test
    fun smallTorrentIsShownWhenEverythingIsBelowTheThreshold() {
        val files = listOf(
            file("短片A.mp4", mb(14.0)),
            file("短片B.mp4", mb(12.0))
        )
        assertEquals(2, visibleMagnetFiles(files).size)
    }

    /** 同理：只有一个文件时再小也要展示，不能因为「太小」把唯一内容藏掉。 */
    @Test
    fun singleTinyFileIsNeverHidden() {
        val files = listOf(file("小片段.mp4", mb(8.0)))
        assertEquals(listOf("小片段.mp4"), visibleMagnetFiles(files).map { it.filename })
    }

    /**
     * 全部条目都不足 20 MB（含非媒体）时也要原样展示 —— 兜底优先于「看起来像垃圾」。
     * 只按体积筛就意味着不再区分媒体/非媒体，这是有意的。
     */
    @Test
    fun allTinyEntriesAreShownAsIs() {
        val files = listOf(
            file("最新地址.txt", mb(0.1)),
            file("www.example.com.url", mb(0.1))
        )
        assertEquals(2, visibleMagnetFiles(files).size)
        assertEquals(null, largestVideoIndex(files))
    }

    /**
     * 主文件判定只看体积，不看名字 —— 拿「名字不像广告」当偏好会挑错：
     * 水印正片被跳过，加粗落到十几 MB 的赠品片段上。
     */
    @Test
    fun largestVideoIndexPicksBySizeNotByName() {
        val files = listOf(
            file("www.98t.la@SSIS-001.mp4", gb(6.46)),
            file("视频2.mp4", mb(12.0))
        )
        assertEquals(0, largestVideoIndex(files))
    }

    /** 主文件仍只在视频里挑：打包的 `.rar` 比正片大时，不该抢走加粗。 */
    @Test
    fun largestVideoIndexIgnoresNonVideoFiles() {
        val files = listOf(
            file("movie.mp4", gb(6.0)),
            file("bonus.rar", gb(8.0))
        )
        assertEquals(0, largestVideoIndex(files))
    }
}
