package io.github.javcinema.network.provider

import io.github.javcinema.data.model.DownloadLink
import io.github.javcinema.data.model.MagnetLink
import okhttp3.ResponseBody

abstract class DownloadLinkProvider {

    companion object {
        fun getProvider(name: String): DownloadLinkProvider? {
            return when (name.lowercase().trim()) {
                "btso" -> BTSOLinkProvider()
                "torrentkitty" -> TorrentKittyLinkProvider()
                "ciliinfo" -> CiliInfoLinkProvider()
                "cili" -> CiliInfoLinkProvider()
                "btsearch" -> BtSearchLinkProvider()
                else -> null
            }
        }
    }

    abstract suspend fun search(keyword: String, page: Int): ResponseBody?

    abstract suspend fun parseDownloadLinks(htmlContent: String): List<DownloadLink>

    abstract suspend fun get(url: String): ResponseBody?

    abstract suspend fun parseMagnetLink(htmlContent: String): MagnetLink?
}
