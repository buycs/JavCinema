package io.github.javcinema.network.provider

import io.github.javcinema.data.model.DownloadLink
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

    override suspend fun parseDownloadLinks(htmlContent: String): List<DownloadLink> = withContext(Dispatchers.IO) {
        val document = Jsoup.parse(htmlContent)
        val rows = document.select("table.table-hover.file-list tbody tr")
        rows.mapNotNull { row ->
            try {
                val cells = row.select("td")
                if (cells.size < 3) return@mapNotNull null

                val titleCell = cells[0].select("a").first()
                val title = titleCell?.text() ?: return@mapNotNull null
                val href = titleCell?.attr("href") ?: return@mapNotNull null

                val size = cells[1].text()
                val date = cells[2].text()

                DownloadLink.create(
                    title,
                    size,
                    date,
                    href,
                    null
                )
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

    suspend fun parseDate(htmlContent: String): String = withContext(Dispatchers.IO) {
        val document = Jsoup.parse(htmlContent)
        document.select("dt:contains(\"发布日期\")").first()?.nextElementSibling()?.text() ?: ""
    }
}
