package io.github.javcinema.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class ActressStarIdTest {

    @Test
    fun extractsIdFromBareStarId() {
        assertEquals("kwbydan", actressStarId("kwbydan"))
    }

    @Test
    fun extractsIdFromStarPath() {
        assertEquals("kwbydan", actressStarId("star/kwbydan"))
        assertEquals("kwbydan", actressStarId("/cn/star/kwbydan"))
    }

    @Test
    fun moviesUrlUsesStarPrefixForBareId() {
        assertEquals("star/kwbydan", actressMoviesUrl("kwbydan", "kwbydan"))
        assertEquals("/cn/star/kwbydan", actressMoviesUrl("kwbydan", "/cn/star/kwbydan"))
    }
}
