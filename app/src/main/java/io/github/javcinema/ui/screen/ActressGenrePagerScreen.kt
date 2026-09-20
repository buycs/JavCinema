package io.github.javcinema.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.javcinema.JavCinema
import io.github.javcinema.ui.components.AppTopTabRow
import io.github.javcinema.ui.components.DataSourceChangeEffect
import io.github.javcinema.ui.components.TopBarSelectedContentColor
import io.github.javcinema.ui.components.TopBarUnselectedContentColor
import kotlinx.coroutines.launch

@Composable
fun ActressGenrePagerScreen(navController: NavController, scrollToTopTrigger: Long = 0L) {
    val tabs = listOf("女优", "类别")
    val pagerState = rememberPagerState(pageCount = { tabs.size }, initialPage = 0)
    val scope = rememberCoroutineScope()

    // 切换数据源后回到第一页（详见 DataSourceChangeEffect 的注释）。
    DataSourceChangeEffect { pagerState.animateScrollToPage(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopTabRow(selectedIndex = pagerState.currentPage) {
            tabs.forEachIndexed { index, label ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(label, fontSize = 15.sp) },
                    selectedContentColor = TopBarSelectedContentColor,
                    unselectedContentColor = TopBarUnselectedContentColor
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 0,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> ActressListScreen(
                    navController = navController,
                    scrollToTopTrigger = scrollToTopTrigger
                )
                else -> GenreListScreen(navController = navController)
            }
        }
    }
}
