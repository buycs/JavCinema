package io.github.javcinema.network.provider

import com.google.gson.Gson
import io.github.javcinema.data.model.DownloadLink
import io.github.javcinema.data.model.MagnetFile
import io.github.javcinema.data.model.MagnetLink
import io.github.javcinema.network.BTSO
import io.github.javcinema.util.stripHtmlTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody

class BTSOLinkProvider : DownloadLinkProvider() {

    override suspend fun search(keyword: String, page: Int): ResponseBody? = null

    override suspend fun parseDownloadLinks(htmlContent: String): List<DownloadLink> = emptyList()

    override suspend fun get(url: String): ResponseBody? = null

    override suspend fun parseMagnetLink(htmlContent: String): MagnetLink? = null

    suspend fun searchApi(keyword: String, page: Int): List<DownloadLink> = withContext(Dispatchers.IO) {
        val json = Gson().toJson(listOf(mapOf("search" to keyword), 30, page))
        val body = json.toRequestBody("application/json".toMediaType())
        val response = BTSO.INSTANCE.search(body)
        response.data.map { item ->
            DownloadLink().apply {
                link = item.hash
                title = stripHtmlTags(item.name)
                size = formatSize(item.size)
                date = formatTimestamp(item.lastUpdateTime)
                magnetLink = MagnetLink.create("magnet:?xt=urn:btih:${item.hash}")
            }
        }
    }

    suspend fun getMagnetDetail(hash: String): List<MagnetFile> = withContext(Dispatchers.IO) {
        try {
            val body = Gson().toJson(listOf(hash)).toRequestBody("application/json".toMediaType())
            val response = BTSO.INSTANCE.getMagnet(body)
            response.data?.files?.map { file ->
                MagnetFile().apply {
                    filename = file.filename
                    size = file.size
                }
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun formatSize(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unitIndex = 0
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        return "%.2f %s".format(size, units[unitIndex])
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp * 1000))
    }
}
