package io.github.javcinema.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.github.javcinema.ui.navigation.NavRoutes

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreListScreen(
    navController: NavController,
    viewModel: GenreListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val genres by viewModel.genres.collectAsState()
    var selected by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

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
                Column(modifier = Modifier.fillMaxSize()) {
                    if (selected.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { selected = emptyMap() }) {
                                Text("清除")
                            }
                            Button(onClick = {
                                navController.navigate(
                                    NavRoutes.movieList(
                                        combinedGenreTitle(selected.values.toList()),
                                        combinedGenreFilterUrl(selected.keys.toList())
                                    )
                                )
                            }) {
                                Text("查看 ${selected.size} 个类型")
                            }
                        }
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        genres.forEach { (category, genreList) ->
                            item {
                                Text(
                                    text = category,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 1.dp)
                                )
                            }
                            item {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    genreList.forEach { genre ->
                                        val id = genreIdFromLink(genre.link)
                                        FilterChip(
                                            selected = selected.containsKey(id),
                                            onClick = {
                                                if (id.isBlank()) return@FilterChip
                                                selected = if (selected.containsKey(id)) {
                                                    selected - id
                                                } else {
                                                    selected + (id to (genre.name ?: id))
                                                }
                                            },
                                            label = {
                                                Text(
                                                    text = genre.name ?: "",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            },
                                            modifier = Modifier.height(24.dp)
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
}
