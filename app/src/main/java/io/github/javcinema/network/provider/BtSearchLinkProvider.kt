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

    /**
     * 取种子详情。
     *
     * ⚠️ **不要**把异常吞成 `null`：调用方拿到 `null` 会走到「未获取到文件列表」，
     * 那是把**网络故障**说成「站点没给文件列表」—— 与 missav 那边「把网络故障说成未收录」
     * 属于同一类**错误结论**（用户看到「站点没给」会以为换个种子才行，实际重试就能好）。
     * 异常应当一路抛到 `DownloadViewModel` 的 catch，那里会显示「加载失败」。
     */
    suspend fun getDetail(id: String, keyword: String): BtSearchDetailResponse =
        BtSearch.INSTANCE.getDetail(id, keyword)

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
