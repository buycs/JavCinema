package io.github.javcinema.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import io.github.javcinema.JavCinema
import io.github.javcinema.data.model.Actress
import io.github.javcinema.data.model.Movie
import io.github.javcinema.data.model.toggleStar
import io.github.javcinema.util.copyText

@Composable
fun MovieFavoriteDialog(movie: Movie, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val config = JavCinema.CONFIGURATIONS
    val isStarred = config?.starredMovies?.contains(movie) == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = movie.title ?: movie.code ?: "",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall
            )
        },
        text = {
            Column {
                TextButton(onClick = {
                    copyText(context, movie.code ?: "")
                    onDismiss()
                }) {
                    Text("复制番号", style = MaterialTheme.typography.bodyLarge)
                }
                TextButton(onClick = {
                    movie.toggleStar()
                    JavCinema.CONFIGURATIONS?.save()
                    val msg = if (isStarred) "已取消收藏" else "已收藏影片"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    onDismiss()
                }) {
                    Text(if (isStarred) "取消收藏" else "收藏影片", style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun ActressFavoriteDialog(actress: Actress, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val config = JavCinema.CONFIGURATIONS
    val isStarred = config?.starredActresses?.contains(actress) == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = actress.name ?: "",
                style = MaterialTheme.typography.titleSmall
            )
        },
        text = {
            Column {
                TextButton(onClick = {
                    copyText(context, actress.name ?: "", "已复制女优")
                    onDismiss()
                }) {
                    Text("复制女优", style = MaterialTheme.typography.bodyLarge)
                }
                TextButton(onClick = {
                    actress.toggleStar()
                    JavCinema.CONFIGURATIONS?.save()
                    val msg = if (isStarred) "已取消收藏" else "已收藏女优"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    onDismiss()
                }) {
                    Text(if (isStarred) "取消收藏" else "收藏女优", style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = {}
    )
}
