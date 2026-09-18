package io.github.javcinema.ui.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieDetailLoadGuardTest {

    @Test
    fun sameCodeSameVersionSuccess_skips() {
        assertTrue(
            shouldSkipDetailLoad(
                movieCode = "SSIS-001",
                currentMovieCode = "SSIS-001",
                version = 2,
                lastVersion = 2,
                isError = false
            )
        )
    }

    @Test
    fun sameCodeVersionChanged_doesNotSkip() {
        assertFalse(
            shouldSkipDetailLoad(
                movieCode = "SSIS-001",
                currentMovieCode = "SSIS-001",
                version = 3,
                lastVersion = 2,
                isError = false
            )
        )
    }

    @Test
    fun errorState_doesNotSkip() {
        assertFalse(
            shouldSkipDetailLoad(
                movieCode = "SSIS-001",
                currentMovieCode = "SSIS-001",
                version = 2,
                lastVersion = 2,
                isError = true
            )
        )
    }

    @Test
    fun differentCode_doesNotSkip() {
        assertFalse(
            shouldSkipDetailLoad(
                movieCode = "SSIS-002",
                currentMovieCode = "SSIS-001",
                version = 2,
                lastVersion = 2,
                isError = false
            )
        )
    }
}
