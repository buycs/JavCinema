package io.github.javcinema.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.github.javcinema.JavCinema
import io.github.javcinema.data.model.Configurations
import io.github.javcinema.data.model.Movie
import io.github.javcinema.ui.components.DataSourceChangeEffect
import io.github.javcinema.ui.components.MovieCard
import io.github.javcinema.ui.components.MovieFavoriteDialog
import io.github.javcinema.ui.navigation.NavRoutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    section: String,
    viewModel: HomeViewModel = viewModel(),
    scrollToTopTrigger: Long = 0L
) {
    val uiState by viewModel.uiState.collectAsState()
    val movies by viewModel.movies.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val uiPrefsVersion by JavCinema.uiPrefsVersionFlow.collectAsState()
    val gridColumns = if (uiPrefsVersion >= 0) Configurations.gridColumns.coerceIn(2, 4) else 3
    var savedIndex by rememberSaveable { mutableIntStateOf(0) }
    var savedOffset by rememberSaveable { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = savedIndex,
        initialFirstVisibleItemScrollOffset = savedOffset
    )
    var dialogMovie by remember { mutableStateOf<Movie?>(null) }

    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            gridState.animateScrollToItem(0)
        }
    }

    // 订阅数据源版本。组合期直接读 StateFlow.value 不会建立订阅（不产生快照读），
    // 所以必须 collectAsStateWithLifecycle 才能在切换数据源时真正触发重组。
    val dsVersion by JavCinema.dataSourceVersionFlow.collectAsStateWithLifecycle()

    LaunchedEffect(section, dsVersion) {
        viewModel.setSection(section)
    }

    // 切换数据源后回到顶部（详见 DataSourceChangeEffect 的注释）。
    DataSourceChangeEffect { gridState.animateScrollToItem(0) }

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !isLoadingMore && uiState is HomeUiState.Success) {
            viewModel.loadMore()
        }
    }

    // 持久化滚动位置。⚠️ 不能把 gridState.firstVisibleItemIndex / ScrollOffset 直接当
    // LaunchedEffect 的 key —— 那属于「组合期读频繁变化的状态」，滚动时每一帧都会
    // 触发整页重组，只为存两个 int（Lint: FrequentlyChangedStateReadInComposition）。
    // 改用 snapshotFlow：在快照观察里读值，只驱动这个协程，不引起重组。
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                savedIndex = index
                savedOffset = offset
            }
    }

    PullToRefreshBox(
        isRefreshing = uiState is HomeUiState.Loading && movies.isNotEmpty(),
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        when (uiState) {
            is HomeUiState.Loading -> {
                if (movies.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            is HomeUiState.Error -> {
                if (movies.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = (uiState as HomeUiState.Error).message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            is HomeUiState.Success -> {
                if (movies.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "暂无数据")
                    }
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            state = gridState,
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                items = movies,
                key = { it.code?.let { c -> it.link?.let { l -> "$c-$l" } ?: c } ?: it.hashCode().toString() }
            ) { movie ->
                MovieCard(
                    movie = movie,
                    onClick = {
                        navController.navigate(NavRoutes.movieDetail(movie.code ?: "", movie.link, movie.coverUrl))
                    },
                    onLongClick = { dialogMovie = movie }
                )
            }

            if (isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }
    }

    dialogMovie?.let { movie ->
        MovieFavoriteDialog(
            movie = movie,
            onDismiss = { dialogMovie = null }
        )
    }
}

