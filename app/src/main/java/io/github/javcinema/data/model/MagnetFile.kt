package io.github.javcinema.data.model

import java.io.Serializable
import java.net.URLEncoder

class MagnetFile : Serializable {
    var hash: String = ""
    var torrentName: String = ""
    var filename: String = ""
    var size: Long = 0

    val magnetLink: String
        get() {
            val dn = try {
                URLEncoder.encode(torrentName, "UTF-8")
            } catch (_: Exception) {
                torrentName
            }
            return "magnet:?xt=urn:btih:$hash&dn=$dn"
        }
}
