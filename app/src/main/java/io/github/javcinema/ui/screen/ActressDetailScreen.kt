package io.github.javcinema.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import io.github.javcinema.data.model.Actress
import io.github.javcinema.ui.components.ActressFavoriteDialog
import io.github.javcinema.ui.components.SwipeBackContainer

@Composable
fun ActressDetailScreen(
    navController: NavController,
    starId: String,
    name: String,
    imageUrl: String?,
    detailViewModel: ActressDetailViewModel = viewModel(),
    moviesViewModel: MovieListViewModel = viewModel(),
    scrollToTopTrigger: Long = 0L
) {
    val uiState by detailViewModel.uiState.collectAsState()
    var dialogActress by remember { mutableStateOf<Actress?>(null) }
    val moviesUrl = remember(starId) { actressMoviesUrl(starId, null) }

    LaunchedEffect(starId, name, imageUrl) {
        detailViewModel.load(starId, name, imageUrl)
        moviesViewModel.load(moviesUrl)
    }

    SwipeBackContainer(
        onBack = { navController.popBackStack() },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            when (val state = uiState) {
                ActressDetailUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is ActressDetailUiState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                is ActressDetailUiState.Success -> {
                    val profile = state.profile
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = profile.actress.imageUrl ?: imageUrl,
                            contentDescription = profile.actress.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.actress.name ?: name,
                                style = MaterialTheme.typography.titleLarge
                            )
                            profile.actress.movieCount?.let {
                                Text(
                                    text = "${it} 部作品",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            profile.birthday?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    text = "生日 $it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            profile.size?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { dialogActress = profile.actress }) {
                                Text("收藏")
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                MovieListScreen(
                    navController = navController,
                    title = name,
                    url = moviesUrl,
                    viewModel = moviesViewModel,
                    scrollToTopTrigger = scrollToTopTrigger,
                    enableSwipeBack = false
                )
            }
        }
    }

    dialogActress?.let { actress ->
        ActressFavoriteDialog(
            actress = actress,
            onDismiss = { dialogActress = null }
        )
    }
}
