package io.github.javcinema.ui.navigation

import coil.imageLoader
import coil.request.ImageRequest
import io.github.javcinema.JavCinema
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
import io.github.javcinema.ui.screen.ActressListScreen
import io.github.javcinema.ui.screen.DownloadScreen
import io.github.javcinema.ui.screen.FavouritesScreen
import io.github.javcinema.ui.screen.GalleryScreen
import io.github.javcinema.ui.screen.GenreListScreen
import io.github.javcinema.ui.screen.HomePagerScreen
import io.github.javcinema.ui.screen.MovieDetailScreen
import io.github.javcinema.ui.screen.MovieListScreen
import io.github.javcinema.ui.screen.SearchScreen
import io.github.javcinema.ui.screen.SettingsScreen
import io.github.javcinema.ui.screen.WebViewScreen
import java.net.URLDecoder

@Composable
fun JavCinemaNavHost(
    navController: NavHostController,
    scrollToTopTrigger: Long = 0L,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.HOME,
        modifier = modifier
    ) {
        composable(route = NavRoutes.HOME) {
            HomePagerScreen(navController = navController, scrollToTopTrigger = scrollToTopTrigger)
        }

        composable(route = NavRoutes.ACTRESSES) {
            ActressListScreen(navController = navController, scrollToTopTrigger = scrollToTopTrigger)
        }

        composable(route = NavRoutes.GENRE) {
            GenreListScreen(navController = navController)
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
            val title = URLDecoder.decode(backStackEntry.arguments?.getString("title") ?: "", "UTF-8")
            val url = URLDecoder.decode(backStackEntry.arguments?.getString("url") ?: "", "UTF-8")
            MovieListScreen(navController = navController, title = title, url = url, scrollToTopTrigger = scrollToTopTrigger)
        }

        composable(
            route = "search?query={query}",
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
            val encodedUrl = backStackEntry.arguments?.getString("url") ?: ""
            val url = URLDecoder.decode(encodedUrl, "UTF-8")
            WebViewScreen(
                url = url,
                onBack = { navController.popBackStack() }
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
