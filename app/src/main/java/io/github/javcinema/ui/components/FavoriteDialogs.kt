package io.github.javcinema.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import io.github.javcinema.JAViewer
import io.github.javcinema.data.model.Actress
import io.github.javcinema.data.model.Movie
import io.github.javcinema.data.model.toggleStar

@Composable
fun MovieFavoriteDialog(movie: Movie, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val config = JAViewer.CONFIGURATIONS
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
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("code", movie.code ?: ""))
                    Toast.makeText(context, "已复制番号", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }) {
                    Text("复制番号", style = MaterialTheme.typography.bodyLarge)
                }
                TextButton(onClick = {
                    movie.toggleStar()
                    JAViewer.CONFIGURATIONS?.save()
                    onDismiss()
                }) {
                    Text(if (isStarred) "取消收藏" else "收藏", style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun ActressFavoriteDialog(actress: Actress, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val config = JAViewer.CONFIGURATIONS
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
                    actress.toggleStar()
                    JAViewer.CONFIGURATIONS?.save()
                    onDismiss()
                }) {
                    Text(if (isStarred) "取消收藏" else "收藏", style = MaterialTheme.typography.bodyLarge)
                }
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("name", actress.name ?: ""))
                    Toast.makeText(context, "已复制演员", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }) {
                    Text("复制演员", style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = {}
    )
}
