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
            bloodType = "A",
            downloadMovieCount = 2433
        )
        assertEquals(
            listOf(
                // 统计在前（三源 60/60 必有），可下载数并进同一项
                "作品数" to "4617 部 · 可下载 2433",
                "最近作品" to "2026-09-19",
                // 基本资料：星座紧跟生日，它本来就是从生日算出来的
                "生日" to "1988-05-24",
                "星座" to "双子座",
                "血型" to "A",
                "身高" to "163cm",
                "胸" to "88(D)",
                "腰" to "59",
                "臀" to "85",
                // 其余稀疏资料垫后，免得第一行净是不一定显示的胶囊
                "出生地" to "京都府",
                "兴趣" to "ゲーム"
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

    // ------------------------------------------------------------------
    // 标题区 / 细节区的切分：头像右侧前三行是 名称 + 作品数 + 最近作品，
    // 头像直径就是按这三行的高度对齐的，所以哪一项进标题区必须由策略层说了算。
    // ------------------------------------------------------------------

    @Test
    fun splitActressHeadline_liftsOnlyTheStatistics() {
        val rows = listOf(
            "作品数" to "4617 部 · 可下载 2433",
            "最近作品" to "2026-09-19",
            "生日" to "1988-05-24",
            "星座" to "双子座"
        )
        val (headline, details) = splitActressHeadline(rows)
        assertEquals(rows.take(2), headline)
        assertEquals(rows.drop(2), details)
    }

    @Test
    fun splitActressHeadline_doesNotBackfillFromDetailsWhenAStatisticIsMissing() {
        // 站点没给作品数时标题区只剩「最近作品」，不会把生日顶上来多占一行。
        val (headline, details) = splitActressHeadline(
            listOf("生日" to "1988-05-24", "最近作品" to "2026-09-19")
        )
        assertEquals(listOf("最近作品" to "2026-09-19"), headline)
        assertEquals(listOf("生日" to "1988-05-24"), details)
    }

    // ------------------------------------------------------------------
    // 星座：由生日推算，不读站点的 constellation 序号。
    //
    // 站点那个字段实测 180 条里只有 8 条非零，且这 8 条全都同时有生日（零增量信息），
    // 其中一条还与自身生日矛盾 —— 所以这里完全按生日算，边界日各测一头。
    // ------------------------------------------------------------------

    @Test
    fun zodiacOf_matchesEverySignStartAndTheDayBefore() {
        val expected = listOf(
            "摩羯座", "水瓶座", "双鱼座", "白羊座", "金牛座", "双子座",
            "巨蟹座", "狮子座", "处女座", "天秤座", "天蝎座", "射手座"
        )
        // 每个星座的起始日，以及它的前一天（必须仍属于上一个星座）
        val boundaries = listOf(
            "01-01" to "摩羯座",
            "01-19" to "摩羯座",
            "01-20" to "水瓶座",
            "02-18" to "水瓶座",
            "02-19" to "双鱼座",
            "03-20" to "双鱼座",
            "03-21" to "白羊座",
            "04-19" to "白羊座",
            "04-20" to "金牛座",
            "05-20" to "金牛座",
            "05-21" to "双子座",
            "06-21" to "双子座",
            "06-22" to "巨蟹座",
            "07-22" to "巨蟹座",
            "07-23" to "狮子座",
            "08-22" to "狮子座",
            "08-23" to "处女座",
            "09-22" to "处女座",
            "09-23" to "天秤座",
            "10-23" to "天秤座",
            "10-24" to "天蝎座",
            "11-22" to "天蝎座",
            "11-23" to "射手座",
            "12-21" to "射手座",
            "12-22" to "摩羯座",
            "12-31" to "摩羯座"
        )
        boundaries.forEach { (monthDay, name) ->
            assertEquals("1990-$monthDay", name, zodiacOf("1990-$monthDay"))
        }
        // 十二档都覆盖到了
        assertEquals(12, boundaries.map { it.second }.distinct().size)
        assertEquals(expected.sorted(), boundaries.map { it.second }.distinct().sorted())
    }

    @Test
    fun zodiacOf_agreesWithSiteDataWhereSiteIsSelfConsistent() {
        // 站点 constellation 非零的那几条（序号 1=白羊 … 12=双鱼），逐个对得上；
        // 唯一不符的是 JULIA 1987-05-25 给了 4（巨蟹），那是站点自身生日与星座矛盾。
        assertEquals("双子座", zodiacOf("1988-05-24"))   // 站点给 3
        assertEquals("双鱼座", zodiacOf("1988-02-21"))   // 站点给 12
        assertEquals("射手座", zodiacOf("1991-12-03"))   // 站点给 9
        assertEquals("射手座", zodiacOf("1978-12-21"))   // 站点给 9
        assertEquals("金牛座", zodiacOf("1983-04-23"))   // 站点给 2
        assertEquals("金牛座", zodiacOf("1989-05-04"))   // 站点给 2
        assertEquals("白羊座", zodiacOf("1993-03-23"))   // 站点给 1
        assertEquals("双子座", zodiacOf("1987-05-25"))   // 站点给 4 —— 以生日为准
    }

    @Test
    fun zodiacOf_returnsNullForMissingOrMalformedBirthday() {
        assertNull(zodiacOf(null))
        assertNull(zodiacOf(""))
        assertNull(zodiacOf("1988-05"))
        assertNull(zodiacOf("1988/05/24"))
        assertNull(zodiacOf("五月二十四"))
        assertNull(zodiacOf("0000-13-01"))   // 月份越界
        assertNull(zodiacOf("0000-00-10"))   // 月份为 0
        assertNull(zodiacOf("0000-06-32"))   // 日期越界
        assertEquals("双子座", zodiacOf("  1988-5-24  "))  // 容忍首尾空白与单位数月/日
    }

    @Test
    fun buildActressInfoRows_omitsZodiacWhenBirthdayUnusable() {
        // 步兵 / 欧美源没有生日：生日与星座两项都不该出现，而不是显示「星座：」空值。
        val rows = buildActressInfoRows(
            ActressProfile(actress = actress(movieCount = 1749), birthday = "", lastReleaseDate = "2026-09-20")
        )
        assertEquals(listOf("作品数" to "1749 部", "最近作品" to "2026-09-20"), rows)
    }

    @Test
    fun buildActressInfoRows_movieCountFallsBackToTotalOnly() {
        // 站点没给 downloadMovieCount 时退化成只显示总数。
        val rows = buildActressInfoRows(
            ActressProfile(actress = actress(movieCount = 248), downloadMovieCount = null)
        )
        assertEquals(listOf("作品数" to "248 部"), rows)
    }

    @Test
    fun buildActressInfoRows_ignoresDownloadCountExceedingTotal() {
        // 脏值保护：可下载数大于总数时不显示子集，免得露出「4617 部 · 可下载 9999」这种。
        val rows = buildActressInfoRows(
            ActressProfile(actress = actress(movieCount = 3), downloadMovieCount = 9)
        )
        assertEquals(listOf("作品数" to "3 部"), rows)
    }
}
