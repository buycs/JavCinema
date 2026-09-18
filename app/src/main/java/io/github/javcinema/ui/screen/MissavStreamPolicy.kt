package io.github.javcinema.ui.screen

import org.jsoup.Jsoup

// 在「已验证会话」的 WebView 里探测当前可播流地址。返回裸字符串，空串表示未找到。
// 优先取 <video> 的实时 src（播放器常通过 JS 赋值），退化到全页扫描。
internal const val MISSAV_STREAM_PROBE_JS = """
(function(){
  try {
    var v = document.querySelector('video');
    if (v) {
      if (v.currentSrc) return v.currentSrc;
      if (v.src) return v.src;
      var s = v.querySelector('source');
      if (s && s.src) return s.src;
    }
    var html = document.documentElement.outerHTML;
    var m = html.match(/https?:\/\/[^"'\s<>\\]+?\.(?:m3u8|mp4)[^"'\s<>\\]*/i);
    return m ? m[0] : '';
  } catch (e) { return ''; }
})();
"""

private val STREAM_URL_RE = Regex(
    """(?:https?:)?//[^\s"'<>\\]+?\.(?:m3u8|mp4)(?:\?[^\s"'<>\\]*)?""",
    RegexOption.IGNORE_CASE
)

// 播放器初始化配置里可能承载直链的字段名。
private val STREAM_KEYS = listOf("source", "file", "src", "url", "hls", "hlsurl", "m3u8")

/** 是否是可直接交给 Media3 的 HLS/MP4 地址（忽略 query / fragment）。 */
internal fun isPlayableStreamUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val path = url.substringBefore('?').substringBefore('#').trim().lowercase()
    return path.endsWith(".m3u8") || path.endsWith(".mp4")
}

/** 归一 JS 里常见的转义（`\/`）、HTML 实体与协议相对地址；非 http(s) 一律判为无效。 */
internal fun normalizeStreamUrl(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    var value = raw.trim().replace("\\/", "/").replace("&amp;", "&")
    if (value.startsWith("//")) value = "https:$value"
    if (!value.startsWith("http://") && !value.startsWith("https://")) return null
    return value
}

/**
 * 从 missav 播放页 HTML 尽力解析出可播放的 HLS/MP4 直链，解析不出返回 null。
 * 顺序：`<video>` 标签 → 播放器配置字段 → 全页扫描。
 */
internal fun extractMissavStreamUrl(html: String): String? {
    if (html.isBlank()) return null
    val flat = html.replace("\\/", "/")

    val doc = Jsoup.parse(flat)
    val domSrc = doc.select("video[src]").firstOrNull()?.attr("src")
        ?: doc.select("video source[src]").firstOrNull()?.attr("src")
    normalizeStreamUrl(domSrc)?.takeIf { isPlayableStreamUrl(it) }?.let { return it }

    for (key in STREAM_KEYS) {
        val match = Regex(
            """["']?$key["']?\s*[:=]\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""",
            RegexOption.IGNORE_CASE
        ).find(flat)
        normalizeStreamUrl(match?.groupValues?.getOrNull(1))?.let { return it }
    }

    return STREAM_URL_RE.findAll(flat)
        .mapNotNull { normalizeStreamUrl(it.value) }
        .firstOrNull()
}
