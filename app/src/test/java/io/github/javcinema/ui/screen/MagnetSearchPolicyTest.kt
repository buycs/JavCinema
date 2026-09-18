package io.github.javcinema.ui.screen

import io.github.javcinema.data.model.DownloadLink
import io.github.javcinema.data.model.MagnetFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MagnetSearchPolicyTest {

    @Test
    fun failure_becomesError() {
        val ui = toMagnetSourceUi(false, emptyList(), "timeout")
        assertTrue(ui is MagnetSourceUi.Error)
        assertEquals("timeout", (ui as MagnetSourceUi.Error).message)
    }

    @Test
    fun emptySuccess_isNotError() {
        val ui = toMagnetSourceUi(true, emptyList(), "ignored")
        assertTrue(ui is MagnetSourceUi.Success)
        assertTrue((ui as MagnetSourceUi.Success).items.isEmpty())
    }

    @Test
    fun shouldLoadFiles_onlyWhenMissingAndIdle() {
        assertTrue(shouldLoadFiles(null, alreadyLoading = false))
        assertFalse(shouldLoadFiles(listOf(MagnetFile()), alreadyLoading = false))
        assertFalse(shouldLoadFiles(null, alreadyLoading = true))
        val unused = DownloadLink()
        assertTrue(unused.files == null)
    }
}
