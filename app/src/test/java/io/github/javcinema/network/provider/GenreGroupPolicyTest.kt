package io.github.javcinema.network.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 类别页分组标签与归并规则的契约。
 *
 * 用例的形状与计数取自三个数据源的真实 `getGenres` 返回：
 * 骑兵 `/jav/` 9 组 366 条、步兵 `/javu/` 8 组 382 条、欧美 `/wav/` 8 组 369 条。
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
    fun typesZeroToSixFollowTheSiteSemantics() {
        val ja = listOf("テーマ", "キャラクター", "コスチューム", "身体", "性行為", "プレイ", "ジャンル")
        ja.forEachIndexed { type, label ->
            assertEquals(label, GenreGroupLabels.JAV.at(type))
            assertEquals(label, GenreGroupLabels.JAVU.at(type))
        }
        val en = listOf("Theme", "Character", "Costume", "Body", "Sex Acts", "Sex Plays", "Genre")
        en.forEachIndexed { type, label -> assertEquals(label, GenreGroupLabels.WAV.at(type)) }
    }

    @Test
    fun javTypeSevenIsItsOwnAvOpenGroup() {
        // 实测骑兵 type 7 的 27 条整组都是 AV OPEN 2016 各部门，站点自己却标「其他」
        assertEquals("AV OPEN", GenreGroupLabels.JAV.at(7))
    }

    @Test
    fun javuAndWavTypeSevenIsTheFallbackLabel() {
        // 步兵 / 欧美的 type 7 是普通类别（場所 / 节日），没有 AV OPEN 这回事
        assertEquals("その他", GenreGroupLabels.JAVU.at(7))
        assertEquals("Other", GenreGroupLabels.WAV.at(7))
    }

    @Test
    fun outOfRangeAndMissingTypesFallBack() {
        // 骑兵特有的 -1 组（64 条：パラダイスTV / 促销精选 / AV OPEN 2014・2015）
        assertEquals("その他", GenreGroupLabels.JAV.at(-1))
        assertEquals("その他", GenreGroupLabels.JAV.at(99))
        assertEquals("その他", GenreGroupLabels.JAV.at(null))
        assertEquals("Other", GenreGroupLabels.WAV.at(-1))
        assertEquals("Other", GenreGroupLabels.WAV.at(null))
        assertEquals(8, GenreGroupLabels.JAV.typeCount)
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
        assertEquals(listOf("テーマ", "その他", "コスチューム"), map.keys.toList())
        // 同一个标签出现两次要合并，不能覆盖
        assertEquals(listOf("パラダイスTV", "DVDトースター"), map["その他"])
    }

    @Test
    fun groupGenresByLabel_skipsEmptyGroups() {
        val map = groupGenresByLabel(
            listOf(0 to emptyList<String>(), 1 to listOf("ウェイトレス")),
            GenreGroupLabels.JAV
        )
        assertEquals(listOf("キャラクター"), map.keys.toList())
    }

    @Test
    fun groupGenresByLabel_unknownTypeFallsToFallbackInsteadOfBeingDropped() {
        val map = groupGenresByLabel(
            listOf(0 to listOf("企画"), 42 to listOf("謎")),
            GenreGroupLabels.JAV
        )
        assertEquals(listOf("テーマ", "その他"), map.keys.toList())
        assertEquals(listOf("謎"), map["その他"])
    }

    @Test
    fun groupGenresByLabel_nullTypeFallsToFallback() {
        // 元素缺 type 字段时不能整组丢掉
        val map = groupGenresByLabel(listOf(null to listOf("謎")), GenreGroupLabels.JAV)
        assertEquals(listOf("その他"), map.keys.toList())
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
                "テーマ", "キャラクター", "コスチューム", "身体",
                "性行為", "プレイ", "ジャンル", "AV OPEN", "その他"
            ),
            map.keys.toList()
        )
        assertEquals(9, map.size)
        assertEquals(listOf("g-1"), map["その他"])
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
                "テーマ", "キャラクター", "コスチューム", "身体",
                "性行為", "プレイ", "ジャンル", "その他"
            ),
            map.keys.toList()
        )
        assertEquals(8, map.size)
        assertEquals(listOf("g7"), map["その他"])
    }

    @Test
    fun javuExtraGroupDoesNotSwallowTypeSeven() {
        // 站点将来多返回一段时的防护：type 7 必须还在，多出来的那段落到兜底标签
        val groups = (0..8).map { it to listOf("g$it") }
        val map = groupGenresByLabel(groups, GenreGroupLabels.JAVU)
        assertEquals(listOf("g7", "g8"), map["その他"])
        assertTrue("type 6 那组不该被挤掉：${map.keys}", map.containsKey("ジャンル"))
    }

    @Test
    fun wavShapeUsesEnglishLabels() {
        // 欧美站点没有日文类别名、回退英文，标签也跟随英文
        val groups = (0..7).map { it to listOf("g$it") }
        val map = groupGenresByLabel(groups, GenreGroupLabels.WAV)
        assertEquals(
            listOf(
                "Theme", "Character", "Costume", "Body",
                "Sex Acts", "Sex Plays", "Genre", "Other"
            ),
            map.keys.toList()
        )
        assertEquals(listOf("g7"), map["Other"])
    }
}
