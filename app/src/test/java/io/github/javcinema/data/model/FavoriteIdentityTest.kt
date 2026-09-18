package io.github.javcinema.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteIdentityTest {

    @Test
    fun sameCodeDifferentSources_areDistinct() {
        val cavalry = Movie().apply {
            code = "SSIS-001"
            link = "same-id"
            dataSourceName = "骑兵"
        }
        val infantry = Movie().apply {
            code = "SSIS-001"
            link = "same-id"
            dataSourceName = "步兵"
        }
        assertFalse(sameFavoriteMovie(cavalry, infantry))
    }

    @Test
    fun sameCodeLegacyNullSource_matchesCurrentSource() {
        val legacy = Movie().apply { code = "SSIS-001" }
        val current = Movie().apply {
            code = "SSIS-001"
            dataSourceName = "骑兵"
        }
        assertTrue(sameFavoriteMovie(legacy, current))
    }

    @Test
    fun actressSameNameDifferentSources_areDistinct() {
        val left = Actress().apply {
            name = "A"
            dataSourceName = "骑兵"
        }
        val right = Actress().apply {
            name = "A"
            dataSourceName = "步兵"
        }
        assertFalse(sameFavoriteActress(left, right))
    }

    @Test
    fun favoriteQueryMatchesTitleOrCode() {
        assertTrue(matchesFavoriteQuery("Test Title", "SSIS-001", "ssis"))
        assertTrue(matchesFavoriteQuery("花园", "ABC-123", "花园"))
        assertFalse(matchesFavoriteQuery("Test Title", "SSIS-001", "zzzz"))
        assertTrue(matchesFavoriteQuery("anything", "code", "  "))
    }
}
