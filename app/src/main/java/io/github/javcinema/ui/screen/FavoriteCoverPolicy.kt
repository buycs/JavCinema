package io.github.javcinema.ui.screen

/**
 * 收藏封面该存哪一张。
 *
 * 收藏页复用 [io.github.javcinema.ui.components.MovieCard]，卡片按 147x200 裁切显示，存大图等于
 * 每次进收藏页白下载一份封面；更麻烦的是列表/搜索长按存的是小图、详情页长按存的是大图，
 * 两个入口存的东西不一样。所以详情页这条入口统一走「小封面优先」。
 *
 * 入参都是调用方已经取好的 URL，本函数不碰注册表也不碰状态，方便单测。
 *
 * @param thumbnailUrl 进详情页时导航参数带的列表缩略图
 * @param registeredSmallCoverUrl 数据源按 movieId 记着的 `posterSmall`（HTML 源恒为 null）
 * @param detailCoverUrl 详情页解析出来的大封面（`bigImage` / `posterLarge`）
 */
internal fun favoriteCoverUrl(
    thumbnailUrl: String?,
    registeredSmallCoverUrl: String?,
    detailCoverUrl: String?
): String? {
    // 从收藏页再进详情时，缩略图就是当初存下去的那张 —— 老数据可能是大图，不能无条件采信。
    if (thumbnailUrl != null && !thumbnailUrl.endsWith(LARGE_COVER_SUFFIX)) return thumbnailUrl
    if (!registeredSmallCoverUrl.isNullOrBlank()) return registeredSmallCoverUrl
    // 站点图片按 `ps.jpg` / `pl.jpg` 成对出现，手里只有大的就反推小的。
    val large = thumbnailUrl ?: detailCoverUrl
    if (large != null && large.endsWith(LARGE_COVER_SUFFIX)) {
        return large.substring(0, large.length - LARGE_COVER_SUFFIX.length) + SMALL_COVER_SUFFIX
    }
    return large ?: detailCoverUrl
}

private const val LARGE_COVER_SUFFIX = "pl.jpg"
private const val SMALL_COVER_SUFFIX = "ps.jpg"
