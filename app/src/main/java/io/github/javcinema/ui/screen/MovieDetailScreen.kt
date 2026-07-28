package io.github.javcinema.ui.screen

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import io.github.javcinema.ui.navigation.NavRoutes
import java.net.URLEncoder

@Composable
fun MovieDetailScreen(
    navController: NavController,
    movieCode: String,
    movieLink: String? = null,
    viewModel: MovieDetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val detail by viewModel.detail.collectAsState()
    val relatedMovies by viewModel.relatedMovies.collectAsState()
    var dialogMovie by remember { mutableStateOf<Movie?>(null) }
    var dialogActress by remember { mutableStateOf<Actress?>(null) }
    var galleryUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var galleryIndex by remember { mutableStateOf<Int?>(null) }
    var savedScroll by remember { mutableFloatStateOf(0f) }
    val scrollState = rememberScrollState()
    LaunchedEffect(scrollState.value) {
        savedScroll = scrollState.value.toFloat()
    }
    LaunchedEffect(movieCode) {
        viewModel.loadDetail(movieCode, movieLink)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (uiState) {
            is MovieDetailUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is MovieDetailUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (uiState as MovieDetailUiState.Error).message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            is MovieDetailUiState.Success -> {
                val d = detail ?: return@Box
                MovieDetailContent(
                    detail = d,
                    relatedMovies = relatedMovies,
                    movieCode = movieCode,
                    navController = navController,
                    scrollState = scrollState,
                    onScreenshotClick = { urls, index ->
                        galleryUrls = urls
                        galleryIndex = index
                    },
                    onPreviewClick = { /* TODO: preview video */ },
                    onPlayClick = { /* TODO: play video */ },
                    onMovieLongClick = { dialogMovie = it },
                    onActressLongClick = { dialogActress = it },
                    onCoverLongClick = {
                        dialogMovie = Movie().apply {
                            code = d.code ?: movieCode
                            title = d.title
                            link = d.id ?: movieCode
                            coverUrl = d.coverUrl
                        }
                    },
                    modifier = Modifier.fillMaxSize()
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
            onClose = { galleryIndex = null }
        )
    }
}

@Composable
private fun GalleryOverlay(
    imageUrls: List<String>,
    initialIndex: Int,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var backgroundBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val loopedPageCount = if (imageUrls.size > 1) Int.MAX_VALUE else 1
    val pagerState = rememberPagerState(
        pageCount = { loopedPageCount },
        initialPage = if (imageUrls.size > 1) {
            val base = Int.MAX_VALUE / 2
            base - (base % imageUrls.size) + initialIndex
        } else 0
    )

    val currentPageIndex = if (imageUrls.size > 1) pagerState.currentPage % imageUrls.size else 0

    LaunchedEffect(Unit) {
        withContext(Dispatchers.Main) {
            try {
                val activity = context as? androidx.activity.ComponentActivity
                val view = activity?.window?.decorView?.rootView
                if (view != null && view.width > 0 && view.height > 0) {
                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    view.draw(canvas)
                    backgroundBitmap = bitmap
                }
            } catch (_: Exception) {}
        }
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
        if (backgroundBitmap != null) {
            Image(
                bitmap = backgroundBitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(25.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageContent = { page ->
                val actualIndex = if (imageUrls.size > 1) page % imageUrls.size else 0
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.5f, 5f)
                                offset = Offset(
                                    x = offset.x + pan.x,
                                    y = offset.y + pan.y
                                )
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { onClose() })
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUrls.getOrNull(actualIndex) ?: "")
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    )
                }
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
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
                    Toast.makeText(context, "已复制: $value", Toast.LENGTH_SHORT).show()
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
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
                    Toast.makeText(context, "已复制: $value", Toast.LENGTH_SHORT).show()
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
            color = Color(0xFFE91E63),
            modifier = Modifier.alignByBaseline()
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun InfoRow(label: String, value: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.combinedClickable(
            onClick = {},
            onLongClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
                Toast.makeText(context, "已复制: $value", Toast.LENGTH_SHORT).show()
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
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.alignByBaseline()
        )
    }
}

@Composable
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
private fun MovieDetailContent(
    detail: MovieDetail,
    relatedMovies: List<Movie>,
    movieCode: String,
    navController: NavController,
    scrollState: ScrollState,
    onScreenshotClick: ((List<String>, Int) -> Unit)? = null,
    onPreviewClick: () -> Unit,
    onPlayClick: () -> Unit,
    onMovieLongClick: ((Movie) -> Unit)? = null,
    onActressLongClick: ((Actress) -> Unit)? = null,
    onCoverLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .navigationBarsPadding()
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(detail.coverUrl)
                .crossfade(true)
                .size(1080)
                .build(),
            contentDescription = detail.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .combinedClickable(
                    onClick = {
                        detail.coverUrl?.let { url ->
                            onScreenshotClick?.invoke(listOf(url), 0)
                        }
                    },
                    onLongClick = onCoverLongClick
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 0.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionWithIcon(Icons.Outlined.Description) {
                val movieCodeValue = detail.code ?: movieCode
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
                            val encodedTitle = URLEncoder.encode(name, "UTF-8")
                            val encodedUrl = URLEncoder.encode(filterUrl, "UTF-8")
                            InfoRowClickable(
                                label = name,
                                value = value,
                                onClick = { navController.navigate(NavRoutes.movieList(encodedTitle, encodedUrl)) }
                            )
                        } else {
                            InfoRow(name, value)
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = onPreviewClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.PlayCircle, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("预览")
                    }

                    Button(
                        onClick = onPlayClick,
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
                    ScreenshotRow(
                        screenshots = detail.screenshots,
                        onScreenshotClick = { screenshot ->
                            val urls = detail.screenshots.mapNotNull { it.getImageUrl() }
                            val index = detail.screenshots.indexOf(screenshot)
                            onScreenshotClick?.invoke(urls, index.coerceAtLeast(0))
                        }
                    )
                }
            }

            if (detail.actresses.isNotEmpty()) {
                HorizontalDivider()
                SectionWithIcon(Icons.Outlined.Face) {
                    ActressRow(
                        actresses = detail.actresses,
                        onActressClick = { actress ->
                            val name = actress.name ?: return@ActressRow
                            val link = actress.link ?: return@ActressRow
                            val rawUrl = if (link.contains("/")) link else "star/$link"
                            val encodedTitle = URLEncoder.encode(name, "UTF-8")
                            val encodedUrl = URLEncoder.encode(rawUrl, "UTF-8")
                            navController.navigate(NavRoutes.movieList(encodedTitle, encodedUrl))
                        },
                        onActressLongClick = { onActressLongClick?.invoke(it) }
                    )
                }
            }

            if (detail.genres.isNotEmpty()) {
                HorizontalDivider()
                SectionWithIcon(Icons.Outlined.Label) {
                    GenreFlow(
                        genres = detail.genres,
                        onGenreClick = { genre ->
                            val name = genre.name ?: return@GenreFlow
                            val link = genre.link ?: return@GenreFlow
                            val rawUrl = if (link.contains("/")) link else "genre/$link"
                            val encodedTitle = URLEncoder.encode(name, "UTF-8")
                            val encodedUrl = URLEncoder.encode(rawUrl, "UTF-8")
                            navController.navigate(NavRoutes.movieList(encodedTitle, encodedUrl))
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
                                        val encodedLink = URLEncoder.encode(link, "UTF-8")
                                        val encodedCode = URLEncoder.encode(movie.code ?: "", "UTF-8")
                                        navController.navigate(NavRoutes.movieDetail(encodedCode, encodedLink))
                                    },
                                    onLongClick = { onMovieLongClick?.invoke(movie) },
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

            Spacer(modifier = Modifier.height(16.dp).navigationBarsPadding())
        }
    }
}
