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

// 预先编译：原先在循环里逐个 new Regex，每次调用要重复编译 7 次。
private val STREAM_KEY_REGEXES: List<Regex> = STREAM_KEYS.map { key ->
    Regex(
        """["']?$key["']?\s*[:=]\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""",
        RegexOption.IGNORE_CASE
    )
}

/**
 * 广告 / 统计 / 播放器插件的流地址特征。
 *
 * 全页扫描是在整份 HTML 里抓第一个像流的 URL，页面上的贴片广告、埋点探针、
 * 预加载器都会产出 `.m3u8` / `.mp4`，不加过滤就会把广告当成正片直链交给播放器。
 */
private val AD_STREAM_MARKERS = listOf(
    "doubleclick", "googlesyndication", "googleadservices", "googletag",
    "adsystem", "adserver", "adservice", "/ads/", "/ad/", "ad_",
    "analytics", "tracker", "tracking", "telemetry", "beacon",
    "preroll", "midroll", "postroll", "vast", "vmap",
    "popads", "propellerads", "exoclick", "juicyads", "trafficjunky",
    "adnxs", "criteo", "taboola", "outbrain"
)

/** 是否是广告/统计类的流地址（仅用于全页扫描时的候选过滤）。 */
internal fun looksLikeAdStream(url: String): Boolean {
    if (url.isBlank()) return false
    val lower = url.lowercase()
    return AD_STREAM_MARKERS.any { lower.contains(it) }
}

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
 *
 * 只有最后一步（全页扫描）会过滤广告流：`<video>` 是播放器本体、配置字段是按键名精确匹配，
 * 两者可信度高；全页扫描是兜底，噪声最大，必须剔除广告/统计地址。
 */
internal fun extractMissavStreamUrl(html: String): String? {
    if (html.isBlank()) return null
    val flat = html.replace("\\/", "/")

    val doc = Jsoup.parse(flat)
    val domSrc = doc.select("video[src]").firstOrNull()?.attr("src")
        ?: doc.select("video source[src]").firstOrNull()?.attr("src")
    normalizeStreamUrl(domSrc)?.takeIf { isPlayableStreamUrl(it) }?.let { return it }

    for (regex in STREAM_KEY_REGEXES) {
        val match = regex.find(flat)
        normalizeStreamUrl(match?.groupValues?.getOrNull(1))?.let { return it }
    }

    return STREAM_URL_RE.findAll(flat)
        .mapNotNull { normalizeStreamUrl(it.value) }
        .firstOrNull { !looksLikeAdStream(it) }
}
