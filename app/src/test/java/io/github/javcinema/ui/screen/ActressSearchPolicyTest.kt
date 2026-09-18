package io.github.javcinema.ui.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActressSearchPolicyTest {

    @Test
    fun matchesIgnoreCaseAndSubstring() {
        assertTrue(actressMatchesQuery("三上悠亜", "悠亜"))
        assertTrue(actressMatchesQuery("Yua Mikami", "mikami"))
        assertFalse(actressMatchesQuery("三上悠亜", "桥本"))
        assertTrue(actressMatchesQuery("anyone", "  "))
    }
}
