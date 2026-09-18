package io.github.javcinema.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

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
        val stored = cookieStore[url.host] ?: return emptyList()
        synchronized(stored) {
            return stored.filter { it.matches(url) }
        }
    }
}
