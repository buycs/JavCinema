package io.github.javcinema.data.model

import java.io.Serializable

/**
 * Project: JavCinema
 */
class MagnetLink : Serializable {

    var magnetLink: String? = null

    companion object {
        fun create(magnetLinkStr: String?): MagnetLink {
            val magnet = MagnetLink()
            if (magnetLinkStr != null) {
                val idx = magnetLinkStr.indexOf("&")
                magnet.magnetLink = if (idx >= 0) magnetLinkStr.substring(0, idx) else magnetLinkStr
            }
            return magnet
        }
    }
}
