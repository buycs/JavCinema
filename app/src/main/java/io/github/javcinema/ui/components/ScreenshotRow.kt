package io.github.javcinema.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.ImageLoader
import coil.imageLoader
import io.github.javcinema.data.model.Screenshot
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext

@Composable
fun ScreenshotRow(
    screenshots: List<Screenshot>,
    onScreenshotClick: (Screenshot) -> Unit,
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader = LocalContext.current.imageLoader
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        screenshots.chunked(4).forEach { chunk ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val items = chunk.toMutableList<Screenshot?>().apply {
                    while (size < 4) add(null)
                }
                items.forEach { screenshot ->
                    if (screenshot != null) {
                        AsyncImage(
                            model = screenshot.thumbnailUrl,
                            imageLoader = imageLoader,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onScreenshotClick(screenshot) }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(16f / 9f)
                        )
                    }
                }
            }
        }
    }
}
