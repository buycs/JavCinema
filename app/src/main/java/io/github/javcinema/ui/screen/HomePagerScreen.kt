package io.github.javcinema.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.github.javcinema.JAViewer
import io.github.javcinema.ui.navigation.NavRoutes
import kotlinx.coroutines.launch

@Composable
fun HomePagerScreen(navController: NavController, scrollToTopTrigger: Long = 0L) {
    data class TabInfo(val label: String, val icon: ImageVector, val section: String)
    val tabs = listOf(
        TabInfo("热门", Icons.Default.LocalFireDepartment, "popular"),
        TabInfo("全部", Icons.Default.GridView, "home"),
        TabInfo("发行", Icons.Default.NewReleases, "released")
    )
    val sections = tabs.map { it.section }
    val pagerState = rememberPagerState(pageCount = { 3 }, initialPage = 1)
    val scope = rememberCoroutineScope()

    val dsVersionAtCreation = remember { JAViewer.dataSourceVersionFlow.value }

    LaunchedEffect(JAViewer.dataSourceVersionFlow.value) {
        if (JAViewer.dataSourceVersionFlow.value != dsVersionAtCreation) {
            pagerState.animateScrollToPage(1)
        }
    }

    val popularViewModel: HomeViewModel = viewModel(key = "popular")
    val homeViewModel: HomeViewModel = viewModel(key = "home")
    val releasedViewModel: HomeViewModel = viewModel(key = "released")
    val viewModels = listOf(popularViewModel, homeViewModel, releasedViewModel)

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            divider = {},
            modifier = Modifier.height(38.dp)
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                tab.icon,
                                contentDescription = null,
                                modifier = Modifier
                                    .alpha(if (pagerState.currentPage == index) 1f else 0.7f)
                                    .height(16.dp)
                            )
                            Text(tab.label, fontSize = 15.sp)
                        }
                    },
                    selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                    unselectedContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 2,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                HomeScreen(
                    navController = navController,
                    section = sections[page],
                    viewModel = viewModels[page],
                    scrollToTopTrigger = scrollToTopTrigger
                )
            }

            IconButton(
                onClick = { navController.navigate(NavRoutes.SEARCH) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .alpha(0.5f)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "搜索",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
