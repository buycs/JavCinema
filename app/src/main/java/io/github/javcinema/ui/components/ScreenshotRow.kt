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
import io.github.javcinema.data.model.Screenshot

@Composable
fun ScreenshotRow(
    screenshots: List<Screenshot>,
    onScreenshotClick: (Screenshot) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
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
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(4.dp))
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
