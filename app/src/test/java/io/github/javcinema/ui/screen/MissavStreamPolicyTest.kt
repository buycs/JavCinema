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

    // --- 全页扫描的广告过滤 ---

    @Test
    fun detectsAdStreamUrls() {
        assertTrue(looksLikeAdStream("https://ads.example.com/preroll.m3u8"))
        assertTrue(looksLikeAdStream("https://securepubads.g.doubleclick.net/vast.mp4"))
        assertTrue(looksLikeAdStream("https://cdn.example.com/tracker/beacon.m3u8"))
        assertTrue(looksLikeAdStream("https://example.com/ads/banner.mp4"))
        assertFalse(looksLikeAdStream("https://cdn.example.com/videos/pppe-443/master.m3u8"))
        assertFalse(looksLikeAdStream(""))
    }

    @Test
    fun rawScanSkipsAdStreamAndPicksRealOne() {
        // 广告流排在前面，全页扫描必须跳过它拿正片。
        val html = """
            <html><body>
            <script>var ad = "https://securepubads.g.doubleclick.net/preroll.m3u8";</script>
            <div data-src="https://cdn.example.com/videos/pppe-443/master.m3u8"></div>
            </body></html>
        """.trimIndent()
        assertEquals(
            "https://cdn.example.com/videos/pppe-443/master.m3u8",
            extractMissavStreamUrl(html)
        )
    }

    @Test
    fun rawScanReturnsNullWhenOnlyAdStreamsPresent() {
        // 只有广告流时宁可返回 null（不自动接管），也不该把广告交给播放器。
        val html = """
            <html><body>
            <script>var a = "https://ads.example.com/preroll.m3u8";</script>
            <script>var b = "https://securepubads.g.doubleclick.net/midroll.mp4";</script>
            </body></html>
        """.trimIndent()
        assertNull(extractMissavStreamUrl(html))
    }

    @Test
    fun videoTagStillWinsEvenIfPageHasAds() {
        // <video> 是播放器本体，优先级高于全页扫描，不受广告过滤影响。
        val html = """
            <html><body>
            <script>var ad = "https://ads.example.com/preroll.m3u8";</script>
            <video src="https://cdn.example.com/videos/pppe-443/master.m3u8"></video>
            </body></html>
        """.trimIndent()
        assertEquals(
            "https://cdn.example.com/videos/pppe-443/master.m3u8",
            extractMissavStreamUrl(html)
        )
    }

    @Test
    fun returnsNullWhenNoStreamPresent() {
        assertNull(extractMissavStreamUrl("<html><body>just a moment</body></html>"))
        assertNull(extractMissavStreamUrl(""))
        assertNull(extractMissavStreamUrl("   "))
    }

    // --- 悬停预览片过滤 ---

    @Test
    fun detectsPreviewClips() {
        assertTrue(looksLikePreviewClip("https://fourhoi.com/ssni-879-uncensored-leak/preview.mp4"))
        assertTrue(looksLikePreviewClip("https://cdn.example.com/x/trailer.mp4"))
        assertTrue(looksLikePreviewClip("https://cdn.example.com/sample/720p.m3u8"))
        assertTrue(looksLikePreviewClip("https://cdn.example.com/teaser.mp4"))
        assertFalse(looksLikePreviewClip("https://surrit.com/abc-123/video.m3u8"))
        assertFalse(looksLikePreviewClip(null))
        assertFalse(looksLikePreviewClip(""))
    }

    /**
     * 回归：实测在 missav 播放页上，整页扫第一个 `.mp4` 抓到的是推荐位的悬停预览片
     * `fourhoi.com/ssni-879-uncensored-leak/preview.mp4` —— 搜索的番号是 SSIS-001，
     * 抓到的却是 SSNI-879 的预览片，于是播放器放了几秒别人的片段。
     */
    @Test
    fun rawScanSkipsPreviewClipAndPicksRealStream() {
        val html = """
            <html><body>
            <div class="thumbnail">
              <video class="preview hidden" data-src="https://fourhoi.com/ssni-879-uncensored-leak/preview.mp4"></video>
              <img src="https://fourhoi.com/ssni-879-uncensored-leak/cover-t.jpg">
            </div>
            <script>var src = "https://surrit.com/f5d6d020967a-8035-49eb-87b1-e33c158d/1280x720/video.m3u8";</script>
            </body></html>
        """.trimIndent()
        assertEquals(
            "https://surrit.com/f5d6d020967a-8035-49eb-87b1-e33c158d/1280x720/video.m3u8",
            extractMissavStreamUrl(html)
        )
    }

    @Test
    fun rawScanReturnsNullWhenOnlyPreviewClipsPresent() {
        // 整页只有预览片时宁可返回 null（不自动接管），也不该把别人的预览片当正片播。
        val html = """
            <html><body>
            <video data-src="https://fourhoi.com/ssni-879-uncensored-leak/preview.mp4"></video>
            <div data-src="https://fourhoi.com/ssni-879-uncensored-leak/preview.mp4"></div>
            </body></html>
        """.trimIndent()
        assertNull(extractMissavStreamUrl(html))
    }
}
