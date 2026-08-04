package io.github.javcinema.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.javcinema.JavCinema
import kotlinx.coroutines.launch

@Composable
fun ActressGenrePagerScreen(navController: NavController, scrollToTopTrigger: Long = 0L) {
    val tabs = listOf("女优", "类别")
    val pagerState = rememberPagerState(pageCount = { tabs.size }, initialPage = 0)
    val scope = rememberCoroutineScope()

    val dsVersionAtCreation = remember { JavCinema.dataSourceVersionFlow.value }

    LaunchedEffect(JavCinema.dataSourceVersionFlow.value) {
        if (JavCinema.dataSourceVersionFlow.value != dsVersionAtCreation) {
            pagerState.animateScrollToPage(0)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            divider = {},
            modifier = Modifier.height(38.dp)
        ) {
            tabs.forEachIndexed { index, label ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(label, fontSize = 15.sp) },
                    selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                    unselectedContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
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
