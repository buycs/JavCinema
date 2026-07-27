package io.github.javcinema.data.model

import java.net.URLEncoder

class TorrentGroup {
    var hash: String = ""
    var torrentName: String = ""
    var totalSize: Long = 0
    var expanded: Boolean = false
    val files: MutableList<MagnetFile> = mutableListOf()

    val magnetLink: String
        get() {
            return try {
                val dn = URLEncoder.encode(torrentName, "UTF-8").replace("+", "%20")
                "magnet:?xt=urn:btih:$hash&dn=$dn"
            } catch (_: Exception) {
                "magnet:?xt=urn:btih:$hash"
            }
        }
}
