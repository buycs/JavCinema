package io.github.javcinema.ui.screen

import io.github.javcinema.data.model.MagnetFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MagnetFilePolicyTest {

    @Test
    fun movieCodeDetection() {
        assertTrue(looksLikeMovieCode("SSIS-001"))
        assertTrue(looksLikeMovieCode("ssis001"))
        assertFalse(looksLikeMovieCode("三上悠亜"))
        assertFalse(looksLikeMovieCode(""))
    }

    @Test
    fun filtersAdFilesAndKeepsLargestVideo() {
        val files = listOf(
            MagnetFile().apply { filename = "官网.txt"; size = 12 },
            MagnetFile().apply { filename = "readme.md"; size = 8 },
            MagnetFile().apply { filename = "cover.jpg"; size = 80 },
            MagnetFile().apply { filename = "movie.mp4"; size = 1_000 },
            MagnetFile().apply { filename = "sample.mp4"; size = 200 }
        )
        val visible = visibleMagnetFiles(files)
        assertEquals(3, visible.size)
        assertFalse(visible.any { it.filename.endsWith(".txt") || it.filename.endsWith(".md") })
        assertTrue(visible.any { it.filename.endsWith(".jpg") })
        assertEquals(1, largestVideoIndex(visible))
    }

    @Test
    fun adTitleIsDetected() {
        assertTrue(isAdText("最新地址 www.example.com"))
        assertFalse(isAdText("SSIS-001 高清"))
    }
}
