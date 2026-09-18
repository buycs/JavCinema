package io.github.javcinema.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {

    @Test
    fun newer_detected() {
        assertTrue(isNewerVersion("v0.4.0", "0.3.0"))
        assertTrue(isNewerVersion("0.3.1", "0.3.0"))
        assertTrue(isNewerVersion("v0.3.0", "0.2.9"))
    }

    @Test
    fun same_or_older_notNewer() {
        assertFalse(isNewerVersion("v0.3.0", "0.3.0"))
        assertFalse(isNewerVersion("v0.2.9", "0.3.0"))
        assertFalse(isNewerVersion("", "0.3.0"))
        assertFalse(isNewerVersion("v0.4.0", ""))
        // 后缀不算新版，避免 beta 误提示
        assertFalse(isNewerVersion("v0.3.0-beta", "0.3.0"))
    }

    @Test
    fun parse_releaseJson() {
        val json = """{"tag_name":"v0.4.0","name":"0.4.0","html_url":"https://github.com/buycs/JavCinema/releases/tag/v0.4.0","body":"notes"}"""
        val info = parseReleaseJson(json)
        assertEquals("v0.4.0", info.tag)
        assertEquals("https://github.com/buycs/JavCinema/releases/tag/v0.4.0", info.htmlUrl)
    }
}
