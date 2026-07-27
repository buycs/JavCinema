package io.github.javcinema.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.javcinema.data.model.Genre

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreFlow(
    genres: List<Genre>,
    onGenreClick: (Genre) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            genres.forEach { genre ->
                FilterChip(
                    selected = false,
                    onClick = { onGenreClick(genre) },
                    label = {
                        Text(
                            text = genre.name ?: "",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }
        }
    }
}
