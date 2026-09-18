package io.github.javcinema.ui.screen

import io.github.javcinema.JavCinema
import okhttp3.Request

internal suspend fun probeSourceUrl(url: String): String? {
    val normalized = url.trim().trimEnd('/')
    if (normalized.isEmpty()) return null
    val client = JavCinema.HTTP_CLIENT.newBuilder()
        .followRedirects(true)
        .build()
    val candidates = listOf(normalized, "$normalized/")
    var lastError: String? = null
    for (target in candidates) {
        val request = Request.Builder()
            .url(target)
            .header("User-Agent", JavCinema.USER_AGENT)
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.code in 200..499) return null
                lastError = "探测失败 HTTP ${response.code}"
            }
        } catch (e: Exception) {
            lastError = e.message ?: "探测失败"
        }
    }
    return lastError
}
