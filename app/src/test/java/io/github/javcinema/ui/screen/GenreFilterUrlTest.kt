package io.github.javcinema.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GenreFilterUrlTest {

    @Test
    fun prefixesBareId() {
        assertEquals("genre/yjkojnz", genreFilterUrl("yjkojnz"))
    }

    @Test
    fun keepsPathLinksAsIs() {
        assertEquals("genre/yjkojnz", genreFilterUrl("genre/yjkojnz"))
        assertEquals("/cn/genre/yjkojnz", genreFilterUrl("/cn/genre/yjkojnz"))
    }

    @Test
    fun returnsNullForMissingLink() {
        assertNull(genreFilterUrl(null))
        assertNull(genreFilterUrl("   "))
    }
}
