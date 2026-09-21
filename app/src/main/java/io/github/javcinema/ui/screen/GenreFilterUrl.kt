package io.github.javcinema.ui.screen

/**
 * 类型过滤跳转用的 URL。
 *
 * 站点给的 link 有三种形态：裸 ID（`yjkojnz`）、相对路径（`genre/yjkojnz`）、绝对路径
 * （`/cn/genre/yjkojnz`）。影片列表页靠 URL 里是否含 `genre/` 判定过滤类型、取最后一段作
 * 过滤 ID，所以裸 ID 需要补上 `genre/` 前缀，带路径的保持原样。
 */
internal fun genreFilterUrl(link: String?): String? {
    val raw = link?.trim().orEmpty()
    if (raw.isEmpty()) return null
    return if (raw.contains('/')) raw else "genre/$raw"
}
