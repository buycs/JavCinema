package io.github.javcinema.ui.screen

import android.view.WindowManager
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.javcinema.JAViewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    initialIndex: Int,
    onClose: () -> Unit
) {
    val images = GalleryState.imageUrls
    val movie = GalleryState.movie
    val pagerState = rememberPagerState(pageCount = { images.size })
    var showBars by remember { mutableStateOf(true) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (initialIndex in images.indices) {
            pagerState.scrollToPage(initialIndex)
        }
    }

    val activity = LocalContext.current as? androidx.activity.ComponentActivity
    val onBack = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    DisposableEffect(showBars) {
        activity?.window?.let { window ->
            if (showBars) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
            } else {
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                window.decorView.systemUiVisibility = (
                        android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                        )
            }
        }
        onDispose {
            activity?.window?.let { window ->
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    var autoHideJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    LaunchedEffect(showBars) {
        if (showBars) {
            delay(3000)
            showBars = false
        }
    }

    Scaffold(
        topBar = {
            if (showBars) {
                TopAppBar(
                    title = {
                        Text("${pagerState.currentPage + 1} / ${images.size}")
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.Close, contentDescription = "关闭")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
                            scope.launch {
                                try {
                                    val dir = JAViewer.getStorageDir()
                                    val movieDir = if (movie != null) {
                                        File(dir, "[${movie.code}] ${movie.title}".replace(Regex("[:\\\\/*?\"<>|]"), "-"))
                                    } else {
                                        File(dir, "gallery")
                                    }
                                    movieDir.mkdirs()

                                    val url = images.getOrNull(pagerState.currentPage) ?: return@launch
                                    val fileName = url.substringAfterLast("/").substringBefore("?")
                                    val file = File(movieDir, fileName)
                                    val connection = URL(url).openConnection()
                                    connection.getInputStream().use { input ->
                                        FileOutputStream(file).use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }) {
                            Icon(Icons.Filled.Save, contentDescription = "保存")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.7f),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            }
        },
        containerColor = Color.Black
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) { page ->
            ZoomableImage(
                imageUrl = images.getOrNull(page) ?: "",
                onTap = { showBars = !showBars; autoHideJob?.cancel() }
            )
        }
    }
}

@Composable
private fun ZoomableImage(
    imageUrl: String,
    onTap: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 5f)
                    offset = Offset(
                        x = offset.x + pan.x,
                        y = offset.y + pan.y
                    )
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() })
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    }
}
