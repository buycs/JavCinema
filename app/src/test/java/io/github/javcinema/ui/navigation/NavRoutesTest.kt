package io.github.javcinema.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavRoutesTest {

    @Test
    fun movieDetail_encodesSpecialCharacters() {
        val route = NavRoutes.movieDetail(
            movieCode = "ABC 123",
            link = "https://example.com/movie/a?b=1&c=2",
            coverUrl = "https://cdn.example.com/封面.jpg"
        )
        assertTrue(route.startsWith("movie_detail/"))
        assertFalse(route.contains(" "))
        assertTrue(route.contains("ABC%20123"))
        assertTrue(route.contains("link="))
        assertTrue(route.contains("coverUrl="))
        assertFalse(route.substringAfter("link=").substringBefore("&").contains("?"))
        assertFalse(route.substringAfter("link=").substringBefore("&").contains("&"))
        assertTrue(route.contains("%E5%B0%81%E9%9D%A2"))
    }

    @Test
    fun movieDetail_omitsBlankQueryParams() {
        val route = NavRoutes.movieDetail("SSIS-001", link = null, coverUrl = "")
        assertEquals("movie_detail/SSIS-001", route)
    }

    @Test
    fun movieList_encodesTitleAndUrl() {
        val route = NavRoutes.movieList("女优 A", "star/id with space")
        assertTrue(route.startsWith("movie_list/"))
        assertTrue(route.contains("%E5%A5%B3%E4%BC%98"))
        assertTrue(route.contains("%20"))
        assertFalse(route.contains("+"))
        val afterPrefix = route.removePrefix("movie_list/")
        assertEquals(2, afterPrefix.split("/").size)
    }

    @Test
    fun download_encodesKeyword() {
        val route = NavRoutes.download("ABC 123")
        assertTrue(route.startsWith("download/"))
        assertEquals("download/ABC%20123", route)
    }

    @Test
    fun actressDetail_encodesStarIdAndName() {
        val route = NavRoutes.actressDetail("kw by", "女优 A", "https://cdn.example.com/a.jpg")
        assertTrue(route.startsWith("actress_detail/"))
        assertTrue(route.contains("kw%20by"))
        assertTrue(route.contains("name="))
        assertTrue(route.contains("%E5%A5%B3%E4%BC%98"))
        assertFalse(route.contains(" "))
    }

    @Test
    fun player_encodesStreamUrlAndReferer() {
        val route = NavRoutes.player(
            "https://cdn.example.com/hls/a.m3u8?token=1&x=2",
            "https://missav.ws/en/pppe-443"
        )
        assertTrue(route.startsWith("player?url="))
        assertTrue(route.contains("&referer="))
        assertFalse(route.contains(" "))
        // 值必须整体转义，否则内层 ?/& 会把 route 的 query 截断。
        val urlValue = route.substringAfter("url=").substringBefore("&referer=")
        assertFalse(urlValue.contains("?"))
        assertFalse(urlValue.contains("&"))
        assertTrue(route.contains("%3A%2F%2F"))
    }

    @Test
    fun player_defaultsBlankReferer() {
        assertEquals("player?url=https%3A%2F%2Fcdn.example.com%2Fa.m3u8&referer=", NavRoutes.player("https://cdn.example.com/a.m3u8"))
    }

    // --- 首页设置 ---
    // 回归：startDestination 必须等于 composable() 注册的 route，
    // 而搜索页注册的是 SEARCH_ROUTE（带 query 参数），不是 SEARCH。

    @Test
    fun searchRoute_isTheRegisteredRouteNotTheBareName() {
        // 这条断言是本次 bug 的核心：两者不能相等。
        assertFalse(NavRoutes.SEARCH_ROUTE == NavRoutes.SEARCH)
        assertEquals("search?query={query}", NavRoutes.SEARCH_ROUTE)
        assertEquals("search", NavRoutes.SEARCH)
    }

    @Test
    fun normalizeHomePage_mapsLegacySearchToRegisteredRoute() {
        // 历史版本存过 "search"，必须纠正成注册 route，否则 NavHost 匹配失败回落 HOME。
        assertEquals(NavRoutes.SEARCH_ROUTE, NavRoutes.normalizeHomePage(NavRoutes.SEARCH))
        assertEquals(NavRoutes.SEARCH_ROUTE, NavRoutes.normalizeHomePage(NavRoutes.SEARCH_ROUTE))
    }

    @Test
    fun normalizeHomePage_keepsHome() {
        assertEquals(NavRoutes.HOME, NavRoutes.normalizeHomePage(NavRoutes.HOME))
    }

    @Test
    fun normalizeHomePage_defaultsToSearchForNullOrUnknown() {
        assertEquals(NavRoutes.SEARCH_ROUTE, NavRoutes.normalizeHomePage(null))
        assertEquals(NavRoutes.SEARCH_ROUTE, NavRoutes.normalizeHomePage(""))
        assertEquals(NavRoutes.SEARCH_ROUTE, NavRoutes.normalizeHomePage("garbage"))
    }

    @Test
    fun normalizeHomePage_resultIsAlwaysARegisteredRoute() {
        // 归一化结果必须落在注册 route 集合里，否则会出现「选完启动还是影片页」。
        val registered = setOf(NavRoutes.HOME, NavRoutes.ACTRESSES, NavRoutes.FAVOURITE, NavRoutes.SETTINGS, NavRoutes.SEARCH_ROUTE)
        listOf(null, "", NavRoutes.HOME, NavRoutes.SEARCH, NavRoutes.SEARCH_ROUTE, "unknown").forEach { input ->
            assertTrue("input=$input", NavRoutes.normalizeHomePage(input) in registered)
        }
    }

    @Test
    fun homePageOptions_searchComesFirstAsDefault() {
        // 默认项（列表第一个）就是搜索为首页。
        assertEquals(NavRoutes.SEARCH_ROUTE, NavRoutes.HOME_PAGE_OPTIONS.first().first)
        assertEquals(NavRoutes.DEFAULT_HOME_PAGE, NavRoutes.HOME_PAGE_OPTIONS.first().first)
        assertEquals(2, NavRoutes.HOME_PAGE_OPTIONS.size)
    }

    @Test
    fun navigateTarget_neverContainsPlaceholders() {
        // 回归：设置页保存后要 navigate 回首页，曾经把 normalizeHomePage 的结果
        // （"search?query={query}"）直接传进 navigate()，`{query}` 被当成字面量填进
        // query 参数 —— 搜索框里出现 "{query}" 并真的拿它去搜。
        // 可导航的路径绝不能带 "{...}" 占位符。
        listOf(null, "", NavRoutes.HOME, NavRoutes.SEARCH, NavRoutes.SEARCH_ROUTE, "garbage")
            .forEach { input ->
                val target = NavRoutes.navigateTarget(input)
                assertFalse("input=$input target=$target", target.contains("{"))
                assertFalse("input=$input target=$target", target.contains("}"))
            }
    }

    @Test
    fun navigateTarget_mapsSearchToBareName() {
        // navigate() 由 NavController 自己拼 android-app://... URI，可选参数可省略，
        // 所以这里必须是裸名 "search"，而不是注册原值。
        assertEquals(NavRoutes.SEARCH, NavRoutes.navigateTarget(NavRoutes.SEARCH_ROUTE))
        assertEquals(NavRoutes.SEARCH, NavRoutes.navigateTarget(NavRoutes.SEARCH))
        assertEquals(NavRoutes.SEARCH, NavRoutes.navigateTarget(null))
        assertEquals(NavRoutes.HOME, NavRoutes.navigateTarget(NavRoutes.HOME))
    }

    @Test
    fun navigateTarget_differsFromNormalizeHomePageForSearch() {
        // 两者对搜索页必须给出**不同**结果：一个给模板（startDestination 用），
        // 一个给具体路径（navigate 用）。若哪天有人「统一」成一个函数，这条会红。
        assertNotEquals(
            NavRoutes.normalizeHomePage(NavRoutes.SEARCH_ROUTE),
            NavRoutes.navigateTarget(NavRoutes.SEARCH_ROUTE)
        )
        // 影片页没有参数，两者可以相同。
        assertEquals(
            NavRoutes.normalizeHomePage(NavRoutes.HOME),
            NavRoutes.navigateTarget(NavRoutes.HOME)
        )
    }

    @Test
    fun homePageLabel_returnsReadableText() {
        assertEquals("搜索为首页", NavRoutes.homePageLabel(NavRoutes.SEARCH_ROUTE))
        assertEquals("搜索为首页", NavRoutes.homePageLabel(NavRoutes.SEARCH))
        assertEquals("搜索为首页", NavRoutes.homePageLabel(null))
        assertEquals("影片为首页", NavRoutes.homePageLabel(NavRoutes.HOME))
    }

    // --- 全屏路由（隐藏底部导航栏）---

    /**
     * 回归：播放页当初漏登记，导致横屏播放时底部还挂着一条导航栏。
     * 三个沉浸式页面一个都不能少。
     */
    @Test
    fun fullscreenRoute_coversEveryImmersivePage() {
        assertTrue(NavRoutes.isFullscreenRoute("movie_detail/SSIS-001"))
        assertTrue(NavRoutes.isFullscreenRoute("missav_play/SSIS-001"))
        assertTrue(NavRoutes.isFullscreenRoute("player"))
        // 实际导航用的是带 query 的完整路径，也要判成全屏
        assertTrue(
            NavRoutes.isFullscreenRoute(
                NavRoutes.player("https://surrit.com/a/playlist.m3u8", "https://missav.ws/en/x")
            )
        )
        // 带参数的目的地，前缀必须取自 route 模板本身而不是硬编码
        assertTrue(NavRoutes.isFullscreenRoute(NavRoutes.movieDetail("SSIS-001", link = "https://a/b")))
    }

    @Test
    fun fullscreenRoute_leavesBottomBarPagesAlone() {
        // 底部栏自己的五个页面绝不能被误判成全屏，否则导航栏会消失、用户被困住。
        listOf(
            NavRoutes.HOME,
            NavRoutes.ACTRESSES,
            NavRoutes.SEARCH,
            NavRoutes.SEARCH_ROUTE,
            NavRoutes.FAVOURITE,
            NavRoutes.SETTINGS,
            "actress_detail/123",
            "movie_list/x/y",
            "download/abc"
        ).forEach { route ->
            assertFalse("route=$route", NavRoutes.isFullscreenRoute(route))
        }
        // 导航图未就绪时不能崩
        assertFalse(NavRoutes.isFullscreenRoute(null))
        assertFalse(NavRoutes.isFullscreenRoute(""))
    }
}
