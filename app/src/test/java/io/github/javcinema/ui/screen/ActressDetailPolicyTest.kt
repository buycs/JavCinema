package io.github.javcinema.ui.screen

import com.google.gson.JsonParser
import io.github.javcinema.data.model.Actress
import io.github.javcinema.data.model.AvmooStar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActressDetailPolicyTest {

    @Test
    fun starDisplayNamePrefersPrimaryThenJa() {
        val star = AvmooStar(
            starId = "kwbydan",
            starDmmId = null,
            starName = null,
            starName_ja = "名前",
            starName_en = "Name",
            starName_cn = "中文",
            starName_tw = null,
            avatar = null,
            avatarUrl = null,
            movieCount = 12,
            weight = null,
            birthday = "1990-01-01",
            size = null
        )
        assertEquals("名前", starDisplayName(star))
    }

    // ------------------------------------------------------------------
    // size 字段的形状不固定：既有对象也有普通字符串。
    //
    // 回归背景：早先的实现只判断「是不是字符串」，其余一律 toString()，
    // 于是女优详情页顶部把原始 JSON 连大括号一起显示了出来 ——
    // {"T":"163","B":"88","C":"D","W":"59","H":"85"}
    // ------------------------------------------------------------------

    @Test
    fun parseActressSize_readsJsonObject() {
        // 取自真实接口返回：T 身高 / B 胸围 / C 罩杯 / W 腰围 / H 臀围
        val size = parseActressSize(
            JsonParser.parseString("""{"T":"163","B":"88","C":"D","W":"59","H":"85"}""")
        )
        requireNotNull(size)
        assertEquals("163", size.height)
        assertEquals("88", size.bust)
        assertEquals("D", size.cup)
        assertEquals("59", size.waist)
        assertEquals("85", size.hip)
        // 罩杯跟在胸围后面，胸/腰/臀三项分别展示
        assertEquals("88(D)", size.bustDisplay)
        assertFalse(size.isEmpty)
    }

    @Test
    fun parseActressSize_neverLeaksBraces() {
        // 这条断言就是本次 bug 的判据：修好之前，界面上会看到 {...} 这样的原始 JSON。
        val size = parseActressSize(
            JsonParser.parseString("""{"T":"163","B":"88","C":"D","W":"59","H":"85"}""")
        )
        val shown = listOfNotNull(size?.height, size?.bustDisplay, size?.waist, size?.hip, size?.raw)
        assertTrue("不应出现 JSON 大括号：$shown", shown.none { it.contains("{") || it.contains("}") })
        assertTrue("不应出现 JSON 引号：$shown", shown.none { it.contains("\"") })
    }

    @Test
    fun parseActressSize_partialFields() {
        // 缺罩杯时胸围后面不加括号
        val noCup = parseActressSize(JsonParser.parseString("""{"T":"160","B":"83","W":"57","H":"86"}"""))
        requireNotNull(noCup)
        assertEquals("83", noCup.bustDisplay)
        assertNull(noCup.cup)

        // 只有身高时，胸/腰/臀三项都不该产生
        val heightOnly = parseActressSize(JsonParser.parseString("""{"T":"160"}"""))
        requireNotNull(heightOnly)
        assertNull(heightOnly.bustDisplay)
        assertNull(heightOnly.waist)
        assertNull(heightOnly.hip)
        assertEquals("160", heightOnly.height)
    }

    @Test
    fun parseActressSize_readsPrimitiveAsRaw() {
        val size = parseActressSize(JsonParser.parseString("\"T170 B88\""))
        requireNotNull(size)
        assertEquals("T170 B88", size.raw)
        assertNull(size.height)
        assertNull(size.bustDisplay)
    }

    @Test
    fun parseActressSize_nullAndUnrecognized() {
        assertNull(parseActressSize(null))
        assertNull(parseActressSize(JsonParser.parseString("null")))
        assertNull(parseActressSize(JsonParser.parseString("\"\"")))
        // 未知键 / 数组：宁可什么都不显示，也不把 JSON 丢到界面上
        assertNull(parseActressSize(JsonParser.parseString("""{"X":"1"}""")))
        assertNull(parseActressSize(JsonParser.parseString("[1,2,3]")))
    }

    @Test
    fun actressSize_bustDisplayPutsCupInParentheses() {
        assertEquals("88(D)", ActressSize(bust = "88", cup = "D").bustDisplay)
        assertEquals("88", ActressSize(bust = "88").bustDisplay)
        assertEquals("D", ActressSize(cup = "D").bustDisplay)
        assertNull(ActressSize().bustDisplay)
        assertTrue(ActressSize().isEmpty)
    }

    // ------------------------------------------------------------------
    // 顶部信息行的组装。
    //
    // 回归背景：接口对「没有值」的表达不统一 —— hometown / hobby 给 null，
    // 而 birthday / bloodType 给的是空串（实测 60 条里 38 条生日是 ''）。
    // 只要有一处没挡住，界面上就会出现「生日：」这种后面空着的行。
    // ------------------------------------------------------------------

    private fun actress(movieCount: Int? = null) = Actress().apply {
        name = "波多野結衣"
        this.movieCount = movieCount
    }

    @Test
    fun cleanValue_treatsBlankAndNullLiteralAsMissing() {
        assertNull(null.cleanValue())
        assertNull("".cleanValue())
        assertNull("   ".cleanValue())
        assertNull("null".cleanValue())
        assertNull("NULL".cleanValue())
        assertEquals("A", "  A  ".cleanValue())
    }

    @Test
    fun buildActressInfoRows_fullProfile() {
        // 取自真实接口返回（波多野結衣那条）。
        val profile = ActressProfile(
            actress = actress(movieCount = 4617),
            birthday = "1988-05-24",
            size = parseActressSize(
                JsonParser.parseString("""{"T":"163","B":"88","C":"D","W":"59","H":"85"}""")
            ),
            lastReleaseDate = "2026-09-19",
            hometown = "京都府",
            hobby = "ゲーム",
            bloodType = "A"
        )
        assertEquals(
            listOf(
                "作品数" to "4617 部",
                "生日" to "1988-05-24",
                "身高" to "163cm",
                "胸" to "88(D)",
                "腰" to "59",
                "臀" to "85",
                "最近作品" to "2026-09-19",
                "出生地" to "京都府",
                "兴趣" to "ゲーム",
                "血型" to "A"
            ),
            buildActressInfoRows(profile)
        )
    }

    @Test
    fun buildActressInfoRows_dropsMissingFieldsEntirely() {
        // 只有「作品数」有值，其余全是空串 / null —— 不该多出任何一行。
        val profile = ActressProfile(
            actress = actress(movieCount = 248),
            birthday = "",
            size = null,
            lastReleaseDate = null,
            hometown = null,
            hobby = null,
            bloodType = ""
        )
        assertEquals(listOf("作品数" to "248 部"), buildActressInfoRows(profile))
    }

    @Test
    fun buildActressInfoRows_emptyWhenNothingAvailable() {
        // 全空 → 返回空列表，调用方据此整块卡片都不渲染。
        val rows = buildActressInfoRows(ActressProfile(actress = actress()))
        assertTrue("全空时不应产生任何行：$rows", rows.isEmpty())
    }

    @Test
    fun buildActressInfoRows_neverLeaksNullOrJson() {
        val profile = ActressProfile(
            actress = actress(movieCount = 12),
            birthday = "null",
            size = parseActressSize(JsonParser.parseString("\"T170 B88\"")),
            lastReleaseDate = "",
            hometown = "  ",
            hobby = "null",
            bloodType = ""
        )
        val rows = buildActressInfoRows(profile)
        assertEquals(listOf("作品数" to "12 部", "三围" to "T170 B88"), rows)
        rows.forEach { (label, value) ->
            assertTrue("标签不应为空：$rows", label.isNotBlank())
            assertTrue("值不应为空：$rows", value.isNotBlank())
            assertTrue("值不应出现 null：$rows", !value.equals("null", ignoreCase = true))
            assertTrue("值不应出现 JSON 大括号：$rows", !value.contains("{") && !value.contains("}"))
            assertTrue("值不应出现 JSON 引号：$rows", !value.contains("\""))
        }
    }
}
