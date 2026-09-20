package io.github.javcinema.ui.navigation

import coil.imageLoader
import coil.request.ImageRequest
import io.github.javcinema.JavCinema
import io.github.javcinema.data.model.Configurations
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.javcinema.ui.screen.ActressDetailScreen
import io.github.javcinema.ui.screen.ActressGenrePagerScreen
import io.github.javcinema.ui.screen.DownloadScreen
import io.github.javcinema.ui.screen.FavouritesScreen
import io.github.javcinema.ui.screen.HomePagerScreen
import io.github.javcinema.ui.screen.MovieDetailScreen
import io.github.javcinema.ui.screen.MovieListScreen
import io.github.javcinema.ui.screen.SearchScreen
import io.github.javcinema.ui.screen.SettingsScreen
import io.github.javcinema.ui.screen.MissavPlayScreen
import io.github.javcinema.player.PlayerScreen

@Composable
fun JavCinemaNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    scrollToTopTrigger: Long = 0L
) {
    // 「首页设置」决定底部导航默认落在哪个 tab：HOME=影片，SEARCH_ROUTE=搜索。
    //
    // ⚠️ startDestination 必须与下面 composable() 注册的路由字符串**逐字一致**。
    // search 页注册的是 "search?query={query}"，若这里写 "search" 会匹配不到目的地，
    // NavHost 会静默回落到第一个 composable（即 HOME），
    // 表现为「选了搜索为首页，但启动后显示影片页」。
    // normalizeHomePage() 负责兜住历史版本存下的 "search"。
    val startDestination = NavRoutes.normalizeHomePage(Configurations.homePage)

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(route = NavRoutes.HOME) {
            HomePagerScreen(navController = navController, scrollToTopTrigger = scrollToTopTrigger)
        }

        composable(route = NavRoutes.ACTRESSES) {
            ActressGenrePagerScreen(navController = navController, scrollToTopTrigger = scrollToTopTrigger)
        }

        composable(route = NavRoutes.FAVOURITE) {
            FavouritesScreen(navController = navController, scrollToTopTrigger = scrollToTopTrigger)
        }

        composable(route = NavRoutes.SETTINGS) {
            SettingsScreen(navController = navController)
        }

        composable(
            route = NavRoutes.MOVIE_DETAIL,
            arguments = listOf(
                navArgument("movieCode") { type = NavType.StringType },
                navArgument("link") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("coverUrl") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val movieCode = backStackEntry.arguments?.getString("movieCode") ?: ""
            val movieLink = backStackEntry.arguments?.getString("link")
            val thumbnailUrl = backStackEntry.arguments?.getString("coverUrl")
            // 把封面先塞进图片内存缓存，让详情页打开时不用等网络。
            // ⚠️ 这里必须用 LaunchedEffect，不能用 remember：remember 的 lambda 会在
            // **组合期**执行，等于在组合里做副作用（Lint: RememberReturnType，
            // 因为块尾的 runCatching 返回 Unit 而被当成缓存值）。LaunchedEffect 才是
            // 正确工具，且以 thumbnailUrl 为 key —— 换了影片会重新预加载。
            LaunchedEffect(thumbnailUrl) {
                if (!thumbnailUrl.isNullOrBlank()) {
                    runCatching {
                        JavCinema.instance.imageLoader.enqueue(
                            ImageRequest.Builder(JavCinema.instance)
                                .data(thumbnailUrl)
                                .memoryCacheKey(thumbnailUrl)
                                .build()
                        )
                    }
                }
            }
            MovieDetailScreen(navController = navController, movieCode = movieCode, movieLink = movieLink, thumbnailUrl = thumbnailUrl)
        }

        composable(
            route = NavRoutes.MOVIE_LIST,
            arguments = listOf(
                navArgument("title") { type = NavType.StringType },
                navArgument("url") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val title = backStackEntry.arguments?.getString("title") ?: ""
            val url = backStackEntry.arguments?.getString("url") ?: ""
            MovieListScreen(navController = navController, title = title, url = url, scrollToTopTrigger = scrollToTopTrigger)
        }

        composable(
            route = NavRoutes.ACTRESS_DETAIL,
            arguments = listOf(
                navArgument("starId") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("imageUrl") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val starId = backStackEntry.arguments?.getString("starId") ?: ""
            val name = backStackEntry.arguments?.getString("name") ?: ""
            val imageUrl = backStackEntry.arguments?.getString("imageUrl")
            ActressDetailScreen(
                navController = navController,
                starId = starId,
                name = name,
                imageUrl = imageUrl,
                scrollToTopTrigger = scrollToTopTrigger
            )
        }

        composable(
            route = NavRoutes.SEARCH_ROUTE,
            arguments = listOf(navArgument("query") { defaultValue = "" })
        ) { backStackEntry ->
            val query = backStackEntry.arguments?.getString("query") ?: ""
            SearchScreen(navController = navController, initialQuery = query, scrollToTopTrigger = scrollToTopTrigger)
        }

        composable(
            route = NavRoutes.DOWNLOAD,
            arguments = listOf(navArgument("keyword") { type = NavType.StringType })
        ) { backStackEntry ->
            val keyword = backStackEntry.arguments?.getString("keyword") ?: ""
            DownloadScreen(keyword = keyword)
        }

        composable(
            route = NavRoutes.MISSAV_PLAY,
            arguments = listOf(navArgument("movieCode") { type = NavType.StringType })
        ) { backStackEntry ->
            val movieCode = backStackEntry.arguments?.getString("movieCode") ?: ""
            MissavPlayScreen(
                movieCode = movieCode,
                onBack = { navController.popBackStack() },
                onPlayStream = { streamUrl, referer ->
                    navController.navigate(NavRoutes.player(streamUrl, referer)) {
                        // 取流页只是自动接管的中间过程，交棒后必须移出回退栈：
                        // 否则从播放器返回时它会重新解析并再次自动跳转，形成死循环。
                        // 用 destination.id 而非路由字符串，避免带参数路由匹配不上。
                        popUpTo(backStackEntry.destination.id) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = NavRoutes.PLAYER,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType },
                navArgument("referer") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url") ?: ""
            val referer = backStackEntry.arguments?.getString("referer") ?: ""
            PlayerScreen(
                url = url,
                referer = referer,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
