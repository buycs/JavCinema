package io.github.javcinema.data.model

import java.io.Serializable
import java.net.URLDecoder

/**
 * Project: JavCinema
 */
class MagnetLink : Serializable {

    var magnetLink: String? = null

    companion object {
        /**
         * 规整磁力串：只裁掉空白，**保留全部参数**。
         *
         * 这里原来截到第一个 `&` 为止，等于把 `&tr=`（tracker 列表）和 `&dn=` 一起丢了。
         * 对第三方工具只是少几个 tracker，但对本机 BT 引擎是致命的：没有 tracker 就只能靠
         * DHT 碰运气，热门资源的命中率会掉一个数量级。所以只清洗、不截断。
         *
         * 清洗规则：站点偶尔回 `&amp;`（HTML 实体没解）或整串被 URL 编码过，
         * 这两种都会让引擎 `parse_magnet_uri` 直接失败。
         */
        fun create(magnetLinkStr: String?): MagnetLink {
            val magnet = MagnetLink()
            if (magnetLinkStr != null) {
                magnet.magnetLink = normalize(magnetLinkStr)
            }
            return magnet
        }

        private fun normalize(raw: String): String {
            var value = raw.trim()
            if (value.isEmpty()) return value
            // 没解码的 HTML 实体
            value = value.replace("&amp;", "&")
            // 整串被编码过（`magnet%3A%3Fxt%3D...`）—— 只在没有明文 `magnet:?` 前缀时才解，
            // 否则会误伤本来就合法的 `&dn=` 里的百分号转义。
            if (!value.startsWith("magnet:?") && value.contains("magnet%3A")) {
                value = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrElse { value }
            }
            return value
        }
    }
}
