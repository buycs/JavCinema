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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
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
import io.github.javcinema.data.model.Configurations
import io.github.javcinema.ui.navigation.JavCinemaNavHost
import io.github.javcinema.ui.navigation.NavRoutes

/**
 * 底部导航项。
 *
 * @param navigateRoute 点击时传给 [NavHostController.navigate] 的路径。
 *   必须是**不含占位符**的具体路径（如 "search"），否则会把 "{query}" 当字面量传进参数。
 * @param matchRoute 与导航目的地 route 比对用的字符串，即 `composable(route = ...)` 的原值。
 *   带可选参数的目的地形如 "search?query={query}"，直接拿去 navigate 是错的，
 *   所以要跟 [navigateRoute] 分开存放。
 */
data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val navigateRoute: String,
    val matchRoute: String = navigateRoute
)

private val bottomItems = listOf(
    BottomNavItem("影片", Icons.Default.VideoLibrary, NavRoutes.HOME),
    BottomNavItem("女优", Icons.Default.Person, NavRoutes.ACTRESSES),
    BottomNavItem(
        label = "搜索",
        icon = Icons.Default.Search,
        navigateRoute = NavRoutes.SEARCH,
        matchRoute = NavRoutes.SEARCH_ROUTE
    ),
    BottomNavItem("收藏", Icons.Default.Favorite, NavRoutes.FAVOURITE),
    BottomNavItem("设置", Icons.Default.Settings, NavRoutes.SETTINGS)
)

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val currentRoute = currentDestination?.route ?: bottomItems[0].matchRoute
    // 规则集中在 NavRoutes.isFullscreenRoute，避免又漏掉某个沉浸式页面。
    val hideBottomBar = NavRoutes.isFullscreenRoute(currentRoute)

    // 导航图未就绪时（currentDestination 为 null），高亮回退到「首页设置」选中的 tab，
    // 否则以搜索为首页启动的瞬间会错误高亮「影片」。
    val fallbackItem = bottomItems.find {
        it.matchRoute == NavRoutes.normalizeHomePage(Configurations.homePage)
    } ?: bottomItems[0]
    val currentItem = bottomItems.find { item ->
        currentDestination?.hierarchy?.any { it.route == item.matchRoute } == true
    } ?: fallbackItem

    val scrollToTopTrigger = remember { mutableLongStateOf(0L) }

    Scaffold(
        bottomBar = {
            if (!hideBottomBar) {
                NavigationBar(
                    modifier = Modifier.height(56.dp)
                ) {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.matchRoute ||
                                currentDestination?.hierarchy?.any { it.route == item.matchRoute } == true,
                            onClick = {
                                if (currentRoute == item.matchRoute) {
                                    scrollToTopTrigger.longValue = System.nanoTime()
                                } else {
                                    navController.navigate(item.navigateRoute) {
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
                            colors = NavigationBarItemDefaults.colors(
                                // 用主题容器色而非 primary 半透明，浅色下也足够可见。
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        val layoutDirection = LocalLayoutDirection.current
        val adjustedPadding = PaddingValues(
            top = paddingValues.calculateTopPadding(),
            start = paddingValues.calculateLeftPadding(layoutDirection),
            end = paddingValues.calculateRightPadding(layoutDirection),
            bottom = if (hideBottomBar) 0.dp else paddingValues.calculateBottomPadding()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(adjustedPadding)
        ) {
            JavCinemaNavHost(navController = navController, scrollToTopTrigger = scrollToTopTrigger.longValue)
        }
    }
}
