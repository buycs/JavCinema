package io.github.javcinema.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteBackupTest {

    @Test
    fun parsesValidExport() {
        val json = """
            {
              "format": "javcinema-favorites",
              "version": 1,
              "movies": [
                {"code": "SSIS-001", "title": "标题", "source": "骑兵", "link": "https://a/1"},
                {"code": "IPX-002"}
              ],
              "actresses": [
                {"name": "三上悠亜", "source": "骑兵"}
              ]
            }
        """.trimIndent()
        val parsed = parseFavoriteExport(json)
        assertEquals(2, parsed.movies.size)
        assertEquals(1, parsed.actresses.size)
        assertEquals("SSIS-001", parsed.movies[0].codeOrName)
        assertEquals("标题", parsed.movies[0].title)
        assertEquals("三上悠亜", parsed.actresses[0].codeOrName)
        assertTrue(parsed.actresses[0].isActress)
    }

    @Test
    fun skipsEntriesWithoutIdentity() {
        val json = """
            {
              "movies": [{"title": "没有番号"}, {"code": "  "}, {"code": "SSIS-001"}],
              "actresses": [{"link": "x"}, {"name": "有名字"}]
            }
        """.trimIndent()
        val parsed = parseFavoriteExport(json)
        assertEquals(1, parsed.movies.size)
        assertEquals("SSIS-001", parsed.movies[0].codeOrName)
        assertEquals(1, parsed.actresses.size)
    }

    @Test
    fun rejectsEmptyAndMalformedInput() {
        assertThrows(IllegalArgumentException::class.java) { parseFavoriteExport("") }
        assertThrows(IllegalArgumentException::class.java) { parseFavoriteExport("   ") }
        assertThrows(IllegalArgumentException::class.java) { parseFavoriteExport("[1,2,3]") }
        // 语法错误由 Gson 抛出，不是 IllegalArgumentException。
        assertThrows(RuntimeException::class.java) { parseFavoriteExport("not json") }
    }

    @Test
    fun rejectsForeignFormat() {
        val json = """{"format":"some-other-app","movies":[{"code":"SSIS-001"}]}"""
        val error = assertThrows(IllegalArgumentException::class.java) { parseFavoriteExport(json) }
        assertTrue(error.message.orEmpty().contains("JavCinema"))
    }

    @Test
    fun acceptsMissingFormatForBackwardCompatibility() {
        // 早期导出可能没有 format 字段，应当仍可导入。
        val json = """{"movies":[{"code":"SSIS-001"}]}"""
        assertEquals(1, parseFavoriteExport(json).movies.size)
    }

    // --- 导入条数上限 ---

    @Test
    fun acceptsExactlyTheLimit() {
        val movies = (1..MAX_IMPORT_ITEMS).joinToString(",") { """{"code":"CODE-$it"}""" }
        val json = """{"movies":[$movies],"actresses":[]}"""
        assertEquals(MAX_IMPORT_ITEMS, parseFavoriteExport(json).movies.size)
    }

    @Test
    fun rejectsTooManyMovies() {
        val movies = (1..MAX_IMPORT_ITEMS + 1).joinToString(",") { """{"code":"CODE-$it"}""" }
        val json = """{"movies":[$movies],"actresses":[]}"""
        val error = assertThrows(IllegalArgumentException::class.java) { parseFavoriteExport(json) }
        assertTrue(error.message.orEmpty().contains("条目过多"))
    }

    @Test
    fun rejectsTooManyActresses() {
        val actresses = (1..MAX_IMPORT_ITEMS + 1).joinToString(",") { """{"name":"NAME-$it"}""" }
        val json = """{"movies":[],"actresses":[$actresses]}"""
        val error = assertThrows(IllegalArgumentException::class.java) { parseFavoriteExport(json) }
        assertTrue(error.message.orEmpty().contains("条目过多"))
    }

    @Test
    fun limitIsCountedPerCategoryNotInTotal() {
        // 影片与女优各自计数：两边都刚好到上限时应当通过。
        val movies = (1..MAX_IMPORT_ITEMS).joinToString(",") { """{"code":"CODE-$it"}""" }
        val actresses = (1..MAX_IMPORT_ITEMS).joinToString(",") { """{"name":"NAME-$it"}""" }
        val json = """{"movies":[$movies],"actresses":[$actresses]}"""
        val parsed = parseFavoriteExport(json)
        assertEquals(MAX_IMPORT_ITEMS, parsed.movies.size)
        assertEquals(MAX_IMPORT_ITEMS, parsed.actresses.size)
    }
}
