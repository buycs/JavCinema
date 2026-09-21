package io.github.javcinema.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
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
import io.github.javcinema.ui.components.GenreFlow
import io.github.javcinema.ui.navigation.NavRoutes

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
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxSize()
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
                            GenreFlow(
                                genres = genreList,
                                onGenreClick = { genre ->
                                    val name = genre.name ?: return@GenreFlow
                                    val url = genreFilterUrl(genre.link) ?: return@GenreFlow
                                    navController.navigate(NavRoutes.movieList(name, url))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
