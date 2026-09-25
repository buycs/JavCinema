package io.github.javcinema.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import io.github.javcinema.ui.components.InfoRow
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import io.github.javcinema.JavCinema
import io.github.javcinema.data.model.Actress
import io.github.javcinema.data.model.Genre
import io.github.javcinema.data.model.Movie
import io.github.javcinema.data.model.MovieDetail
import io.github.javcinema.data.model.Screenshot
import io.github.javcinema.data.model.toggleStar
import io.github.javcinema.ui.components.ActressFavoriteDialog
import io.github.javcinema.ui.components.ActressRow
import io.github.javcinema.ui.components.GenreFlow
import io.github.javcinema.ui.components.MovieCard
import io.github.javcinema.ui.components.MovieFavoriteDialog
import io.github.javcinema.ui.components.ScreenshotRow
import io.github.javcinema.ui.components.ZoomableImage
import io.github.javcinema.ui.components.rememberSaveImageAction
import io.github.javcinema.ui.navigation.NavRoutes
import io.github.javcinema.util.copyText
import io.github.javcinema.util.saveImageToGallery

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MovieDetailScreen(
    navController: NavController,
    movieCode: String,
    movieLink: String? = null,
    thumbnailUrl: String? = null,
    viewModel: MovieDetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val detail by viewModel.detail.collectAsState()
    val relatedMovies by viewModel.relatedMovies.collectAsState()
    val defaultCover by viewModel.defaultCover.collectAsState()
    var dialogMovie by remember { mutableStateOf<Movie?>(null) }
    var dialogActress by remember { mutableStateOf<Actress?>(null) }
    var galleryUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var galleryIndex by remember { mutableStateOf<Int?>(null) }
    var galleryMovie by remember { mutableStateOf<Movie?>(null) }
    var savedScroll by remember { mutableFloatStateOf(0f) }
    val scrollState = rememberScrollState()
    LaunchedEffect(scrollState.value) {
        savedScroll = scrollState.value.toFloat()
    }
    val localContext = LocalContext.current
    var largeCoverUrl by remember(movieCode) { mutableStateOf<String?>(null) }
    LaunchedEffect(movieCode) {
        val thumb = thumbnailUrl
        var large: String? = null
        movieLink?.let { link -> large = JavCinema.imageUrlsRegistry[link]?.posterLarge }
        if (large == null && thumb != null && thumb.endsWith("ps.jpg")) {
            large = thumb.substring(0, thumb.length - "ps.jpg".length) + "pl.jpg"
        }
        largeCoverUrl = large
        viewModel.loadDetail(movieCode, movieLink, thumbnailUrl)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .navigationBarsPadding()
    ) {
        // Cover renders immediately on entry so pl.jpg downloads in parallel
        // with the detail API call, instead of waiting for it to finish.
        DetailCover(
            coverUrl = largeCoverUrl ?: defaultCover ?: detail?.coverUrl ?: thumbnailUrl,
            thumbnailUrl = thumbnailUrl,
            contentDescription = detail?.title ?: movieCode,
            onClick = {
                val cover = largeCoverUrl ?: defaultCover ?: detail?.coverUrl ?: thumbnailUrl
                galleryUrls = listOfNotNull(cover)
                galleryIndex = 0
                galleryMovie = detail?.let { d ->
                    Movie().apply {
                        code = d.code ?: movieCode
                        title = d.title
                    }
                }
            },
            onLongClick = detail?.let { d ->
                {
                    dialogMovie = Movie().apply {
                        code = d.code ?: movieCode
                        title = d.title
                        link = d.id ?: movieCode
                        coverUrl = defaultCover ?: d.coverUrl
                        date = d.headers.find { it.name == "发行日期" }?.value
                        dataSourceName = JavCinema.getDataSource()?.name
                    }
                }
            }
        )
        when (uiState) {
            is MovieDetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is MovieDetailUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (uiState as MovieDetailUiState.Error).message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            is MovieDetailUiState.Success -> {
                val d = detail ?: return@Column
                MovieDetailContent(
                    detail = d,
                    fallbackCoverUrl = largeCoverUrl ?: defaultCover ?: d.coverUrl ?: thumbnailUrl,
                    relatedMovies = relatedMovies,
                    movieCode = movieCode,
                    navController = navController,
                    onScreenshotClick = { urls, index ->
                        galleryUrls = urls
                        galleryIndex = index
                        galleryMovie = Movie().apply {
                            code = d.code ?: movieCode
                            title = d.title
                        }
                    },
                    onMovieLongClick = { dialogMovie = it },
                    onActressLongClick = { dialogActress = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
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

    galleryIndex?.let { index ->
        GalleryOverlay(
            imageUrls = galleryUrls,
            initialIndex = index,
            movie = galleryMovie,
            onClose = { galleryIndex = null }
        )
    }
}

@Composable
private fun GalleryOverlay(
    imageUrls: List<String>,
    initialIndex: Int,
    movie: Movie?,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var showSaveDialog by remember { mutableStateOf(false) }
    val saveImage = rememberSaveImageAction()
    val pagerState = rememberPagerState(
        pageCount = { imageUrls.size.coerceAtLeast(1) },
        initialPage = initialIndex.coerceIn(0, (imageUrls.size - 1).coerceAtLeast(0))
    )

    val currentPageIndex = pagerState.currentPage.coerceIn(0, (imageUrls.size - 1).coerceAtLeast(0))

    BackHandler {
        onClose()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClose
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageContent = { page ->
                ZoomableImage(
                    imageUrl = imageUrls.getOrNull(page) ?: "",
                    onTap = { onClose() },
                    onLongPress = { showSaveDialog = true },
                    modifier = Modifier.fillMaxSize()
                )
            }
        )

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "关闭",
                tint = Color.White
            )
        }

        Text(
            text = "${currentPageIndex + 1} / ${imageUrls.size}",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("保存图片") },
            text = { Text("将当前图片保存到本地？") },
            confirmButton = {
                TextButton(onClick = {
                    showSaveDialog = false
                    val url = imageUrls.getOrNull(currentPageIndex) ?: return@TextButton
                    val subDir = if (movie != null) {
                        "[${movie.code}] ${movie.title}"
                    } else {
                        "gallery"
                    }
                    saveImage(url, subDir)
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun InfoRowClickable(label: String, value: String, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    copyText(context, value, if (label.contains("番号")) "已复制番号" else "已复制")
                }
            )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp).alignByBaseline()
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.alignByBaseline()
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun InfoRowClickableMagnet(label: String, value: String, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    copyText(context, value, if (label.contains("番号")) "已复制番号" else "已复制")
                }
            )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp).alignByBaseline()
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.alignByBaseline()
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SectionWithIcon(icon: ImageVector, content: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .width(24.dp)
                .padding(top = 2.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            content()
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DetailCover(
    coverUrl: String?,
    thumbnailUrl: String?,
    contentDescription: String?,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?
) {
    val coverContext = LocalContext.current
    var coverModel by remember(coverUrl, thumbnailUrl) {
        mutableStateOf(thumbnailUrl ?: coverUrl)
    }
    LaunchedEffect(coverUrl, thumbnailUrl) {
        if (coverUrl != null && coverUrl != coverModel) {
            coverModel = coverUrl
        }
    }
    if (coverModel != null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .combinedClickable(
                    onClick = onClick ?: {},
                    onLongClick = onLongClick
                )
        ) {
            if (thumbnailUrl != null && coverModel != thumbnailUrl) {
                AsyncImage(
                    model = ImageRequest.Builder(coverContext)
                        .data(thumbnailUrl)
                        .size(147, 200)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    imageLoader = coverContext.imageLoader,
                    modifier = Modifier.fillMaxSize()
                )
            }
            AsyncImage(
                model = ImageRequest.Builder(coverContext)
                    .data(coverModel)
                    .memoryCacheKey(coverModel)
                    .size(1080, 763)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                    imageLoader = coverContext.imageLoader,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MovieDetailContent(
    detail: MovieDetail,
    fallbackCoverUrl: String?,
    relatedMovies: List<Movie>,
    movieCode: String,
    navController: NavController,
    modifier: Modifier = Modifier,
    onScreenshotClick: ((List<String>, Int) -> Unit)? = null,
    onMovieLongClick: ((Movie) -> Unit)? = null,
    onActressLongClick: ((Actress) -> Unit)? = null
) {
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 0.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionWithIcon(Icons.Outlined.Description) {
                val movieCodeValue = detail.code ?: movieCode
                val playContext = LocalContext.current
                InfoRowClickableMagnet(
                    label = "影片番号",
                    value = movieCodeValue,
                    onClick = { navController.navigate(NavRoutes.download(movieCodeValue)) }
                )
                InfoRow("影片名称", detail.title ?: movieCode)
                val headerUrlPrefix = mapOf(
                    "导演" to "director",
                    "制作商" to "studio",
                    "发行商" to "label",
                    "系列" to "series"
                )
                detail.headers.forEach { header ->
                    val name = header.name
                    val value = header.value
                    if (name != null && value != null) {
                        val prefix = headerUrlPrefix[name]
                        if (prefix != null) {
                            val id = header.link ?: value
                            val filterUrl = "$prefix/$id"
                            InfoRowClickable(
                                label = name,
                                value = value,
                                onClick = { navController.navigate(NavRoutes.movieList(name, filterUrl)) }
                            )
                        } else {
                            InfoRow(name, value)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(
                        onClick = {
                            Toast.makeText(playContext, "预览暂未实现", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.PlayCircle, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("预览")
                    }
                    Button(
                        onClick = {
                            navController.navigate(NavRoutes.missavPlay(movieCodeValue))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("播放")
                    }
                }
            }

            HorizontalDivider()

            if (detail.screenshots.isNotEmpty()) {
                SectionWithIcon(Icons.Outlined.Collections) {
                    val screenshotContext = LocalContext.current
                    var hasScreenshots by remember(detail.code) { mutableStateOf<Boolean?>(null) }
                    LaunchedEffect(detail.code, detail.screenshots) {
                        val loader = screenshotContext.imageLoader
                        val results = withContext(Dispatchers.IO) {
                            detail.screenshots.take(4).map { s ->
                                async {
                                    val url = s.thumbnailUrl ?: return@async false
                                    try {
                                        loader.execute(
                                            ImageRequest.Builder(screenshotContext).data(url).build()
                                        ) is coil.request.SuccessResult
                                    } catch (_: Exception) {
                                        false
                                    }
                                }
                            }.awaitAll()
                        }
                        hasScreenshots = results.any { it }
                    }
                    when (hasScreenshots) {
                        null -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "截图加载中…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        true -> ScreenshotRow(
                            screenshots = detail.screenshots,
                            imageLoader = screenshotContext.imageLoader,
                            fallbackUrl = fallbackCoverUrl,
                            onFallbackClick = {
                                onScreenshotClick?.invoke(listOfNotNull(fallbackCoverUrl), 0)
                            },
                            onScreenshotClick = { screenshot ->
                                val urls = detail.screenshots.mapNotNull { it.getImageUrl() }
                                val index = detail.screenshots.indexOf(screenshot)
                                onScreenshotClick?.invoke(urls, index.coerceAtLeast(0))
                            }
                        )
                        false -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                repeat(4) { index ->
                                    if (index == 0) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(screenshotContext)
                                                .data(fallbackCoverUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "预览",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(16f / 9f)
                                                .clip(RoundedCornerShape(4.dp))
                                                .clickable {
                                                    onScreenshotClick?.invoke(listOfNotNull(fallbackCoverUrl), 0)
                                                }
                                        )
                                    } else {
                                        Box(modifier = Modifier.weight(1f).aspectRatio(16f / 9f))
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (fallbackCoverUrl != null) {
                SectionWithIcon(Icons.Outlined.Collections) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            repeat(4) { index ->
                                if (index == 0) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(fallbackCoverUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "预览",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(16f / 9f)
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable {
                                                onScreenshotClick?.invoke(listOfNotNull(fallbackCoverUrl), 0)
                                            }
                                    )
                                } else {
                                    Box(modifier = Modifier.weight(1f).aspectRatio(16f / 9f))
                                }
                            }
                        }
                    }
                }
            }

            if (detail.actresses.isNotEmpty()) {
                HorizontalDivider()
                SectionWithIcon(Icons.Outlined.Face) {
                    ActressRow(
                        actresses = detail.actresses,
                        imageLoader = LocalContext.current.imageLoader,
                        onActressClick = { actress ->
                            val name = actress.name ?: return@ActressRow
                            val starId = actressStarId(actress.link)
                            if (starId.isBlank()) return@ActressRow
                            navController.navigate(
                                NavRoutes.actressDetail(starId, name, actress.imageUrl)
                            )
                        },
                        onActressLongClick = { onActressLongClick?.invoke(it) }
                    )
                }
            }

            if (detail.genres.isNotEmpty()) {
                HorizontalDivider()
                SectionWithIcon(Icons.AutoMirrored.Outlined.Label) {
                    GenreFlow(
                        genres = detail.genres,
                        onGenreClick = { genre ->
                            val name = genre.name ?: return@GenreFlow
                            val url = genreFilterUrl(genre.link) ?: return@GenreFlow
                            navController.navigate(NavRoutes.movieList(name, url))
                        }
                    )
                }
            }

            if (relatedMovies.isNotEmpty()) {
                HorizontalDivider()
                SectionWithIcon(Icons.Outlined.Movie) {
                    relatedMovies.chunked(3).forEach { rowItems ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            rowItems.forEach { movie ->
                                MovieCard(
                                    movie = movie,
                                    onClick = {
                                        val link = movie.link ?: movie.code ?: return@MovieCard
                                        navController.navigate(NavRoutes.movieDetail(movie.code ?: "", link, movie.coverUrl))
                                    },
                                    onLongClick = { onMovieLongClick?.invoke(movie) },
                                    prefetchCover = false,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                            repeat(3 - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // ⚠️ 这里的底部留白不要调大：外层 Column 已经挂了 `navigationBarsPadding()`，
            // 它会为系统手势条让出一整块空间（本机 420dpi 下约 26dp），两者是**线性叠加**的。
            // 曾经是 40dp，叠加后滚到底会有约 66dp 的纯白，视觉上像「内容没铺满」。
            // 16dp 只用来让最后一行卡片与下方留一点呼吸感，避开手势条的活交给上面那层 padding。
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
