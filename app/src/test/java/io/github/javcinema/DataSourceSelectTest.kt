package io.github.javcinema

import io.github.javcinema.data.model.DataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DataSourceSelectTest {

    private val cavalry = DataSource("骑兵", "https://avmoo.shop")
    private val infantry = DataSource("步兵", "https://avsox.click")

    @Test
    fun nonemptyList_matchingSaved_returnsMatch() {
        val selected = selectDataSource(listOf(cavalry, infantry), infantry)
        assertEquals("步兵", selected.name)
    }

    @Test
    fun nonemptyList_unknownSaved_fallsBackToFirst() {
        val unknown = DataSource("未知", "https://example.com")
        val selected = selectDataSource(listOf(cavalry, infantry), unknown)
        assertEquals("骑兵", selected.name)
    }

    @Test
    fun nonemptyList_nullSaved_returnsFirst() {
        val selected = selectDataSource(listOf(cavalry, infantry), null)
        assertEquals("骑兵", selected.name)
    }

    @Test
    fun emptyList_nonNullSaved_returnsSaved() {
        val selected = selectDataSource(emptyList(), infantry)
        assertEquals("步兵", selected.name)
    }

    @Test
    fun emptyList_nullSaved_throwsRatherThanNpe() {
        assertThrows(IllegalStateException::class.java) {
            selectDataSource(emptyList(), null)
        }
    }
}
