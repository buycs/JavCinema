package io.github.javcinema.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import io.github.javcinema.data.model.Actress
import io.github.javcinema.ui.components.ActressFavoriteDialog
import io.github.javcinema.ui.navigation.NavRoutes
import java.net.URLEncoder

@Composable
fun ActressListScreen(
    navController: NavController,
    viewModel: ActressListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val actresses by viewModel.actresses.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val listState = rememberLazyListState()
    var dialogActress by remember { mutableStateOf<Actress?>(null) }

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !isLoadingMore && uiState is ActressListUiState.Success) {
            viewModel.loadMore()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (uiState) {
            is ActressListUiState.Loading -> {
                if (actresses.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            is ActressListUiState.Error -> {
                if (actresses.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (uiState as ActressListUiState.Error).message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            is ActressListUiState.Success -> {}
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = actresses,
                key = { it.link ?: it.name ?: it.hashCode().toString() }
            ) { actress ->
                ActressListItem(
                    actress = actress,
                    onClick = {
                        val rawUrl = actress.link ?: ""
                        val url = URLEncoder.encode(
                            if (rawUrl.contains("/")) rawUrl else "star/$rawUrl", "UTF-8"
                        )
                        val name = URLEncoder.encode(actress.name ?: "", "UTF-8")
                        navController.navigate(NavRoutes.movieList(name, url))
                    },
                    onLongClick = { dialogActress = actress }
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

    dialogActress?.let { actress ->
        ActressFavoriteDialog(
            actress = actress,
            onDismiss = { dialogActress = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActressListItem(
    actress: Actress,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = actress.imageUrl,
            contentDescription = actress.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
        )

        Column {
            Text(
                text = actress.name ?: "",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
