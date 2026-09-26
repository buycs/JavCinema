package io.github.javcinema.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MagnetLinkTest {

    @Test
    fun create_keepsEveryTracker() {
        val raw = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567" +
            "&dn=SSIS-001" +
            "&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce" +
            "&tr=udp%3A%2F%2Ftracker.torrent.eu.org%3A451%2Fannounce"
        val link = MagnetLink.create(raw).magnetLink!!
        assertEquals(raw, link)
        assertEquals(2, Regex("tr=").findAll(link).count())
    }

    @Test
    fun create_unescapesHtmlEntityAmpersand() {
        val link = MagnetLink.create("magnet:?xt=urn:btih:abc&amp;tr=udp%3A%2F%2Fx%2Fannounce").magnetLink!!
        assertTrue(link.contains("&tr="))
        assertNull(link.indexOf("&amp;").takeIf { it >= 0 })
    }

    @Test
    fun create_decodesWhollyEncodedUri() {
        val link = MagnetLink.create("magnet%3A%3Fxt%3Durn%3Abtih%3Aabc%26tr%3Dudp%3A%2F%2Fx%2Fannounce").magnetLink!!
        assertTrue(link.startsWith("magnet:?xt=urn:btih:abc"))
        assertTrue(link.contains("&tr=udp://x/announce"))
    }

    @Test
    fun create_doesNotDoubleDecodeLegitPercentEscapes() {
        val raw = "magnet:?xt=urn:btih:abc&dn=SSIS%20001%20CH&tr=udp%3A%2F%2Fx%2Fannounce"
        assertEquals(raw, MagnetLink.create(raw).magnetLink)
    }

    @Test
    fun create_trimsSurroundingBlank() {
        assertEquals(
            "magnet:?xt=urn:btih:abc",
            MagnetLink.create("  magnet:?xt=urn:btih:abc\n").magnetLink
        )
    }

    @Test
    fun create_keepsNullAndEmptyAsIs() {
        assertNull(MagnetLink.create(null).magnetLink)
        assertEquals("", MagnetLink.create("   ").magnetLink)
    }
}
