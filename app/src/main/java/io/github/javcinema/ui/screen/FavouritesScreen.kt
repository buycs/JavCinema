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
import coil.imageLoader
import coil.request.ImageRequest
import io.github.javcinema.JavCinema
import io.github.javcinema.data.model.Actress
import io.github.javcinema.data.model.DataSource
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
    var showDataSourceSwitchDialog by remember { mutableStateOf(false) }
    var pendingSwitchSourceName by remember { mutableStateOf<String?>(null) }
    var pendingSwitchNavigate by remember { mutableStateOf<(() -> Unit)?>(null) }
    val moviesGridState = rememberLazyGridState()
    val actressesListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        val movies = io.github.javcinema.JavCinema.CONFIGURATIONS?.starredMovies?.toList() ?: emptyList()
        val actresses = io.github.javcinema.JavCinema.CONFIGURATIONS?.starredActresses?.toList() ?: emptyList()
        starredMovies.value = movies
        starredActresses.value = actresses
        val loader = runCatching { JavCinema.instance.imageLoader }.getOrNull()
        if (loader != null) {
            val urls = movies.mapNotNull { it.coverUrl } + actresses.mapNotNull { it.imageUrl }
            urls.forEach { url ->
                try {
                    loader.enqueue(ImageRequest.Builder(JavCinema.instance)
                        .data(url)
                        .memoryCacheKey(url)
                        .build())
                } catch (e: Exception) {
                    android.util.Log.w("Favourites", "preload failed: $url - ${e.message}")
                }
            }
        }
    }

    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            when (pagerState.currentPage) {
                0 -> moviesGridState.animateScrollToItem(0)
                1 -> actressesListState.animateScrollToItem(0)
            }
        }
    }

    val dsVersionAtCreation = remember { JavCinema.dataSourceVersionFlow.value }

    LaunchedEffect(JavCinema.dataSourceVersionFlow.value) {
        if (JavCinema.dataSourceVersionFlow.value != dsVersionAtCreation) {
            moviesGridState.animateScrollToItem(0)
            actressesListState.animateScrollToItem(0)
        }
    }

    fun removeItem(item: Any) {
        val config = io.github.javcinema.JavCinema.CONFIGURATIONS ?: return
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

    if (showDataSourceSwitchDialog) {
        val targetName = pendingSwitchSourceName ?: ""
        AlertDialog(
            onDismissRequest = {
                showDataSourceSwitchDialog = false
                pendingSwitchSourceName = null
                pendingSwitchNavigate = null
            },
            title = { Text("数据源不匹配") },
            text = {
                Text("该收藏属于「$targetName」数据源，是否切换到该数据源查看？")
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = JavCinema.DATA_SOURCES.find { it.name == targetName }
                    if (target != null) {
                        val config = JavCinema.CONFIGURATIONS
                        if (config != null) {
                            config.dataSource = target
                            config.save()
                            JavCinema.recreateService()
                        }
                        pendingSwitchNavigate?.invoke()
                    }
                    showDataSourceSwitchDialog = false
                    pendingSwitchSourceName = null
                    pendingSwitchNavigate = null
                }) {
                    Text("切换")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDataSourceSwitchDialog = false
                    pendingSwitchSourceName = null
                    pendingSwitchNavigate = null
                }) {
                    Text("取消")
                }
            }
        )
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
                                        val currentSource = JavCinema.getDataSource()?.name
                                        if (movie.dataSourceName != null && currentSource != null && movie.dataSourceName != currentSource) {
                                            pendingSwitchSourceName = movie.dataSourceName
                                            pendingSwitchNavigate = {
                                                val code = URLEncoder.encode(movie.code ?: "", "UTF-8")
                                                val link = movie.link?.let { URLEncoder.encode(it, "UTF-8") }
                                                navController.navigate(NavRoutes.movieDetail(code, link, movie.coverUrl)) {
                                                    popUpTo(0) { inclusive = true }
                                                    launchSingleTop = true
                                                }
                                            }
                                            showDataSourceSwitchDialog = true
                                        } else {
                                            val code = URLEncoder.encode(movie.code ?: "", "UTF-8")
                                            val link = movie.link?.let { URLEncoder.encode(it, "UTF-8") }
                                            navController.navigate(NavRoutes.movieDetail(code, link, movie.coverUrl))
                                        }
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
                                                val currentSource = JavCinema.getDataSource()?.name
                                                if (actress.dataSourceName != null && currentSource != null && actress.dataSourceName != currentSource) {
                                                    pendingSwitchSourceName = actress.dataSourceName
                                                    pendingSwitchNavigate = {
                                                        val rawUrl = actress.link ?: ""
                                                        val url = URLEncoder.encode(
                                                            if (rawUrl.contains("/")) rawUrl else "star/$rawUrl", "UTF-8"
                                                        )
                                                        val name = URLEncoder.encode(actress.name ?: "", "UTF-8")
                                                        navController.navigate(NavRoutes.movieList(name, url)) {
                                                            popUpTo(0) { inclusive = true }
                                                            launchSingleTop = true
                                                        }
                                                    }
                                                    showDataSourceSwitchDialog = true
                                                } else {
                                                    val rawUrl = actress.link ?: ""
                                                    val url = URLEncoder.encode(
                                                        if (rawUrl.contains("/")) rawUrl else "star/$rawUrl", "UTF-8"
                                                    )
                                                    val name = URLEncoder.encode(actress.name ?: "", "UTF-8")
                                                    navController.navigate(NavRoutes.movieList(name, url))
                                                }
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