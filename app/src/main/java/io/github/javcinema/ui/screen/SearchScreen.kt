package io.github.javcinema.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.github.javcinema.JavCinema
import io.github.javcinema.data.model.Actress
import io.github.javcinema.data.model.Configurations
import io.github.javcinema.data.model.Movie
import io.github.javcinema.ui.components.ActressFavoriteDialog
import io.github.javcinema.ui.components.MovieCard
import io.github.javcinema.ui.components.MovieFavoriteDialog
import io.github.javcinema.ui.navigation.NavRoutes
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val HISTORY_MAX = 25
private const val MAX_HISTORY_ROWS = 2

private data class VisibleChips(val visibleCount: Int, val hasMore: Boolean)

@Composable
fun SearchScreen(
    navController: NavController,
    initialQuery: String = "",
    viewModel: SearchViewModel = viewModel(),
    scrollToTopTrigger: Long = 0L
) {
    val uiState by viewModel.uiState.collectAsState()
    val movies by viewModel.movies.collectAsState()
    val actresses by viewModel.actresses.collectAsState()
    val searchScope by viewModel.scope.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val uiPrefsVersion by JavCinema.uiPrefsVersionFlow.collectAsState()
    val gridColumns = if (uiPrefsVersion >= 0) Configurations.gridColumns.coerceIn(2, 4) else 3
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var dialogMovie by remember { mutableStateOf<Movie?>(null) }
    var dialogActress by remember { mutableStateOf<Actress?>(null) }
    var savedIndex by rememberSaveable { mutableIntStateOf(0) }
    var savedOffset by rememberSaveable { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = savedIndex,
        initialFirstVisibleItemScrollOffset = savedOffset
    )

    val context = LocalContext.current
    var history by remember { mutableStateOf(SearchHistoryStore.load(context, SearchScope.MOVIES).take(HISTORY_MAX)) }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(searchScope) {
        history = SearchHistoryStore.load(context, searchScope).take(HISTORY_MAX)
    }

    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            gridState.animateScrollToItem(0)
        }
    }

    val dsVersionAtCreation = remember { JavCinema.dataSourceVersionFlow.value }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) {
            query = initialQuery
            viewModel.search(initialQuery, searchScope)
        }
    }

    LaunchedEffect(JavCinema.dataSourceVersionFlow.value) {
        if (JavCinema.dataSourceVersionFlow.value != dsVersionAtCreation) {
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

    fun doSearch(keyword: String) {
        val q = keyword.trim()
        query = q
        if (q.isEmpty()) {
            if (searchScope == SearchScope.FAVORITES) {
                viewModel.search("", SearchScope.FAVORITES, force = true)
            }
            return
        }
        history = SearchHistoryStore.add(context, searchScope, q).take(HISTORY_MAX)
        viewModel.search(q, searchScope)
    }

    val hasActiveSearch = query.isNotBlank() || uiState !is SearchUiState.Idle
    BackHandler(enabled = hasActiveSearch) {
        query = ""
        viewModel.reset()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(
                            when (searchScope) {
                                SearchScope.ACTRESSES -> "搜索女优..."
                                SearchScope.FAVORITES -> "搜索收藏..."
                                SearchScope.MOVIES -> "搜索影片..."
                            }
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { doSearch(query) }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    trailingIcon = {
                        IconButton(onClick = {
                            focusRequester.requestFocus()
                            if (query.isBlank()) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("请输入搜索关键字")
                                }
                            } else {
                                doSearch(query)
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                )
                Spacer(Modifier.padding(start = 8.dp))
            }
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = searchScope == SearchScope.MOVIES,
                    onClick = { viewModel.setScope(SearchScope.MOVIES) },
                    label = { Text("影片") }
                )
                FilterChip(
                    selected = searchScope == SearchScope.ACTRESSES,
                    onClick = { viewModel.setScope(SearchScope.ACTRESSES) },
                    label = { Text("女优") }
                )
                FilterChip(
                    selected = searchScope == SearchScope.FAVORITES,
                    onClick = { viewModel.setScope(SearchScope.FAVORITES) },
                    label = { Text("收藏") }
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                state = gridState,
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState is SearchUiState.Idle && history.isNotEmpty()) {
                    item(span = { GridItemSpan(gridColumns) }) {
                        SearchHistorySection(
                            history = history,
                            onSelect = { doSearch(it) },
                            onDelete = {
                                history = SearchHistoryStore.remove(context, searchScope, it).take(HISTORY_MAX)
                            },
                            onClear = {
                                SearchHistoryStore.clear(context, searchScope)
                                history = emptyList()
                            }
                        )
                    }
                }

            when (uiState) {
                is SearchUiState.Idle -> {}
                is SearchUiState.Loading -> {
                    if (movies.isEmpty() && actresses.isEmpty() && searchScope != SearchScope.FAVORITES) {
                        item(span = { GridItemSpan(gridColumns) }) {
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
                is SearchUiState.Success -> {
                    if (searchScope == SearchScope.MOVIES && movies.isEmpty() && looksLikeMovieCode(query)) {
                        item(span = { GridItemSpan(gridColumns) }) {
                            TextButton(onClick = {
                                navController.navigate(NavRoutes.download(query.trim()))
                            }) {
                                Text("用番号搜磁力")
                            }
                        }
                    }
                }
            }

            if (searchScope == SearchScope.MOVIES || searchScope == SearchScope.FAVORITES) {
                if (searchScope == SearchScope.FAVORITES && movies.isEmpty() && actresses.isEmpty() && uiState is SearchUiState.Success) {
                    item(span = { GridItemSpan(gridColumns) }) {
                        Text(
                            text = if (query.isBlank()) "暂无收藏" else "没有匹配的收藏",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                items(
                    items = movies,
                    key = { "m_${it.dataSourceName ?: ""}_${it.code ?: ""}_${it.link ?: ""}" }
                ) { movie ->
                    MovieCard(
                        movie = movie,
                        onClick = {
                            navController.navigate(NavRoutes.movieDetail(movie.code ?: "", movie.link, movie.coverUrl))
                        },
                        onLongClick = { dialogMovie = movie }
                    )
                }
            }
            if (searchScope == SearchScope.ACTRESSES || searchScope == SearchScope.FAVORITES) {
                items(
                    items = actresses,
                    key = { "a_${it.dataSourceName ?: ""}_${it.link ?: ""}_${it.name ?: ""}" },
                    span = { GridItemSpan(gridColumns) }
                ) { actress ->
                    ActressSearchRow(
                        actress = actress,
                        onClick = {
                            val starId = actressStarId(actress.link)
                            if (starId.isNotBlank()) {
                                navController.navigate(
                                    NavRoutes.actressDetail(starId, actress.name, actress.imageUrl)
                                )
                            }
                        },
                        onLongClick = { dialogActress = actress }
                    )
                }
            }

            if (isLoadingMore) {
                item(span = { GridItemSpan(gridColumns) }) {
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

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 16.dp)
    )
    }

    dialogMovie?.let { movie ->
        MovieFavoriteDialog(
            movie = movie,
            onDismiss = { dialogMovie = null }
        )
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
private fun ActressSearchRow(
    actress: Actress,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        coil.compose.AsyncImage(
            model = actress.imageUrl,
            contentDescription = actress.name,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
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
            actress.movieCount?.let { count ->
                Text(
                    text = "${count} 部作品",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchHistorySection(
    history: List<String>,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    var expanded by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }

    LaunchedEffect(history.isEmpty()) {
        if (history.isEmpty()) managing = false
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "历史记录",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.weight(1f))
            if (managing) {
                TextButton(onClick = { managing = false }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("完成", fontSize = 12.sp)
                }
            } else {
                TextButton(onClick = onClear, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("清空", fontSize = 12.sp)
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val chipStyle = MaterialTheme.typography.labelSmall
            val plusStyle = chipStyle.copy(fontWeight = FontWeight.Bold)
            val density = LocalDensity.current
            val visible = remember(textMeasurer, history, constraints.maxWidth) {
                val chipHPadPx = with(density) { 12.dp.toPx() }.roundToInt() * 2
                val spacingPx = with(density) { 4.dp.toPx() }.roundToInt()
                fun chipWidthPx(text: String, style: TextStyle): Int =
                    textMeasurer.measure(AnnotatedString(text), style, maxLines = 1).size.width + chipHPadPx
                val plusW = chipWidthPx("+", plusStyle) + spacingPx

                fun countVisible(hasMore: Boolean): Int {
                    fun currentAvail(rows: Int): Int {
                        val reserve = if (hasMore && rows == MAX_HISTORY_ROWS) plusW else 0
                        return constraints.maxWidth - reserve
                    }

                    var rows = 1
                    var used = 0
                    var shown = 0
                    for (item in history) {
                        val w = chipWidthPx(item, chipStyle)
                        if (used > 0 && used + spacingPx + w > currentAvail(rows)) {
                            rows++
                            if (rows > MAX_HISTORY_ROWS) return shown
                            used = 0
                        }
                        val rowAvail = currentAvail(rows)
                        if (used + w > rowAvail) {
                            if (rows >= MAX_HISTORY_ROWS) return shown
                            used = w
                            shown++
                            continue
                        }
                        used += (if (used > 0) spacingPx else 0) + w
                        shown++
                    }
                    return shown
                }

                val allFit = countVisible(hasMore = false) >= history.size
                VisibleChips(
                    visibleCount = if (allFit) history.size else countVisible(hasMore = true),
                    hasMore = !allFit
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val shown = if (expanded) history else history.take(visible.visibleCount)
                shown.forEach { item ->
                    HistoryChip(
                        text = item,
                        style = chipStyle,
                        managing = managing,
                        onClick = { onSelect(item) },
                        onLongClick = { managing = true },
                        onDelete = { onDelete(item) }
                    )
                }
                if (!expanded && visible.hasMore) {
                    HistoryChip(
                        text = "+",
                        style = plusStyle,
                        emphasized = true,
                        onClick = { expanded = true }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryChip(
    text: String,
    style: TextStyle,
    emphasized: Boolean = false,
    managing: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val shake: Float
    if (managing) {
        val infiniteTransition = rememberInfiniteTransition(label = "shake")
        shake = infiniteTransition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 120, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "shakeOffset"
        ).value
    } else {
        shake = 0f
    }

    val base = Modifier
        .clip(RoundedCornerShape(16.dp))
        .background(
            if (emphasized) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )

    Box(
        modifier = base
            .then(if (managing) Modifier.offset(x = (shake * 2f).dp) else Modifier)
            .then(
                if (managing) {
                    Modifier.combinedClickable(onClick = {}, onLongClick = {})
                } else {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                }
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (managing) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 5.dp, y = (-5).dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
                    .clickable { onDelete?.invoke() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
