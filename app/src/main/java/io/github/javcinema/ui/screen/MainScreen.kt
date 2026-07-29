package io.github.javcinema.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.javcinema.ui.navigation.JAViewerNavHost
import io.github.javcinema.ui.navigation.NavRoutes

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

private val bottomItems = listOf(
    BottomNavItem("影片", Icons.Default.VideoLibrary, NavRoutes.HOME),
    BottomNavItem("女优", Icons.Default.Person, NavRoutes.ACTRESSES),
    BottomNavItem("类别", Icons.Default.Category, NavRoutes.GENRE),
    BottomNavItem("收藏", Icons.Default.Favorite, NavRoutes.FAVOURITE),
    BottomNavItem("设置", Icons.Default.Settings, NavRoutes.SETTINGS)
)

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val currentRoute = currentDestination?.route?.substringBefore("?") ?: NavRoutes.HOME

    val currentItem = bottomItems.find { item ->
        currentDestination?.hierarchy?.any { it.route?.startsWith(item.route) == true } == true
    } ?: bottomItems[0]

    val scrollToTopTrigger = remember { mutableLongStateOf(0L) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier.height(72.dp)
            ) {
                bottomItems.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route ||
                            currentDestination?.hierarchy?.any { it.route?.startsWith(item.route) == true } == true,
                        onClick = {
                            if (currentRoute == item.route) {
                                scrollToTopTrigger.longValue = System.nanoTime()
                            } else {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(22.dp)) },
                        label = { Text(item.label, style = MaterialTheme.typography.bodySmall) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    )
                }
            }
        }
    ) { paddingValues ->
        val isMovieDetail = currentRoute.startsWith("movie_detail")
        val layoutDirection = LocalLayoutDirection.current
        val adjustedPadding = PaddingValues(
            top = paddingValues.calculateTopPadding(),
            start = paddingValues.calculateLeftPadding(layoutDirection),
            end = paddingValues.calculateRightPadding(layoutDirection),
            bottom = if (isMovieDetail) 0.dp else paddingValues.calculateBottomPadding()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(adjustedPadding)
        ) {
            JAViewerNavHost(navController = navController, scrollToTopTrigger = scrollToTopTrigger.longValue)
        }
    }
}
