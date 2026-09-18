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
}
