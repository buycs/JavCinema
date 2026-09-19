package io.github.javcinema.network.provider

import io.github.javcinema.data.model.DownloadLink
import io.github.javcinema.data.model.MagnetFile
import io.github.javcinema.data.model.MagnetLink
import io.github.javcinema.network.BtSearch
import io.github.javcinema.network.BtSearchDetailResponse
import io.github.javcinema.network.BtSearchTorrentFile
import io.github.javcinema.util.stripHtmlTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody

class BtSearchLinkProvider : DownloadLinkProvider() {

    override suspend fun search(keyword: String, page: Int): ResponseBody? = null

    override suspend fun parseDownloadLinks(htmlContent: String): List<DownloadLink> = emptyList()

    suspend fun searchApi(keyword: String, page: Int): List<DownloadLink> = withContext(Dispatchers.IO) {
        val offset = (page - 1) * 10
        val result = BtSearch.INSTANCE.search(keyword, limit = 10, offset = offset)
        result.data.map { item ->
            DownloadLink().apply {
                link = item.id.toString()
                title = stripHtmlTags(item.name)
                size = formatSize(item.size.toLongOrNull() ?: 0L)
                date = item.created_at.take(10)
                magnetLink = MagnetLink.create("magnet:?xt=urn:btih:${item.hash}")
            }
        }
    }

    suspend fun getDetail(id: String, keyword: String): BtSearchDetailResponse? {
        return try {
            BtSearch.INSTANCE.getDetail(id, keyword)
        } catch (_: Exception) {
            null
        }
    }

    fun parseFilesFromTorrentFiles(files: List<BtSearchTorrentFile>): List<MagnetFile> {
        return files.map { file ->
            MagnetFile().apply {
                filename = file.name
                size = file.size.toLongOrNull() ?: 0L
            }
        }
    }

    override suspend fun get(url: String): ResponseBody? = null

    override suspend fun parseMagnetLink(htmlContent: String): MagnetLink? = null

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
}
