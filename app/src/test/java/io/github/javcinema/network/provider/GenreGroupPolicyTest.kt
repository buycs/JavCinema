package io.github.javcinema.network.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 类别页分组标签、显示名与归并规则的契约。
 *
 * 用例的形状与计数取自三个数据源的真实 `getGenres` 返回：
 * 骑兵 `/jav/` 9 组 366 条、步兵 `/javu/` 8 组 382 条、欧美 `/wav/` 8 组 369 条。
 *
 * 请求语言是 `cn`，分组标签取**站点自己的 cn 字典**（主题 / … / 类别 / 其他），
 * 与组内类别名同语言；类别名走 [preferredGenreName]（站点的 `genreName` 已按 cn 填好）。
 */
class GenreGroupPolicyTest {

    // ------------------------------------------------------------------
    // 标签选择
    // ------------------------------------------------------------------

    @Test
    fun forApiPath_picksLabelsPerSite() {
        assertSame(GenreGroupLabels.JAV, GenreGroupLabels.forApiPath("/jav/data/api/"))
        assertSame(GenreGroupLabels.JAVU, GenreGroupLabels.forApiPath("/javu/data/api/"))
        assertSame(GenreGroupLabels.WAV, GenreGroupLabels.forApiPath("/wav/data/api/"))
    }

    @Test
    fun forApiPath_toleratesMissingSlashAndUnknownSites() {
        assertSame(GenreGroupLabels.WAV, GenreGroupLabels.forApiPath("/wav"))
        assertSame(GenreGroupLabels.WAV, GenreGroupLabels.forApiPath("wav"))
        assertSame(GenreGroupLabels.JAVU, GenreGroupLabels.forApiPath("/javu"))
        // 认不出来时按骑兵（默认数据源）
        assertSame(GenreGroupLabels.JAV, GenreGroupLabels.forApiPath(null))
        assertSame(GenreGroupLabels.JAV, GenreGroupLabels.forApiPath(""))
        assertSame(GenreGroupLabels.JAV, GenreGroupLabels.forApiPath("/"))
        assertSame(GenreGroupLabels.JAV, GenreGroupLabels.forApiPath("/unknown/data/api/"))
    }

    @Test
    fun typesZeroToSixUseChineseLabelsOnAllThreeSites() {
        val zh = listOf("主题", "角色", "服装", "身体", "性行为", "玩法", "类别")
        zh.forEachIndexed { type, label ->
            assertEquals(label, GenreGroupLabels.JAV.at(type))
            assertEquals(label, GenreGroupLabels.JAVU.at(type))
            assertEquals(label, GenreGroupLabels.WAV.at(type))
        }
    }

    /** 三源同一套前端，共用一份 cn 字典；唯一的差别是骑兵把 type 7 单独标成 AV OPEN。 */
    @Test
    fun allThreeSitesShareOneCnDictionary() {
        for (type in 0..7) {
            val javu = GenreGroupLabels.JAVU.at(type)
            assertEquals("type $type 的欧美标签要和步兵一致", javu, GenreGroupLabels.WAV.at(type))
            if (type < 7) {
                assertEquals("type $type 的骑兵标签要和步兵一致", javu, GenreGroupLabels.JAV.at(type))
            }
        }
        assertEquals("AV OPEN", GenreGroupLabels.JAV.at(7))
    }

    @Test
    fun javTypeSevenIsItsOwnAvOpenGroup() {
        // 实测骑兵 type 7 的 27 条整组都是 AV OPEN 2016 各部门，站点自己却标「其他」
        assertEquals("AV OPEN", GenreGroupLabels.JAV.at(7))
    }

    @Test
    fun javuAndWavTypeSevenIsTheFallbackLabel() {
        // 步兵 / 欧美的 type 7 是普通类别（場所 / 节日），没有 AV OPEN 这回事
        assertEquals("其他", GenreGroupLabels.JAVU.at(7))
        assertEquals("其他", GenreGroupLabels.WAV.at(7))
    }

    @Test
    fun outOfRangeAndMissingTypesFallBack() {
        // 骑兵特有的 -1 组（64 条：パラダイスTV / 促销精选 / AV OPEN 2014・2015）
        assertEquals("其他", GenreGroupLabels.JAV.at(-1))
        assertEquals("其他", GenreGroupLabels.JAV.at(99))
        assertEquals("其他", GenreGroupLabels.JAV.at(null))
        assertEquals("其他", GenreGroupLabels.WAV.at(-1))
        assertEquals("其他", GenreGroupLabels.WAV.at(null))
        assertEquals(8, GenreGroupLabels.JAV.typeCount)
    }

    // ------------------------------------------------------------------
    // 类别显示名：优先简体中文
    // ------------------------------------------------------------------

    @Test
    fun preferredGenreName_prefersSimplifiedChinese() {
        // 接口真实条目：{"genreName_ja":"セクシー","genreName_en":"Sexy",
        //              "genreName_cn":"性感的","genreName":"セクシー"}
        assertEquals("性感的", preferredGenreName("性感的", "セクシー", "セクシー"))
        assertEquals("角色扮演", preferredGenreName("角色扮演", "Character", "キャラクター"))
    }

    /**
     * 回归判据：**缺中文时接口给的是空串，不是 null**（实测骑兵 366 条里有 131 条如此，
     * 占 36%）。只写 `cn ?: fallback` 会拿到空串，类别名在界面上直接消失。
     */
    @Test
    fun preferredGenreName_fallsBackWhenChineseIsEmptyString() {
        assertEquals("パラダイスTV", preferredGenreName("", "パラダイスTV", "パラダイスTV"))
        assertEquals("DVDトースター", preferredGenreName("", "DVDトースター", "DVDトースター"))
    }

    @Test
    fun preferredGenreName_fallsBackWhenChineseIsNullOrBlank() {
        assertEquals("和服・浴衣", preferredGenreName(null, "和服・浴衣", "和服・浴衣"))
        assertEquals("和服・浴衣", preferredGenreName("   ", "和服・浴衣", "和服・浴衣"))
    }

    @Test
    fun preferredGenreName_fallsBackToJapaneseWhenSiteDefaultIsMissing() {
        assertEquals("ディルド", preferredGenreName(null, null, "ディルド"))
        assertEquals("ディルド", preferredGenreName("", "", "ディルド"))
    }

    @Test
    fun preferredGenreName_returnsEmptyWhenNothingIsUsable() {
        assertEquals("", preferredGenreName(null, null, null))
        assertEquals("", preferredGenreName("", "", ""))
    }

    @Test
    fun preferredGenreName_trimsSurroundingWhitespace() {
        assertEquals("性感的", preferredGenreName("  性感的  ", "セクシー", "セクシー"))
    }

    // ------------------------------------------------------------------
    // 归并规则
    // ------------------------------------------------------------------

    @Test
    fun groupGenresByLabel_keepsFirstAppearanceOrderAndMergesSameLabel() {
        val map = groupGenresByLabel(
            listOf(
                0 to listOf("企画"),
                -1 to listOf("パラダイスTV"),
                2 to listOf("コスプレ"),
                -1 to listOf("DVDトースター")
            ),
            GenreGroupLabels.JAV
        )
        assertEquals(listOf("主题", "其他", "服装"), map.keys.toList())
        // 同一个标签出现两次要合并，不能覆盖
        assertEquals(listOf("パラダイスTV", "DVDトースター"), map["其他"])
    }

    @Test
    fun groupGenresByLabel_skipsEmptyGroups() {
        val map = groupGenresByLabel(
            listOf(0 to emptyList<String>(), 1 to listOf("ウェイトレス")),
            GenreGroupLabels.JAV
        )
        assertEquals(listOf("角色"), map.keys.toList())
    }

    @Test
    fun groupGenresByLabel_unknownTypeFallsToFallbackInsteadOfBeingDropped() {
        val map = groupGenresByLabel(
            listOf(0 to listOf("企画"), 42 to listOf("謎")),
            GenreGroupLabels.JAV
        )
        assertEquals(listOf("主题", "其他"), map.keys.toList())
        assertEquals(listOf("謎"), map["其他"])
    }

    @Test
    fun groupGenresByLabel_nullTypeFallsToFallback() {
        // 元素缺 type 字段时不能整组丢掉
        val map = groupGenresByLabel(listOf(null to listOf("謎")), GenreGroupLabels.JAV)
        assertEquals(listOf("其他"), map.keys.toList())
    }

    // ------------------------------------------------------------------
    // 三个数据源的真实形状
    // ------------------------------------------------------------------

    @Test
    fun javShapeRendersNineGroupsInSiteOrder() {
        // 骑兵 data 是 dict：0~6 常规组、7 是 AV OPEN 专题、-1 是站点杂项，站点把 -1 排在最后
        val groups = listOf(0, 1, 2, 3, 4, 5, 6, 7, -1).map { it to listOf("g$it") }
        val map = groupGenresByLabel(groups, GenreGroupLabels.JAV)
        assertEquals(
            listOf(
                "主题", "角色", "服装", "身体",
                "性行为", "玩法", "类别", "AV OPEN", "其他"
            ),
            map.keys.toList()
        )
        assertEquals(9, map.size)
        assertEquals(listOf("g-1"), map["其他"])
        assertEquals(listOf("g7"), map["AV OPEN"])
    }

    @Test
    fun javuShapeKeepsTypeSevenInsteadOfDroppingIt() {
        // 步兵 data 是 list of list，下标即 type，元素自带 type 字段。
        //
        // 回归判据：旧实现把最后一段的 key 改写成 "-1" 才绕开了「跳过 type 7」的分支，
        // 属巧合；站点一旦多返回一段，中间那段就会被整组丢掉。
        // 实测步兵 type 7 有 39 条类别（別荘 / 撮影現場 / エレベーター），必须保留。
        val groups = (0..7).map { it to listOf("g$it") }
        val map = groupGenresByLabel(groups, GenreGroupLabels.JAVU)
        assertEquals(
            listOf(
                "主题", "角色", "服装", "身体",
                "性行为", "玩法", "类别", "其他"
            ),
            map.keys.toList()
        )
        assertEquals(8, map.size)
        assertEquals(listOf("g7"), map["其他"])
    }

    @Test
    fun javuExtraGroupDoesNotSwallowTypeSeven() {
        // 站点将来多返回一段时的防护：type 7 必须还在，多出来的那段落到兜底标签
        val groups = (0..8).map { it to listOf("g$it") }
        val map = groupGenresByLabel(groups, GenreGroupLabels.JAVU)
        assertEquals(listOf("g7", "g8"), map["其他"])
        assertTrue("type 6 那组不该被挤掉：${map.keys}", map.containsKey("类别"))
    }

    @Test
    fun wavShapeUsesTheSameChineseLabels() {
        val groups = (0..7).map { it to listOf("g$it") }
        val map = groupGenresByLabel(groups, GenreGroupLabels.WAV)
        assertEquals(
            listOf(
                "主题", "角色", "服装", "身体",
                "性行为", "玩法", "类别", "其他"
            ),
            map.keys.toList()
        )
        assertEquals(listOf("g7"), map["其他"])
    }
}
