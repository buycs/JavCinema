package io.github.javcinema.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.javcinema.JavCinema
import io.github.javcinema.data.model.Movie

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MovieCard(
    movie: Movie,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    prefetchCover: Boolean = true
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
            Column {
                LaunchedEffect(movie.coverUrl) {
                    if (!prefetchCover) return@LaunchedEffect
                    val thumb = movie.coverUrl ?: return@LaunchedEffect
                    if (JavCinema.prefetchedLargeCovers.contains(thumb)) return@LaunchedEffect
                    var large = movie.link?.let { JavCinema.imageUrlsRegistry[it]?.posterLarge }
                    if (large == null && thumb.endsWith("ps.jpg")) {
                        large = thumb.substring(0, thumb.length - "ps.jpg".length) + "pl.jpg"
                    }
                    if (large != null && JavCinema.prefetchSemaphore.tryAcquire()) {
                        JavCinema.enqueueCoverWithRetry(large, JavCinema.instance,
                            onSuccess = {
                                JavCinema.prefetchedLargeCovers.add(thumb)
                                JavCinema.prefetchSemaphore.release()
                            },
                            onFailed = { JavCinema.prefetchSemaphore.release() }
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.7f)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(JavCinema.instance)
                            .data(movie.coverUrl)
                            .size(147, 200)
                            .memoryCacheKey(movie.coverUrl)
                            .build(),
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (movie.coverUrl == null) {
                        Text(
                            text = movie.code ?: "?",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 68.dp)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = movie.title ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = movie.code ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color(0xFFE91E63)
                        )

                        Text(
                            text = movie.date ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
    }
}
