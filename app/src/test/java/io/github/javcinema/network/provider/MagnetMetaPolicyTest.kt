package io.github.javcinema.network.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MagnetMetaPolicyTest {

    @Test
    fun parseMagnetMeta_splitsJammedSizeAndDate() {
        // 无极搜索结果行的真实形态：体积和日期被塞在同一个 meta 单元格里，
        // 对整格取 .text() 得到的就是这样一串粘连文本。
        val meta = parseMagnetMeta("2.02GB 2025-10-28")
        assertEquals("2.02 GB", meta.size)
        assertEquals("2025-10-28", meta.date)
    }

    @Test
    fun parseMagnetMeta_handlesThreeColumnLayout() {
        // 旧结构（3 列）拼起来后是同一段文本，所以两种结构共用一条解析路径
        val meta = parseMagnetMeta("7.42 GB 2026-09-19")
        assertEquals("7.42 GB", meta.size)
        assertEquals("2026-09-19", meta.date)
    }

    @Test
    fun parseMagnetMeta_sizeNeverSwallowsDate() {
        val meta = parseMagnetMeta("2.02GB 2025-10-28")
        assertFalse("体积里不应混入日期", meta.size.contains("-"))
        assertFalse(meta.size.contains("2025"))
    }

    @Test
    fun normalizeSize_unifiesFormatWithJsonApiSources() {
        // 另外两个源（BTSO / BTSEARCH）走 JSON API，体积一律是 "%.2f 单位"。
        // 无极这边是裸文本 "7.42GB"，必须归一成同一种写法。
        assertEquals("2.02 GB", normalizeSize("2.02GB"))
        assertEquals("3.30 GB", normalizeSize("3.3GB"))
        assertEquals("1.16 GB", normalizeSize("1.16 GB"))
        assertEquals("10.55 MB", normalizeSize("10.55MB"))
        assertEquals("42.00 B", normalizeSize("42 B"))
    }

    @Test
    fun normalizeSize_emptyWhenAbsent() {
        assertEquals("", normalizeSize(null))
        assertEquals("", normalizeSize(""))
        assertEquals("", normalizeSize("2025-10-28"))
    }

    @Test
    fun normalizeDate_truncatesTimeComponent() {
        // 详情页的「发布日期」带时分秒，列表页不带 —— 截成同一种格式
        assertEquals("2025-10-29", normalizeDate("2025-10-29 04:30:14"))
        assertEquals("2025-10-28", normalizeDate("2025-10-28"))
    }

    @Test
    fun normalizeDate_emptyWhenAbsent() {
        assertEquals("", normalizeDate(null))
        assertEquals("", normalizeDate("2.02 GB"))
    }

    // ------------------------------------------------------------------
    // 真实 HTML 回归：下面的表格结构直接取自 cili.info 的搜索结果页。
    //
    // 关键点是**每行只有 2 个 <td>**（标题 + 一个 meta 单元格），
    // 修复前的写法（size = cells[1]、date = cells[2]）会得到
    // size = "2.02GB 2025-10-28"、date = ""，在界面上表现为
    // 无极那一栏的副标题和另外两个源长得不一样。
    // ------------------------------------------------------------------

    private val searchHtml = """
        <table class="table table-hover file-list"><tbody>
        <tr>
            <td class="result-title"><a href="/!lTFZ">第一會所新片@SIS001@<mark>SSIS</mark>-518-U</a></td>
            <td class="result-meta"><div>7.42GB</div><div class="result-date">2026-09-19</div></td>
        </tr>
        <tr>
            <td class="result-title"><a href="/!ko2s"><mark>SSIS</mark>-<mark>001</mark></a></td>
            <td class="result-meta"><div>2.02GB</div><div class="result-date">2025-10-28</div></td>
        </tr>
        </tbody></table>
    """.trimIndent()

    @Test
    fun parseSearchResults_splitsSizeAndDateFromMetaCell() {
        val items = CiliInfoLinkProvider().parseSearchResults(searchHtml)
        assertEquals(2, items.size)

        val first = items[0]
        assertEquals("第一會所新片@SIS001@SSIS-518-U", first.title)
        assertEquals("7.42 GB", first.size)
        assertEquals("2026-09-19", first.date)
        assertEquals("/!lTFZ", first.link)

        val second = items[1]
        assertEquals("SSIS-001", second.title)
        assertEquals("2.02 GB", second.size)
        assertEquals("2025-10-28", second.date)
    }

    @Test
    fun parseSearchResults_markTagsDoNotInsertSpaces() {
        // <mark> 是行内元素，Jsoup 的 text() 不会在它周围塞空格。
        // 这条用例把这个假设钉住 —— 一旦升级 Jsoup 导致标题出现 "SSIS - 001" 就能立刻发现。
        val titles = CiliInfoLinkProvider().parseSearchResults(searchHtml).map { it.title }
        assertTrue(titles.contains("SSIS-001"))
        assertTrue(titles.none { it?.contains(" -") == true })
    }

    @Test
    fun parseSearchResults_rowsWithoutAnchorAreSkipped() {
        val html = """
            <table class="table table-hover file-list"><tbody>
            <tr><td>没有链接</td><td class="result-meta"><div>1 GB</div></td></tr>
            <tr><td class="result-title"><a href="/!ok">SSIS-002</a></td>
                <td class="result-meta"><div>1.5GB</div><div class="result-date">2024-01-02</div></td></tr>
            </tbody></table>
        """.trimIndent()
        val items = CiliInfoLinkProvider().parseSearchResults(html)
        assertEquals(1, items.size)
        assertEquals("SSIS-002", items[0].title)
        assertEquals("1.50 GB", items[0].size)
        assertEquals("2024-01-02", items[0].date)
    }
}
