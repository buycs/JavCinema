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

    // --- 取流合并判据 ---

    /**
     * 回归：`ad_` 这个**裸**标记会误伤正常地址 —— `.../download_video.m3u8`、
     * `.../load_720p.m3u8` 都会被判成广告流。误判的代价是「真实流被拒 → 静默回退到站点页面」，
     * 没有任何报错，极难排查。所以标记一律带路径分隔符前缀（`/ad_`）。
     */
    @Test
    fun adMarkerDoesNotMatchOrdinaryPathWords() {
        assertFalse(looksLikeAdStream("https://cdn.example.com/download_video.m3u8"))
        assertFalse(looksLikeAdStream("https://cdn.example.com/load_720p.m3u8"))
        assertFalse(looksLikeAdStream("https://cdn.example.com/upload_720p.m3u8"))
        // 真正的广告路径仍要拦住
        assertTrue(looksLikeAdStream("https://cdn.example.com/ad_break.m3u8"))
        assertTrue(looksLikeAdStream("https://cdn.example.com/ads/banner.mp4"))
    }

    /**
     * 取流三条过滤（能播 / 非广告 / 非预览片）合并后的判据。
     *
     * 用**实测抓到的真实地址**做锚点，防止将来收紧过滤时把真实流一起误杀。
     */
    @Test
    fun acceptableStreamUrlAcceptsRealSurritStream() {
        // 模拟器实测嗅到的真实 m3u8
        assertTrue(
            isAcceptableStreamUrl(
                "https://surrit.com/e33c158d-87b1-49eb-8035-f5d6d020967a/playlist.m3u8"
            )
        )
        // 真实播放页里的悬停预览片，属于**别的番号**，必须拒
        assertFalse(
            isAcceptableStreamUrl("https://fourhoi.com/ssni-879-uncensored-leak/preview.mp4")
        )
        // MSE 的 blob 地址对 Media3 无意义
        assertFalse(
            isAcceptableStreamUrl("blob:https://missav.ws/4c0e8c65-f98d-4a30-beb2-d53156db0bec")
        )
        assertFalse(isAcceptableStreamUrl("https://securepubads.g.doubleclick.net/preroll.m3u8"))
        assertFalse(isAcceptableStreamUrl(null))
        assertFalse(isAcceptableStreamUrl(""))
    }
}
