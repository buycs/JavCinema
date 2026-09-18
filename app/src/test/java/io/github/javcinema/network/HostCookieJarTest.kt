package io.github.javcinema.network

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HostCookieJarTest {

    @Test
    fun sameHostDifferentPath_sharesCookie() {
        val jar = HostCookieJar()
        val first = "https://example.com/a".toHttpUrl()
        val second = "https://example.com/b".toHttpUrl()
        jar.saveFromResponse(
            first,
            listOf(
                Cookie.Builder()
                    .name("sid")
                    .value("1")
                    .domain("example.com")
                    .path("/")
                    .build()
            )
        )
        val loaded = jar.loadForRequest(second)
        assertEquals(1, loaded.size)
        assertEquals("sid", loaded[0].name)
        assertEquals("1", loaded[0].value)
    }

    @Test
    fun differentHosts_areIsolated() {
        val jar = HostCookieJar()
        jar.saveFromResponse(
            "https://example.com/".toHttpUrl(),
            listOf(
                Cookie.Builder()
                    .name("sid")
                    .value("1")
                    .domain("example.com")
                    .path("/")
                    .build()
            )
        )
        assertTrue(jar.loadForRequest("https://other.com/".toHttpUrl()).isEmpty())
    }

    // 回归：父域 Cookie 必须对子域可用（此前按 host 单层索引会导致会话 Cookie 丢失）。
    @Test
    fun parentDomainCookie_isSentToSubdomain() {
        val jar = HostCookieJar()
        jar.saveFromResponse(
            "https://example.com/".toHttpUrl(),
            listOf(
                Cookie.Builder()
                    .name("sid")
                    .value("1")
                    .domain("example.com")
                    .path("/")
                    .build()
            )
        )
        val loaded = jar.loadForRequest("https://cdn.example.com/".toHttpUrl())
        assertEquals(1, loaded.size)
        assertEquals("sid", loaded[0].name)
    }

    // 回归：子域写入的 Cookie 不应外泄到父域，也不应反向泄漏到兄弟子域。
    @Test
    fun subdomainCookie_isNotLeakedToParentOrSibling() {
        val jar = HostCookieJar()
        jar.saveFromResponse(
            "https://cdn.example.com/".toHttpUrl(),
            listOf(
                Cookie.Builder()
                    .name("token")
                    .value("x")
                    .hostOnlyDomain("cdn.example.com")
                    .path("/")
                    .build()
            )
        )
        assertTrue(jar.loadForRequest("https://example.com/".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://img.example.com/".toHttpUrl()).isEmpty())
        assertEquals(1, jar.loadForRequest("https://cdn.example.com/".toHttpUrl()).size)
    }

    // 回归：过期 Cookie 不应回发。
    @Test
    fun expiredCookie_isNotSent() {
        val jar = HostCookieJar()
        jar.saveFromResponse(
            "https://example.com/".toHttpUrl(),
            listOf(
                Cookie.Builder()
                    .name("old")
                    .value("1")
                    .domain("example.com")
                    .path("/")
                    .expiresAt(System.currentTimeMillis() - 60_000)
                    .build()
            )
        )
        assertTrue(jar.loadForRequest("https://example.com/".toHttpUrl()).isEmpty())
    }

    // 回归：同 name/domain/path 重复写入应覆盖而非堆积。
    @Test
    fun sameKey_overwritesInsteadOfAccumulating() {
        val jar = HostCookieJar()
        val url = "https://example.com/".toHttpUrl()
        fun cookie(value: String) = Cookie.Builder()
            .name("sid").value(value).domain("example.com").path("/").build()

        jar.saveFromResponse(url, listOf(cookie("v1")))
        jar.saveFromResponse(url, listOf(cookie("v2")))

        val loaded = jar.loadForRequest(url)
        assertEquals(1, loaded.size)
        assertEquals("v2", loaded[0].value)
    }
}
