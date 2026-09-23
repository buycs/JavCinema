package io.github.javcinema.ui.screen

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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
import kotlin.math.abs

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
    val currentIndex = bottomItems.indexOf(currentItem).coerceAtLeast(0)

    val scrollToTopTrigger = remember { mutableLongStateOf(0L) }

    // 搜索 / 设置页没有顶部功能页 —— 上半屏的滑动要退化成切底栏（见 effectiveSwipeZone）。
    val pageHasTopTabs = hasTopPages(currentRoute)

    /** 底部功能页之间跳转的统一入口：点底栏、左右滑动都走它，参数只有一处。 */
    fun openBottomItem(item: BottomNavItem) {
        navController.navigate(item.navigateRoute) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    // 手势回调要读最新的 currentIndex，但 pointerInput 的 key 里不能放它 ——
    // 否则每次切页都会重启手势检测、把正在进行的拖动打断。
    val onBottomSwipe: (SwipeDirection) -> Unit = { direction ->
        swipeTargetIndex(currentIndex, direction, bottomItems.size)?.let { target ->
            openBottomItem(bottomItems[target])
        }
    }
    val latestOnBottomSwipe = rememberUpdatedState(onBottomSwipe)

    // 同上：这个值会随切页变化，但也不能进 pointerInput 的 key ——
    // 否则从「影片」切到「搜索」时会重启手势检测，正好把刚开始的那次拖动丢掉。
    val latestHasTopTabs = rememberUpdatedState(pageHasTopTabs)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(hideBottomBar) {
                // 详情 / 取流 / 播放这类沉浸页没有底部导航，滑动切页只会让人迷路。
                if (hideBottomBar) return@pointerInput
                val slop = viewConfiguration.touchSlop
                awaitPointerEventScope {
                    while (true) {
                        // ⚠️ 必须在 Initial 阶段监听：每个底部功能页自己的顶部 HorizontalPager
                        // 铺满整个内容区（上下半屏都算），按常规顺序它会先把下半屏的横向拖动吃掉。
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial
                        )
                        // 上半屏的滑动归顶部功能页，这里直接放行给子级；
                        // 但页面根本没有顶部功能页时（搜索/设置），上半屏也归切底栏。
                        val zone = effectiveSwipeZone(
                            y = down.position.y,
                            height = size.height.toFloat(),
                            hasTopPages = latestHasTopTabs.value
                        )
                        if (zone != SwipeZone.LOWER) {
                            continue
                        }
                        var dragX = 0f
                        var dragY = 0f
                        var claimed = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val delta = change.positionChange()
                            dragX += delta.x
                            dragY += delta.y
                            if (!claimed) {
                                when {
                                    // 横向位移先越过阈值且明显大于纵向 → 这是翻页手势，抢下来。
                                    abs(dragX) > slop && abs(dragX) > abs(dragY) -> claimed = true
                                    // 纵向先越过阈值 → 是滚动列表，放手交还给子级。
                                    abs(dragY) > slop -> break
                                }
                            }
                            if (claimed) change.consume()
                        }
                        if (claimed) {
                            latestOnBottomSwipe.value(
                                decideSwipe(
                                    dragX = dragX,
                                    triggerPx = swipeTriggerPx(size.width.toFloat(), density)
                                )
                            )
                        }
                    }
                }
            }
    ) {
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
                                        openBottomItem(item)
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
}
