package io.github.javcinema.network.provider

import io.github.javcinema.data.model.DownloadLink
import io.github.javcinema.data.model.MagnetLink
import io.github.javcinema.network.TorrentKitty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.jsoup.Jsoup

class TorrentKittyLinkProvider : DownloadLinkProvider() {

    override suspend fun search(keyword: String, page: Int): ResponseBody? {
        return if (page == 1) {
            TorrentKitty.INSTANCE.search(keyword)
        } else {
            TorrentKitty.INSTANCE.searchPage(keyword, page)
        }
    }

    override suspend fun parseDownloadLinks(htmlContent: String): List<DownloadLink> = withContext(Dispatchers.IO) {
        val table = Jsoup.parse(htmlContent).getElementById("archiveResult")!!
        table.getElementsByTag("tr").mapNotNull { tr ->
            try {
                DownloadLink.create(
                    tr.getElementsByClass("name").first()!!.text(),
                    "",
                    tr.getElementsByClass("date").first()!!.text(),
                    null,
                    tr.getElementsByAttributeValue("rel", "magnet").first()!!.attr("href")
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    override suspend fun get(url: String): ResponseBody? {
        return null
        //ABANDONED
    }

    override suspend fun parseMagnetLink(htmlContent: String): MagnetLink? {
        return null
        //ABANDONED
    }
}
