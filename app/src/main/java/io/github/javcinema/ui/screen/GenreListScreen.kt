package io.github.javcinema.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.github.javcinema.data.model.Genre
import io.github.javcinema.ui.navigation.NavRoutes
import java.net.URLEncoder

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreListScreen(
    navController: NavController,
    viewModel: GenreListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val genres by viewModel.genres.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when (uiState) {
            is GenreListUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is GenreListUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (uiState as GenreListUiState.Error).message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            is GenreListUiState.Success -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    genres.forEach { (category, genreList) ->
                        item {
                            Text(
                                text = category,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        item {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                genreList.forEach { genre ->
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            val rawUrl = genre.link ?: ""
                                            val url = URLEncoder.encode(
                                                if (rawUrl.contains("/")) rawUrl else "genre/$rawUrl", "UTF-8"
                                            )
                                            val name = URLEncoder.encode(genre.name ?: "", "UTF-8")
                                            navController.navigate(NavRoutes.movieList(name, url))
                                        },
                                        label = {
                                            Text(
                                                text = genre.name ?: "",
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
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
