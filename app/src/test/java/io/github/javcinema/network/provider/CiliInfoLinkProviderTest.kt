package io.github.javcinema.network.provider

import io.github.javcinema.ui.screen.largestVideoIndex
import io.github.javcinema.ui.screen.visibleMagnetFiles
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CiliInfoLinkProviderTest {

    /** 取自 cili.info 真实详情页（ROE-556 的 6.15 GB 条目），保留了 Cloudflare 邮件保护结构。 */
    private val detailHtml = """
        <table class="table table-hover file-list">
          <thead><tr><th>文件 ( 5 )</th><th class="th-size">大小</th></tr></thead>
          <tbody>
            <tr><td>roe-556/2 0 2 6 英 超 官 方 指 定 网 站.url</td><td class="td-size">175 B</td></tr>
            <tr><td>roe-556/<a href="/cdn-cgi/l/email-protection" class="__cf_email__" data-cfemail="c5f1aef7eba8a085b7aaa0e8f0f0f3eba8b5f1">[email&#160;protected]</a></td><td class="td-size">6.13 GB</td></tr>
            <tr><td>roe-556/●無料で楽しめる！最新アダルト動画サイト.url</td><td class="td-size">176 B</td></tr>
            <tr><td>roe-556/三 上 悠 亚 想 要 跟 你 决 胜 负.mp4</td><td class="td-size">19.25 MB</td></tr>
            <tr><td>roe-556/全 网 最 劲 体 育 电 竞 直 播 平台.url</td><td class="td-size">175 B</td></tr>
          </tbody>
        </table>
    """.trimIndent()

    private fun parse() = runBlocking { CiliInfoLinkProvider().parseFiles(detailHtml) }

    @Test
    fun restoresCloudflareObfuscatedFilename() {
        val files = parse()
        assertEquals(5, files.size)
        assertTrue(
            "被 Cloudflare 改写的文件名应还原出扩展名",
            files.any { it.filename == "roe-556/4k2.me@roe-556.mp4" }
        )
        assertFalse(
            "不应残留占位文本",
            files.any { it.filename.contains("[email") }
        )
    }

    @Test
    fun parsesSizeOfRestoredFile() {
        val file = parse().first { it.filename.endsWith("4k2.me@roe-556.mp4") }
        // 6.13 GB
        assertTrue("体积应约 6.13 GB，实际 ${file.size}", file.size > 6_000_000_000L)
    }

    /**
     * 修复前：占位文件名没有媒体扩展名 → 被 visibleMagnetFiles() 过滤掉，
     * 6.13 GB 的正片在界面上完全看不到，只剩 19 MB 的赠品片段被当成主文件。
     */
    @Test
    fun largestRealVideoSurvivesFilteringAndBecomesMainFile() {
        val visible = visibleMagnetFiles(parse())
        assertEquals(2, visible.size)
        assertTrue(visible.none { it.filename.endsWith(".url") })
        val main = largestVideoIndex(visible)
        assertEquals("roe-556/4k2.me@roe-556.mp4", visible[main!!].filename)
    }
}
