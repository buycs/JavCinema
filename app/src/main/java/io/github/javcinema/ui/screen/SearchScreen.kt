package io.github.javcinema.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.github.javcinema.JAViewer
import io.github.javcinema.data.model.Movie
import io.github.javcinema.ui.components.MovieCard
import io.github.javcinema.ui.components.MovieFavoriteDialog
import io.github.javcinema.ui.components.SwipeBackContainer
import io.github.javcinema.ui.navigation.NavRoutes

@Composable
fun SearchScreen(
    navController: NavController,
    initialQuery: String = "",
    viewModel: SearchViewModel = viewModel(),
    scrollToTopTrigger: Long = 0L
) {
    val uiState by viewModel.uiState.collectAsState()
    val movies by viewModel.movies.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    var query by remember { mutableStateOf(initialQuery) }
    var dialogMovie by remember { mutableStateOf<Movie?>(null) }
    var savedIndex by rememberSaveable { mutableIntStateOf(0) }
    var savedOffset by rememberSaveable { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = savedIndex,
        initialFirstVisibleItemScrollOffset = savedOffset
    )

    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            gridState.animateScrollToItem(0)
        }
    }

    val dsVersionAtCreation = remember { JAViewer.dataSourceVersionFlow.value }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) {
            query = initialQuery
            viewModel.search(initialQuery)
        }
    }

    LaunchedEffect(JAViewer.dataSourceVersionFlow.value) {
        if (JAViewer.dataSourceVersionFlow.value != dsVersionAtCreation) {
            gridState.animateScrollToItem(0)
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !isLoadingMore && uiState is SearchUiState.Success) {
            viewModel.loadMore()
        }
    }

    LaunchedEffect(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset) {
        savedIndex = gridState.firstVisibleItemIndex
        savedOffset = gridState.firstVisibleItemScrollOffset
    }

    SwipeBackContainer(
        onBack = { navController.popBackStack() },
        modifier = Modifier
            .fillMaxSize()
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索影片...") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            when (uiState) {
                is SearchUiState.Idle -> {}
                is SearchUiState.Loading -> {
                    if (movies.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
                is SearchUiState.Error -> {}
                is SearchUiState.Success -> {}
            }

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
                        CircularProgressIndicator()
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
