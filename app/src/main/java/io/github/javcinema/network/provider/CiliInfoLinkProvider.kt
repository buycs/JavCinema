package io.github.javcinema.network.provider

import io.github.javcinema.data.model.DownloadLink
import io.github.javcinema.data.model.MagnetFile
import io.github.javcinema.data.model.MagnetLink
import io.github.javcinema.network.CiliInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.jsoup.Jsoup

class CiliInfoLinkProvider : DownloadLinkProvider() {

    override suspend fun search(keyword: String, page: Int): ResponseBody {
        return CiliInfo.INSTANCE.search(keyword)
    }

    override suspend fun parseDownloadLinks(htmlContent: String): List<DownloadLink> =
        withContext(Dispatchers.IO) { parseSearchResults(htmlContent) }

    /**
     * 搜索结果解析的纯逻辑（不切线程），便于单测直接调用。
     *
     * ⚠️ 无极的搜索结果行**只有 2 个 `<td>`**：标题 + 一个 meta 单元格，
     * 体积和日期都在 meta 里（见 [parseMagnetMeta]）。所以不能按
     * 「第 2 列体积、第 3 列日期」取值 —— 那样 `size` 会拿到
     * `"2.02GB 2025-10-28"` 这样一串粘连文本，而 `date` 恒为空串。
     * 这里把第 2 列起的文本合并后统一解析，2 列 / 3 列结构都能正确处理。
     */
    internal fun parseSearchResults(htmlContent: String): List<DownloadLink> {
        val document = Jsoup.parse(htmlContent)
        val rows = document.select("table.table-hover.file-list tbody tr")
        return rows.mapNotNull { row ->
            try {
                val cells = row.select("td")
                if (cells.size < 2) return@mapNotNull null

                val titleCell = cells[0].select("a").first() ?: return@mapNotNull null
                val title = titleCell.text()
                val href = titleCell.attr("href")
                if (title.isBlank() || href.isBlank()) return@mapNotNull null

                val meta = parseMagnetMeta(cells.drop(1).joinToString(" ") { it.text() })

                DownloadLink.create(title, meta.size, meta.date, href, null)
            } catch (_: Exception) {
                null
            }
        }
    }

    override suspend fun get(url: String): ResponseBody {
        // URL might be a relative path like /!lBfm
        val path = if (url.startsWith("http")) {
            url.removePrefix("https://cili.info")
        } else {
            url
        }
        return CiliInfo.INSTANCE.get(path.removePrefix("/"))
    }

    override suspend fun parseMagnetLink(htmlContent: String): MagnetLink = withContext(Dispatchers.IO) {
        val document = Jsoup.parse(htmlContent)
        val magnetInput = document.getElementById("input-magnet")
        val magnetText = magnetInput?.attr("value") ?: magnetInput?.text() ?: ""
        MagnetLink.create(magnetText)
    }

    suspend fun parseFiles(htmlContent: String): List<MagnetFile> = withContext(Dispatchers.IO) {
        val document = Jsoup.parse(htmlContent)
        val rows = document.select("table.table-hover.file-list tbody tr")
        rows.mapNotNull { row ->
            try {
                val cells = row.select("td")
                if (cells.isEmpty()) return@mapNotNull null
                MagnetFile().apply {
                    filename = cells[0].text().trim()
                    val lastCell = cells.last() ?: return@mapNotNull null
                    size = parseSize(lastCell.text())
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun parseSize(sizeStr: String): Long {
        return try {
            val parts = sizeStr.trim().split(" ")
            if (parts.size < 2) 0L
            else {
                val value = parts[0].toDoubleOrNull() ?: 0.0
                when (parts[1].uppercase()) {
                    "KB" -> (value * 1024).toLong()
                    "MB" -> (value * 1024 * 1024).toLong()
                    "GB" -> (value * 1024 * 1024 * 1024).toLong()
                    "TB" -> (value * 1024 * 1024 * 1024 * 1024).toLong()
                    else -> value.toLong()
                }
            }
        } catch (_: Exception) {
            0L
        }
    }
}
