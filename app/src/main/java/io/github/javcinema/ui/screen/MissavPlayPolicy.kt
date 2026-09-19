package io.github.javcinema.ui.screen

import com.google.gson.JsonParser
import java.net.URI
import java.net.URLEncoder
import org.jsoup.Jsoup

internal data class MissavSearchResult(
    val url: String,
    val title: String,
    val duration: String? = null,
    val badge: String? = null,
    val thumbnailUrl: String? = null
)

internal fun normalizeMissavCode(code: String): String =
    code.trim().lowercase().replace(" ", "")

internal fun missavSearchUrl(code: String): String {
    val query = code.trim()
    val encoded = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
    return "https://missav.ws/en/search/$encoded"
}

internal fun isMissavSearchUrl(url: String?): Boolean {
    val path = urlPath(url) ?: return false
    return path.contains("/search/")
}

/**
 * 人机验证页的**强**特征。
 *
 * 判据只取验证插页自身独有的标记，不要用 "cdn-cgi/challenge" / "cf-challenge" /
 * "challenges.cloudflare.com" 这类子串：Cloudflare 的 JS Detections 会把
 * `/cdn-cgi/challenge-platform/scripts/jsd/main.js` 注入到**每一个正常页面**里，
 * 拿它当判据会把已通过验证的正常搜索结果页误判成验证页，
 * 于是自动接管被静默跳过、直接掉进回退分支（表现为「明明搜到了结果却不播」）。
 */
private val CHALLENGE_HTML_MARKERS = listOf(
    "正在进行安全验证",
    "just a moment",
    "challenge-running",
    "challenge-form",
    "cf-turnstile",
    "chl_page"
)

internal fun isMissavChallengeHtml(html: String): Boolean {
    val lower = html.lowercase()
    return CHALLENGE_HTML_MARKERS.any { it in lower }
}

internal fun isMissavChallengeTitle(title: String?): Boolean {
    val text = title.orEmpty()
    val lower = text.lowercase()
    return "请稍候" in text ||
        "安全验证" in text ||
        "just a moment" in lower ||
        text.equals("missav.ws", ignoreCase = true)
}

internal fun isMissavChallengeUrl(url: String?): Boolean {
    val text = url.orEmpty().lowercase()
    return "__cf_chl" in text ||
        "cdn-cgi/challenge" in text ||
        "challenges.cloudflare.com" in text
}

// 允许的站内域名表：missav 换镜像是常事，加新镜像改这里即可。
internal val MISSAV_SITE_HOSTS = listOf("missav.ws", "missav.com")
internal val CLOUDFLARE_HOSTS = listOf("cloudflare.com")

internal fun missavHost(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return try {
        URI(url).host?.lowercase()?.removePrefix("www.")
    } catch (_: Exception) {
        null
    }
}

internal fun isMissavSiteUrl(url: String?): Boolean {
    val host = missavHost(url) ?: return false
    return MISSAV_SITE_HOSTS.any { host == it || host.endsWith(".$it") }
}

internal fun isCloudflareUrl(url: String?): Boolean {
    val host = missavHost(url) ?: return false
    return CLOUDFLARE_HOSTS.any { host == it || host.endsWith(".$it") }
}

internal const val MISSAV_CLEAN_PLAY_JS = """
(function(){
  function junk(el) {
    if (!el || !el.tagName) return false;
    if (el.querySelector && el.querySelector('video')) return false;
    var id = (el.id || '').toLowerCase();
    var cls = (el.className || '').toString().toLowerCase();
    var tag = el.tagName.toLowerCase();
    if (tag === 'video' || tag === 'source') return false;
    if (tag === 'iframe') {
      var src = (el.getAttribute('src') || '').toLowerCase();
      return src.indexOf('missav') < 0 && src.indexOf('cloudflare') < 0;
    }
    return cls.indexOf('ad-') >= 0 || cls.indexOf('ads') >= 0 || cls.indexOf('popup') >= 0 ||
      cls.indexOf('banner') >= 0 || cls.indexOf('recommend') >= 0 || cls.indexOf('related') >= 0 ||
      cls.indexOf('watch-next') >= 0 || tag === 'nav' || tag === 'footer' || tag === 'aside';
  }
  function hideJunk() {
    Array.prototype.slice.call(document.querySelectorAll('iframe, aside, nav, footer')).forEach(function(el){
      if (!junk(el)) return;
      el.style.setProperty('display','none','important');
      el.style.setProperty('pointer-events','none','important');
    });
  }
  hideJunk();
  if (!window.__javcinemaCleanObs) {
    window.__javcinemaCleanObs = true;
    new MutationObserver(hideJunk).observe(document.documentElement, {childList:true, subtree:true});
  }
})();
"""

internal fun isAllowedMissavNavigation(url: String?, mainFrame: Boolean = true): Boolean {
    if (url.isNullOrBlank()) return false
    val lower = url.lowercase()
    if (lower.startsWith("about:") || lower.startsWith("blob:") || lower.startsWith("data:")) {
        return true
    }
    // 主帧只放行站内与 Cloudflare 人机验证。注意：isMissavChallengeUrl 是子串匹配，
    // 不能单独作为放行条件，否则 evil.com?__cf_chl=1 也能绕过。
    if (isMissavSiteUrl(url) || isCloudflareUrl(url)) {
        return true
    }
    if (!mainFrame) return true
    return false
}

internal fun isMissavPlayUrl(url: String, code: String): Boolean {
    val needle = normalizeMissavCode(code)
    if (needle.isEmpty()) return false
    val path = urlPath(url)?.lowercase() ?: return false
    if ("/search/" in path || "/actresses" in path || "/genres" in path || "/makers" in path) {
        return false
    }
    val slug = urlSlug(path).orEmpty()
    if (slug.isEmpty() || slug == "en" || slug == needle) {
        return slug == needle
    }
    return slug == needle || slug.startsWith("$needle-")
}

/** URL 路径的最后一段（slug），统一小写；取不到或为空返回 null。 */
internal fun missavSlug(url: String?): String? = urlSlug(urlPath(url))

private fun urlSlug(path: String?): String? =
    path?.lowercase()?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }

/**
 * 从搜索结果里挑一条用于自动接管。
 *
 * 能走到这里的候选都已通过 [isMissavPlayUrl] 校验（slug 要么等于番号，要么以「番号-」开头），
 * 所以只需在「精确命中」和「站点排序」之间取舍：
 * 优先 slug 与番号完全一致的那条（番号本体，通常是正片），没有则取站点排序第一条。
 */
internal fun selectBestMissavResult(
    results: List<MissavSearchResult>,
    code: String
): MissavSearchResult? {
    if (results.isEmpty()) return null
    val needle = normalizeMissavCode(code)
    if (needle.isEmpty()) return results.first()
    return results.firstOrNull { missavSlug(it.url) == needle } ?: results.first()
}

/**
 * 解析搜索结果页。只负责解析，不判人机验证 —— 是否验证页交给调用方在
 * 「解析不出候选」之后判断。顺序反过来会把正常页面误判成验证页（见 [isMissavChallengeHtml]）。
 */
internal fun parseMissavSearchResults(html: String, code: String): List<MissavSearchResult> {
    val doc = Jsoup.parse(html, "https://missav.ws")
    val grouped = linkedMapOf<String, MutableList<org.jsoup.nodes.Element>>()
    for (anchor in doc.select("a[href]")) {
        val raw = anchor.absUrl("href").ifBlank { anchor.attr("href") }
        val url = canonicalizeMissavUrl(raw) ?: continue
        if (!isMissavPlayUrl(url, code)) continue
        grouped.getOrPut(url) { mutableListOf() }.add(anchor)
    }
    return grouped.mapNotNull { (url, anchors) ->
        toMovieResult(url, anchors)
    }
}

private fun toMovieResult(url: String, anchors: List<org.jsoup.nodes.Element>): MissavSearchResult? {
    val texts = anchors.map { it.text().trim() }.filter { it.isNotEmpty() }
    val img = anchors.mapNotNull { it.selectFirst("img") }.firstOrNull()
    val imgAlt = img?.attr("alt")?.trim().orEmpty()
    val thumbnail = sequenceOf(
        img?.absUrl("data-src"),
        img?.attr("data-src"),
        img?.absUrl("src"),
        img?.attr("src")
    ).map { it?.trim().orEmpty() }.firstOrNull { it.startsWith("http") }
    val duration = texts.firstNotNullOfOrNull { DURATION_RE.find(it)?.value }
    val title = listOf(imgAlt, texts.firstOrNull { candidate ->
        candidate != duration &&
            !candidate.equals("Uncensored", ignoreCase = true) &&
            !candidate.equals("HD", ignoreCase = true) &&
            candidate.length > 8
    }).firstOrNull { !it.isNullOrBlank() } ?: url.substringAfterLast('/')
    if (isAdOrJunkTitle(title)) return null
    val joined = texts.joinToString(" ")
    val badge = when {
        url.contains("uncensored-leak", ignoreCase = true) ||
            joined.contains("Uncensored", ignoreCase = true) -> "无码"
        url.contains("chinese-subtitle", ignoreCase = true) ||
            joined.contains("Chinese", ignoreCase = true) -> "中字"
        else -> null
    }
    return MissavSearchResult(
        url = url,
        title = title,
        duration = duration,
        badge = badge,
        thumbnailUrl = thumbnail
    )
}

private fun isAdOrJunkTitle(title: String): Boolean {
    val lower = title.lowercase()
    return "live" in lower ||
        "webcam" in lower ||
        "myavlive" in lower ||
        lower == "uncensored" ||
        DURATION_RE.matches(title)
}

private val DURATION_RE = Regex("""\d+:\d{2}(?::\d{2})?""")

/**
 * 解开 `WebView.evaluateJavascript` 回传的 JSON 字符串字面量。
 *
 * ⚠️ 必须按 JSON 规范解**全部**转义，尤其是 `\uXXXX`：Android 会把结果里的
 * `<` `>` `&` `=` `'` 转义成 `\u003C` 这类 Unicode 转义（防止页面内容夹带标签）。
 * 只 replace `\n` / `\"` / `\/` 的话，拿到的 HTML 是满屏 `\u003Cdiv>` ——
 * Jsoup 一个标签都认不出来，搜索页永远解析出 0 条候选，自动接管被静默跳过
 * （现象是「明明搜到了结果却不播」，且直接掉进回退分支）。
 *
 * 交给 Gson 是因为它按规范处理全部转义（含 UTF-16 代理对），比手工 replace 链可靠。
 */
internal fun unescapeJsString(value: String?): String {
    if (value.isNullOrBlank() || value == "null") return ""
    return try {
        val parsed = JsonParser.parseString(value)
        if (parsed.isJsonPrimitive) parsed.asString else value
    } catch (_: Exception) {
        // evaluateJavascript 只会回传合法 JSON，走到这里说明是异常输入，原样返回。
        value
    }
}

private fun canonicalizeMissavUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("javascript:", ignoreCase = true)) {
        return null
    }
    val absolute = when {
        trimmed.startsWith("//") -> "https:$trimmed"
        trimmed.startsWith("/") -> "https://${MISSAV_SITE_HOSTS.first()}$trimmed"
        else -> trimmed
    }
    return try {
        val uri = URI(absolute)
        val host = uri.host?.lowercase() ?: return null
        if (MISSAV_SITE_HOSTS.none { host == it || host.endsWith(".$it") }) return null
        URI(uri.scheme, host, uri.path, null, null).toString().trimEnd('/')
    } catch (_: Exception) {
        null
    }
}

private fun urlPath(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return try {
        URI(url).path
    } catch (_: Exception) {
        url
    }
}
