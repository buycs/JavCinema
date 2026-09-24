package io.github.javcinema.ui.navigation

import java.net.URLEncoder

object NavRoutes {
    const val HOME = "home"
    const val ACTRESSES = "actresses"
    const val FAVOURITE = "favourite"
    const val MOVIE_DETAIL = "movie_detail/{movieCode}?link={link}&coverUrl={coverUrl}"
    const val MOVIE_LIST = "movie_list/{title}/{url}"
    const val ACTRESS_DETAIL = "actress_detail/{starId}?name={name}&imageUrl={imageUrl}"
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
     * 把持久化的「首页设置」值归一化成导航图里注册的 route **原值**。
     *
     * ⚠️ 这个返回值**只能给 `NavHost(startDestination = ...)` 用**，不能传给
     * `navController.navigate(...)` —— 需要 navigate 时请用 [navigateTarget]。
     * 两者不能混用，这里踩过坑，详见 [navigateTarget] 的注释。
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

    /**
     * 把「首页设置」的值转成**可以安全传给 `navController.navigate(...)` 的具体路径**。
     *
     * ## 为什么不能直接用 [normalizeHomePage] 的返回值去 navigate
     *
     * 搜索页注册的 route 是带**可选参数**的 [SEARCH_ROUTE]（`"search?query={query}"`）。
     * 把这一整串传给 `navigate()` 时，`{query}` 会被当成**字面量**塞进 `query` 参数 ——
     * 表现为「保存设置回到首页后，搜索框里赫然写着 `{query}`，并且真的拿它去搜」。
     *
     * ## 为什么 startDestination 反而必须用带占位符的原值
     *
     * `NavHost` 解析 `startDestination` 时内部走的是 `Uri.parse(startDestination)` 再匹配
     * 深链，`"search"` 这种没有 scheme/authority 的裸字符串匹配不上（会静默回落到
     * 第一个 composable）；而 `NavController.navigate()` 会自己拼出
     * `android-app://androidx.navigation/search` 再匹配，可选参数（`query` 有默认值）
     * 可以省略，所以 `"search"` 能正常命中。
     *
     * 一句话：**startDestination 要模板，navigate 要具体路径。**
     * 底部导航栏的 `BottomNavItem.navigateRoute` / `matchRoute` 分两个字段，也是同一个道理。
     */
    fun navigateTarget(stored: String?): String = when (normalizeHomePage(stored)) {
        SEARCH_ROUTE -> SEARCH
        else -> HOME
    }

    /** 首页设置值对应的中文说明，用于设置页 summary。 */
    fun homePageLabel(stored: String?): String {
        val normalized = normalizeHomePage(stored)
        return HOME_PAGE_OPTIONS.firstOrNull { it.first == normalized }?.second
            ?: HOME_PAGE_OPTIONS.first().second
    }
    const val SETTINGS = "settings"
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
    fun download(keyword: String) = "download/${encodePath(keyword)}"
    fun missavPlay(movieCode: String) = "missav_play/${encodePath(movieCode)}"
    fun player(url: String, referer: String = "") =
        "player?url=${encodePath(url)}&referer=${encodePath(referer)}"

    /**
     * **显示**底部导航栏的目的地 —— 只有这五个底部功能页。
     *
     * 反过来用白名单而不是给「全屏页」登记黑名单，是因为后者的失败方式很难看：
     * 新增一个子页忘了登记，底栏就会挂在一个本不该有底栏的页面上，而且
     * `MainScreen` 的左右滑动还会继续生效 —— 用户看到的是「手势乱跑」，
     * 而不是「少登记了一行」。
     *
     * 历史事故：播放页当初漏登记，横屏播放时底部仍挂着导航栏。
     * 现在换成白名单，新增子页天然就是对的。
     */
    private val BOTTOM_TAB_ROUTES = listOf(HOME, ACTRESSES, FAVOURITE, SEARCH, SETTINGS)

    /**
     * 当前路由是否显示底部导航栏（同时也决定是否响应「左右滑动切底栏」）。
     *
     * 其余一切路由（影片详情、过滤结果、女优详情、磁力结果、取流、播放…）都是
     * 「从底栏跳出去」的页面：底栏隐藏，底栏的左右滑动/点击一律不响应。
     * 那些页面自己的手势（顶部功能页的 `HorizontalPager`、返回手势）照常工作。
     *
     * route 为 null（导航图未就绪）时返回 `true` —— 启动瞬间宁可先显示，
     * 也不要闪一下（`MainScreen` 此时会用「首页设置」的 tab 兜底）。
     */
    fun showsBottomBar(route: String?): Boolean {
        val value = route.orEmpty()
        // 导航图未就绪（null / 空串）时先显示 —— 启动瞬间宁可先画出来，也不要闪一下。
        if (value.isEmpty()) return true
        return BOTTOM_TAB_ROUTES.any { value.startsWith(it) }
    }
}
