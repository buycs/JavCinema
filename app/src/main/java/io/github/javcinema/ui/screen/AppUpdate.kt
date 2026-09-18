package io.github.javcinema.ui.screen

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

const val UPDATE_REPO_OWNER = "buycs"
const val UPDATE_REPO_NAME = "JavCinema"
const val UPDATE_RELEASES_API = "https://api.github.com/repos/$UPDATE_REPO_OWNER/$UPDATE_REPO_NAME/releases/latest"
const val UPDATE_RELEASES_PAGE = "https://github.com/$UPDATE_REPO_OWNER/$UPDATE_REPO_NAME/releases/latest"

// 独立 client：不复用 JavCinema.HTTP_CLIENT，避免数据源 hostReplacements 改写 api.github.com。
internal val UPDATE_HTTP_CLIENT: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
}

data class ReleaseInfo(
    val tag: String = "",
    val name: String = "",
    val htmlUrl: String = "",
    val body: String = ""
)

internal fun normalizeVersionParts(version: String): List<Int> {
    val cleaned = version.trim().removePrefix("v").removePrefix("V")
    if (cleaned.isEmpty()) return emptyList()
    return cleaned.split('.', '-', '_', '+').mapNotNull { part ->
        part.takeWhile { it.isDigit() }.takeIf { it.isNotEmpty() }?.toIntOrNull()
    }
}

internal fun isNewerVersion(latestTag: String, currentName: String): Boolean {
    val latest = normalizeVersionParts(latestTag)
    val current = normalizeVersionParts(currentName)
    if (latest.isEmpty() || current.isEmpty()) return false
    val size = maxOf(latest.size, current.size)
    for (i in 0 until size) {
        val l = latest.getOrElse(i) { 0 }
        val c = current.getOrElse(i) { 0 }
        if (l != c) return l > c
    }
    return false
}

internal fun parseReleaseJson(json: String): ReleaseInfo {
    val obj = JsonParser.parseString(json).asJsonObject
    fun str(key: String): String =
        if (obj.has(key) && !obj.get(key).isJsonNull) obj.get(key).asString else ""
    return ReleaseInfo(
        tag = str("tag_name"),
        name = str("name"),
        htmlUrl = str("html_url"),
        body = str("body")
    )
}

internal suspend fun fetchLatestRelease(): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
    runCatching {
        val request = Request.Builder()
            .url(UPDATE_RELEASES_API)
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()
        UPDATE_HTTP_CLIENT.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val msg = when (response.code) {
                    403 -> "GitHub 限流稍后再试"
                    404 -> "暂无正式 release"
                    else -> "HTTP ${response.code}"
                }
                throw IllegalStateException(msg)
            }
            parseReleaseJson(body)
        }
    }
}
