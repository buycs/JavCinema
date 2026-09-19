package io.github.javcinema.ui.navigation

import java.net.URLEncoder

object NavRoutes {
    const val HOME = "home"
    const val POPULAR = "popular"
    const val RELEASED = "released"
    const val ACTRESSES = "actresses"
    const val FAVOURITE = "favourite"
    const val MOVIE_DETAIL = "movie_detail/{movieCode}?link={link}&coverUrl={coverUrl}"
    const val MOVIE_LIST = "movie_list/{title}/{url}"
    const val ACTRESS_DETAIL = "actress_detail/{starId}?name={name}&imageUrl={imageUrl}"
    const val GALLERY = "gallery/{index}"
    const val DOWNLOAD = "download/{keyword}"
    const val SEARCH = "search"

    /**
     * 搜索页在导航图里注册的完整路由（带可选 query 参数）。
     *
     * ⚠️ 用于 [androidx.navigation.compose.NavHost] 的 `composable(route = ...)`
     * 以及 `startDestination`。它们必须传同一个字符串，否则 NavHost 匹配不到目的地，
     * 会静默回落到第一个注册的 composable。
     */
    const val SEARCH_ROUTE = "search?query={query}"

    /**
     * 「首页设置」的可选值 —— 直接复用导航图里注册的 route，
     * 这样 `startDestination`、底部栏高亮、设置页读写三处天然一致。
     *
     * 之所以不用 [HOME]/[SEARCH]（"home"/"search"）作为存储值：
     * startDestination 与 `composable(route=)` 必须逐字相同，
     * 而搜索页注册的是 [SEARCH_ROUTE]，存 "search" 会导致 NavHost 匹配失败。
     */
    val HOME_PAGE_OPTIONS: List<Pair<String, String>> = listOf(
        SEARCH_ROUTE to "搜索为首页",
        HOME to "影片为首页"
    )

    /** 首页设置的默认值：搜索为首页。 */
    const val DEFAULT_HOME_PAGE = SEARCH_ROUTE

    /**
     * 把持久化的「首页设置」值归一化成导航图里注册的 route。
     *
     * ⚠️ 这个函数的返回值会直接传给 `NavHost(startDestination = ...)` 和
     * `navController.navigate(...)`，必须与 `composable(route = ...)` 逐字一致。
     *
     * 为什么要归一化：历史版本曾把 "search"（[SEARCH]）存进偏好设置，
     * 而搜索页注册的 route 是 [SEARCH_ROUTE]（"search?query={query}"）。
     * 直接使用 "search" 会导致 NavHost 匹配不到目的地，
     * 静默回落到第一个注册的 composable（HOME），
     * 表现为「选了搜索为首页，启动后却显示影片页」。
     */
    fun normalizeHomePage(stored: String?): String = when (stored) {
        HOME -> HOME
        SEARCH, SEARCH_ROUTE -> SEARCH_ROUTE
        else -> DEFAULT_HOME_PAGE
    }

    /** 首页设置值对应的中文说明，用于设置页 summary。 */
    fun homePageLabel(stored: String?): String {
        val normalized = normalizeHomePage(stored)
        return HOME_PAGE_OPTIONS.firstOrNull { it.first == normalized }?.second
            ?: HOME_PAGE_OPTIONS.first().second
    }
    const val SETTINGS = "settings"
    const val WEBVIEW = "webview/{url}"
    const val MISSAV_PLAY = "missav_play/{movieCode}"
    const val PLAYER = "player?url={url}&referer={referer}"

    private fun encodePath(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    fun movieDetail(movieCode: String, link: String? = null, coverUrl: String? = null): String {
        val base = "movie_detail/${encodePath(movieCode)}"
        val parts = mutableListOf<String>()
        if (!link.isNullOrBlank()) parts.add("link=${encodePath(link)}")
        if (!coverUrl.isNullOrBlank()) parts.add("coverUrl=${encodePath(coverUrl)}")
        return if (parts.isEmpty()) base else "$base?${parts.joinToString("&")}"
    }

    fun movieList(title: String, url: String) = "movie_list/${encodePath(title)}/${encodePath(url)}"
    fun actressDetail(starId: String, name: String? = null, imageUrl: String? = null): String {
        val base = "actress_detail/${encodePath(starId)}"
        val parts = mutableListOf<String>()
        if (!name.isNullOrBlank()) parts.add("name=${encodePath(name)}")
        if (!imageUrl.isNullOrBlank()) parts.add("imageUrl=${encodePath(imageUrl)}")
        return if (parts.isEmpty()) base else "$base?${parts.joinToString("&")}"
    }
    fun gallery(index: Int) = "gallery/$index"
    fun download(keyword: String) = "download/${encodePath(keyword)}"
    fun webview(url: String) = "webview/${encodePath(url)}"
    fun missavPlay(movieCode: String) = "missav_play/${encodePath(movieCode)}"
    fun player(url: String, referer: String = "") =
        "player?url=${encodePath(url)}&referer=${encodePath(referer)}"

    /**
     * 注册为「全屏」的目的地：底部导航栏在这些页面上必须隐藏。
     *
     * 这些页面要么需要整块画面（详情页大图、取流页 WebView），要么是全屏横屏播放器 ——
     * 底下挂一条导航栏既挡内容，横屏时还会被拉成奇怪的比例。
     *
     * **新增沉浸式页面时必须在这里登记。** 播放页当初就是因为漏登记，
     * 导致横屏播放时底部仍挂着导航栏（`MainScreen.hideBottomBar` 只判了前两个）。
     */
    private val FULLSCREEN_ROUTES = listOf(MOVIE_DETAIL, MISSAV_PLAY, PLAYER)
        .map { it.substringBefore("/{").substringBefore("?") }

    /** 当前路由是否属于 [FULLSCREEN_ROUTES]。route 为 null（导航图未就绪）时返回 false。 */
    fun isFullscreenRoute(route: String?): Boolean {
        val value = route ?: return false
        return FULLSCREEN_ROUTES.any { value.startsWith(it) }
    }
}
