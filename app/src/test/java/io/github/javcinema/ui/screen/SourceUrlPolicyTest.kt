package io.github.javcinema.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceUrlPolicyTest {

    @Test
    fun httpsIsAcceptedAndBlankRestoresDefault() {
        val https = validateSourceUrl("https://avmoo.shop")
        assertEquals("https://avmoo.shop", https.normalized)
        assertNull(https.error)
        assertNull(validateSourceUrl("  ").normalized)
    }

    @Test
    fun httpNeedsConfirmUnlessAllowed() {
        val pending = validateSourceUrl("http://example.com")
        assertTrue(pending.needsHttpConfirm)
        val allowed = validateSourceUrl("http://example.com", allowHttp = true)
        assertFalse(allowed.needsHttpConfirm)
        assertEquals("http://example.com", allowed.normalized)
    }

    @Test
    fun rejectsUnknownScheme() {
        assertEquals("只支持 http 或 https", validateSourceUrl("ftp://x.com").error)
    }

    @Test
    fun parsesExportedFavoritesJson() {
        val json = favoritesToJson(
            movies = listOf(
                FavoriteImportItem("SSIS-001", title = "标题", source = "骑兵"),
                FavoriteImportItem("ABC-123")
            ),
            actresses = listOf(FavoriteImportItem("三上悠亜", source = "骑兵", isActress = true))
        )
        val parsed = parseFavoriteExport(json)
        assertEquals(2, parsed.movies.size)
        assertEquals("SSIS-001", parsed.movies[0].codeOrName)
        assertEquals("骑兵", parsed.movies[0].source)
        assertEquals(1, parsed.actresses.size)
        assertEquals("三上悠亜", parsed.actresses[0].codeOrName)
        assertTrue(json.contains("\"format\": \"javcinema-favorites\""))
        val empty = favoritesToJson(emptyList(), emptyList())
        val parsedEmpty = parseFavoriteExport(empty)
        assertTrue(parsedEmpty.movies.isEmpty())
        assertTrue(parsedEmpty.actresses.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownFavoriteFormat() {
        parseFavoriteExport("""{"format":"other","movies":[],"actresses":[]}""")
    }
}
