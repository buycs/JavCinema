package io.github.javcinema.network.provider

/**
 * Cloudflare Email Obfuscation（`data-cfemail`）解码。
 *
 * 无极磁链（cili.info）挂在 Cloudflare 后面，种子详情页的文件列表里，凡是
 * **长得像邮箱的文件名**都会被邮件保护脚本整段替换成占位文本：
 *
 * ```html
 * <td>roe-556/<a class="__cf_email__" data-cfemail="c5f1…">[email&#160;protected]</a></td>
 * <td class="td-size">6.13 GB</td>
 * ```
 *
 * 这段的真实文件名是 `4k2.me@roe-556.mp4` —— 因为前缀像邮箱，**连扩展名一起被吃掉**。
 * Jsoup 不执行 JS，只能读到 `[email protected]`，于是这个 6.13 GB 的正片在
 * `visibleMagnetFiles()` 里因为「没有媒体扩展名」被判为非媒体而隐藏，
 * 界面上只剩旁边 19 MB 的赠品片段，还会被 `largestVideoIndex()` 当成主文件加粗。
 *
 * 算法：首字节是密钥，其余每字节与它异或；结果按 UTF-8 解码。
 */
internal fun decodeCfEmail(hex: String?): String? {
    val value = hex.orEmpty().trim()
    if (value.length < 4 || value.length % 2 != 0) return null

    val raw = ByteArray(value.length / 2)
    for (i in raw.indices) {
        val hi = Character.digit(value[i * 2], 16)
        val lo = Character.digit(value[i * 2 + 1], 16)
        if (hi < 0 || lo < 0) return null
        raw[i] = ((hi shl 4) or lo).toByte()
    }

    val key = raw[0].toInt() and 0xFF
    val out = ByteArray(raw.size - 1)
    for (i in out.indices) {
        out[i] = ((raw[i + 1].toInt() and 0xFF) xor key).toByte()
    }
    return String(out, Charsets.UTF_8).takeIf { it.isNotBlank() }
}
