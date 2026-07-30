package io.github.javcinema.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import coil.compose.AsyncImage
import io.github.javcinema.data.model.Actress
import io.github.javcinema.data.model.Movie
import io.github.javcinema.ui.components.MovieCard
import io.github.javcinema.ui.navigation.NavRoutes
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavouritesScreen(
    navController: NavController,
    scrollToTopTrigger: Long = 0L
) {
    val context = LocalContext.current
    val starredMovies = remember { mutableStateOf(emptyList<Movie>()) }
    val starredActresses = remember { mutableStateOf(emptyList<Actress>()) }
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    var showItemDialog by remember { mutableStateOf(false) }
    var dialogMovieItem by remember { mutableStateOf<Movie?>(null) }
    var dialogActressItem by remember { mutableStateOf<Actress?>(null) }
    val moviesGridState = rememberLazyGridState()
    val actressesListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        starredMovies.value = io.github.javcinema.JAViewer.CONFIGURATIONS?.starredMovies?.toList() ?: emptyList()
        starredActresses.value = io.github.javcinema.JAViewer.CONFIGURATIONS?.starredActresses?.toList() ?: emptyList()
    }

    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            when (pagerState.currentPage) {
                0 -> moviesGridState.animateScrollToItem(0)
                1 -> actressesListState.animateScrollToItem(0)
            }
        }
    }

    fun removeItem(item: Any) {
        val config = io.github.javcinema.JAViewer.CONFIGURATIONS ?: return
        when (item) {
            is Movie -> {
                config.starredMovies?.remove(item)
                starredMovies.value = config.starredMovies?.toList() ?: emptyList()
            }
            is Actress -> {
                config.starredActresses?.remove(item)
                starredActresses.value = config.starredActresses?.toList() ?: emptyList()
            }
        }
        config.save()
        Toast.makeText(context, "已取消收藏", Toast.LENGTH_SHORT).show()
    }

    if (showItemDialog) {
        val movie = dialogMovieItem
        val actress = dialogActressItem
        if (movie != null) {
            AlertDialog(
                onDismissRequest = {
                    showItemDialog = false
                    dialogMovieItem = null
                },
                title = {
                    Text(
                        text = movie.title ?: movie.code ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall
                    )
                },
                text = {
                    Column {
                        TextButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("code", movie.code ?: ""))
                            Toast.makeText(context, "已复制番号", Toast.LENGTH_SHORT).show()
                            showItemDialog = false
                            dialogMovieItem = null
                        }) {
                            Text("复制番号", style = MaterialTheme.typography.bodyLarge)
                        }
                        TextButton(onClick = {
                            removeItem(movie)
                            showItemDialog = false
                            dialogMovieItem = null
                        }) {
                            Text("取消收藏", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                },
                confirmButton = {}
            )
        } else if (actress != null) {
            AlertDialog(
                onDismissRequest = {
                    showItemDialog = false
                    dialogActressItem = null
                },
                title = {
                    Text(
                        text = actress.name ?: "",
                        style = MaterialTheme.typography.titleSmall
                    )
                },
                text = {
                    Column {
                        TextButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("name", actress.name ?: ""))
                            Toast.makeText(context, "已复制女优", Toast.LENGTH_SHORT).show()
                            showItemDialog = false
                            dialogActressItem = null
                        }) {
                            Text("复制女优", style = MaterialTheme.typography.bodyLarge)
                        }
                        TextButton(onClick = {
                            removeItem(actress)
                            showItemDialog = false
                            dialogActressItem = null
                        }) {
                            Text("取消收藏", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                },
                confirmButton = {}
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TabRow(selectedTabIndex = pagerState.currentPage) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = {
                    scope.launch { pagerState.animateScrollToPage(0) }
                },
                text = { Text("作品") }
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = {
                    scope.launch { pagerState.animateScrollToPage(1) }
                },
                text = { Text("女优") }
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> {
                    if (starredMovies.value.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("暂无收藏的影片", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            state = moviesGridState,
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = starredMovies.value,
                                key = { "${it.code ?: ""}_${it.link ?: ""}_${it.hashCode()}" }
                            ) { movie ->
                                MovieCard(
                                    movie = movie,
                                    onClick = {
                                        val code = URLEncoder.encode(movie.code ?: "", "UTF-8")
                                        val link = movie.link?.let { URLEncoder.encode(it, "UTF-8") }
                                        navController.navigate(NavRoutes.movieDetail(code, link))
                                    },
                                    onLongClick = {
                                        dialogMovieItem = movie
                                        dialogActressItem = null
                                        showItemDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    if (starredActresses.value.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("暂无收藏的女优", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            state = actressesListState,
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = starredActresses.value,
                                key = { "${it.link ?: ""}_${it.name ?: ""}_${it.hashCode()}" }
                            ) { actress ->
                                val clickModifier = if (actress.link != null) {
                                    Modifier.pointerInput(actress) {
                                        detectTapGestures(
                                            onTap = {
                                                val rawUrl = actress.link ?: ""
                                                val url = URLEncoder.encode(
                                                    if (rawUrl.contains("/")) rawUrl else "star/$rawUrl", "UTF-8"
                                                )
                                                val name = URLEncoder.encode(actress.name ?: "", "UTF-8")
                                                navController.navigate(NavRoutes.movieList(name, url))
                                            },
                                            onLongPress = {
                                                dialogActressItem = actress
                                                dialogMovieItem = null
                                                showItemDialog = true
                                            }
                                        )
                                    }
                                } else {
                                    Modifier
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(clickModifier)
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

                                    Text(
                                        text = actress.name ?: "",
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}