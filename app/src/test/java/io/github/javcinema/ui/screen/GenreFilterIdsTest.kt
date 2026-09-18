package io.github.javcinema.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class GenreFilterIdsTest {

    @Test
    fun extractsBareAndPathIds() {
        assertEquals("yjkojnz", genreIdFromLink("yjkojnz"))
        assertEquals("yjkojnz", genreIdFromLink("genre/yjkojnz"))
        assertEquals("yjkojnz", genreIdFromLink("/cn/genre/yjkojnz"))
    }

    @Test
    fun combinesDistinctIdsAsCsvPath() {
        assertEquals(
            "genre/yjkojnz,rpnpmko",
            combinedGenreFilterUrl(listOf("genre/yjkojnz", "rpnpmko", "yjkojnz"))
        )
    }

    @Test
    fun joinsNamesForTitle() {
        assertEquals("巨乳、中出", combinedGenreTitle(listOf("巨乳", "中出", "巨乳")))
    }
}
