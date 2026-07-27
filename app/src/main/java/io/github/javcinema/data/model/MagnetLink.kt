package io.github.javcinema.data.model

import java.io.Serializable

/**
 * Project: JAViewer
 */
class MagnetLink : Serializable {

    var magnetLink: String? = null

    companion object {
        fun create(magnetLinkStr: String?): MagnetLink {
            val magnet = MagnetLink()
            if (magnetLinkStr != null) {
                magnet.magnetLink = magnetLinkStr.substring(0, magnetLinkStr.indexOf("&"))
            }
            return magnet
        }
    }
}
