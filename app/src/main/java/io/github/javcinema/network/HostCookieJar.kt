package io.github.javcinema.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * 按 host 分桶的内存 Cookie 仓库。
 *
 * 注意：Cookie 的适用性由 [Cookie.matches] 依 domain/path/secure 判定，与「用哪个 host 做键」无关。
 * 因此 [loadForRequest] 必须遍历**全部**分桶，否则 `.example.com` 这类父域 Cookie
 * 在请求子域时取不到（反之亦然），会导致会话 Cookie 间歇失效。
 */
class HostCookieJar : CookieJar {
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val stored = cookieStore.getOrPut(url.host) { mutableListOf() }
        synchronized(stored) {
            cookies.forEach { incoming ->
                stored.removeAll {
                    it.name == incoming.name && it.domain == incoming.domain && it.path == incoming.path
                }
                stored.add(incoming)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val result = mutableListOf<Cookie>()
        cookieStore.values.forEach { bucket ->
            synchronized(bucket) {
                result += bucket.filter { it.matches(url) && it.expiresAt > now }
            }
        }
        return result
    }
}
