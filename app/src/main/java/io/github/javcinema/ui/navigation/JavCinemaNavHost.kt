package io.github.javcinema.ui.navigation

import coil.imageLoader
import coil.request.ImageRequest
import io.github.javcinema.JavCinema
import io.github.javcinema.data.model.Configurations
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import io.github.javcinema.ui.screen.GalleryScreen
import io.github.javcinema.ui.screen.HomePagerScreen
import io.github.javcinema.ui.screen.MovieDetailScreen
import io.github.javcinema.ui.screen.MovieListScreen
import io.github.javcinema.ui.screen.SearchScreen
import io.github.javcinema.ui.screen.SettingsScreen
import io.github.javcinema.ui.screen.MissavPlayScreen
import io.github.javcinema.ui.screen.WebViewScreen
import io.github.javcinema.player.PlayerScreen

@Composable
fun JavCinemaNavHost(
    navController: NavHostController,
    scrollToTopTrigger: Long = 0L,
    modifier: Modifier = Modifier
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
            remember {
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
            route = NavRoutes.GALLERY,
            arguments = listOf(navArgument("index") { type = NavType.IntType })
        ) { backStackEntry ->
            val index = backStackEntry.arguments?.getInt("index") ?: 0
            GalleryScreen(initialIndex = index, onClose = { navController.popBackStack() })
        }

        composable(
            route = NavRoutes.WEBVIEW,
            arguments = listOf(navArgument("url") { type = NavType.StringType })
        ) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url") ?: ""
            WebViewScreen(
                url = url,
                onBack = { navController.popBackStack() }
            )
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
                    navController.navigate(NavRoutes.player(streamUrl, referer))
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

@Composable
private fun PlaceholderScreen(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text)
    }
}
