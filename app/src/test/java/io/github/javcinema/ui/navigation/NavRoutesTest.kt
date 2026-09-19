package io.github.javcinema.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun homePageLabel_returnsReadableText() {
        assertEquals("搜索为首页", NavRoutes.homePageLabel(NavRoutes.SEARCH_ROUTE))
        assertEquals("搜索为首页", NavRoutes.homePageLabel(NavRoutes.SEARCH))
        assertEquals("搜索为首页", NavRoutes.homePageLabel(null))
        assertEquals("影片为首页", NavRoutes.homePageLabel(NavRoutes.HOME))
    }
}
