package io.github.javcinema.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
    BottomNavItem("演员", Icons.Default.Person, NavRoutes.ACTRESSES),
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

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomItems.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route ||
                            currentDestination?.hierarchy?.any { it.route?.startsWith(item.route) == true } == true,
                        onClick = {
                            if (currentRoute != item.route) {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = false
                                    }
                                    launchSingleTop = true
                                }
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            JAViewerNavHost(navController = navController)
        }
    }
}
