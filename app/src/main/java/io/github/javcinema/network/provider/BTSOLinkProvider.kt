package io.github.javcinema.network.provider

import io.github.javcinema.data.model.DownloadLink
import io.github.javcinema.data.model.MagnetLink
import io.github.javcinema.network.BTSO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.jsoup.Jsoup

class BTSOLinkProvider : DownloadLinkProvider() {

    override suspend fun search(keyword: String, page: Int): ResponseBody {
        return BTSO.INSTANCE.search(keyword, page)
    }

    override suspend fun parseDownloadLinks(htmlContent: String): List<DownloadLink> = withContext(Dispatchers.IO) {
        val rows = Jsoup.parse(htmlContent).getElementsByClass("row")
        rows.mapNotNull { row ->
            try {
                val a = row.getElementsByTag("a").first()!!
                DownloadLink.create(
                    row.getElementsByClass("file").first()!!.text(),
                    row.getElementsByClass("size").first()!!.text(),
                    row.getElementsByClass("date").first()!!.text(),
                    a.attr("href"),
                    null
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    override suspend fun get(url: String): ResponseBody {
        return BTSO.INSTANCE.get(url)
    }

    override suspend fun parseMagnetLink(htmlContent: String): MagnetLink = withContext(Dispatchers.IO) {
        MagnetLink.create(Jsoup.parse(htmlContent).getElementsByClass("magnet-link").first()!!.text())
    }
}
