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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import io.github.javcinema.data.model.Genre
import io.github.javcinema.data.model.Movie
import io.github.javcinema.data.model.MovieDetail
import io.github.javcinema.data.model.Screenshot
import io.github.javcinema.ui.components.ActressRow
import io.github.javcinema.ui.components.GenreFlow
import io.github.javcinema.ui.components.MovieCard
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
    val isStarred by viewModel.isStarred.collectAsState()
    val relatedMovies by viewModel.relatedMovies.collectAsState()
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
                    onPreviewClick = { /* TODO: preview video */ },
                    onPlayClick = { /* TODO: play video */ },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            IconButton(onClick = { viewModel.toggleStar() }) {
                Icon(
                    imageVector = if (isStarred) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (isStarred) "取消收藏" else "收藏",
                    tint = if (isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        FloatingActionButton(
            onClick = {
                navController.navigate(NavRoutes.download(movieCode))
            },
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.Download, contentDescription = "下载")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
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
private fun MovieDetailContent(
    detail: MovieDetail,
    relatedMovies: List<Movie>,
    movieCode: String,
    navController: NavController,
    onPreviewClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        AsyncImage(
            model = detail.coverUrl,
            contentDescription = detail.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(280.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 0.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionWithIcon(Icons.Outlined.Description) {
                InfoRow("影片番号", detail.code ?: movieCode)
                InfoRow("影片名称", detail.title ?: movieCode)
                detail.headers.forEach { header ->
                    val name = header.name
                    val value = header.value
                    if (name != null && value != null) {
                        InfoRow(name, value)
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
                            GalleryState.imageUrls = detail.screenshots.mapNotNull { it.getImageUrl() }
                            GalleryState.movie = Movie().apply {
                                code = movieCode
                                title = detail.title
                            }
                            val index = detail.screenshots.indexOf(screenshot)
                            navController.navigate(NavRoutes.gallery(index.coerceAtLeast(0)))
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
                        }
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

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
