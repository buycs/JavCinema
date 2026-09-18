package io.github.javcinema.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MissavStreamPolicyTest {

    @Test
    fun detectsPlayableExtensions() {
        assertTrue(isPlayableStreamUrl("https://cdn.example.com/hls/master.m3u8"))
        assertTrue(isPlayableStreamUrl("https://cdn.example.com/v.mp4?token=1"))
        assertFalse(isPlayableStreamUrl("https://missav.ws/en/pppe-443"))
        assertFalse(isPlayableStreamUrl("https://cdn.example.com/thumb.jpg"))
        assertFalse(isPlayableStreamUrl(null))
    }

    @Test
    fun normalizesEscapedAndProtocolRelativeUrls() {
        assertEquals(
            "https://cdn.example.com/a.m3u8",
            normalizeStreamUrl("https:\\/\\/cdn.example.com\\/a.m3u8")
        )
        assertEquals("https://cdn.example.com/a.m3u8", normalizeStreamUrl("//cdn.example.com/a.m3u8"))
        assertEquals("https://cdn.example.com/a.mp4?x=1&y=2", normalizeStreamUrl("https://cdn.example.com/a.mp4?x=1&amp;y=2"))
        assertNull(normalizeStreamUrl("/relative/a.m3u8"))
        assertNull(normalizeStreamUrl(""))
        assertNull(normalizeStreamUrl("   "))
    }

    @Test
    fun extractsFromVideoSourceTag() {
        val html = """
            <html><body>
            <video controls poster="/p.jpg">
              <source src="https://cdn.example.com/videos/pppe-443/master.m3u8" type="application/x-mpegURL">
            </video>
            </body></html>
        """.trimIndent()
        assertEquals(
            "https://cdn.example.com/videos/pppe-443/master.m3u8",
            extractMissavStreamUrl(html)
        )
    }

    @Test
    fun extractsFromPlayerConfigWithEscapedSlashes() {
        val html = """<script>var player = new Playerjs({file:"https:\/\/cdn.example.com\/hls\/pppe-443\/index.m3u8"});</script>"""
        assertEquals(
            "https://cdn.example.com/hls/pppe-443/index.m3u8",
            extractMissavStreamUrl(html)
        )
    }

    @Test
    fun fallsBackToRawScan() {
        val html = """<div data-x="https://cdn.example.com/only.m3u8"></div>"""
        assertEquals("https://cdn.example.com/only.m3u8", extractMissavStreamUrl(html))
    }

    @Test
    fun returnsNullWhenNoStreamPresent() {
        assertNull(extractMissavStreamUrl("<html><body>just a moment</body></html>"))
        assertNull(extractMissavStreamUrl(""))
        assertNull(extractMissavStreamUrl("   "))
    }
}
