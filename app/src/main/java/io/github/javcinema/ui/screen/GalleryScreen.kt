package io.github.javcinema.ui.screen

import android.view.WindowManager
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.javcinema.JavCinema
import io.github.javcinema.ui.components.ZoomableImage
import io.github.javcinema.ui.components.rememberSaveImageAction
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    initialIndex: Int,
    onClose: () -> Unit
) {
    val images = GalleryState.imageUrls
    val movie = GalleryState.movie
    val loopedPageCount = if (images.size > 1) Int.MAX_VALUE else images.size
    val pagerState = rememberPagerState(
        pageCount = { loopedPageCount },
        initialPage = if (images.size > 1) {
            val base = Int.MAX_VALUE / 2
            base - (base % images.size) + initialIndex.coerceIn(0, images.size - 1)
        } else 0
    )
    val currentPageIndex = if (images.size > 1) pagerState.currentPage % images.size else pagerState.currentPage
    var showBars by remember { mutableStateOf(true) }
    var showSaveDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val saveImage = rememberSaveImageAction()

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
                        Text("${currentPageIndex + 1} / ${images.size}")
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.Close, contentDescription = "关闭")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val url = images.getOrNull(currentPageIndex) ?: return@IconButton
                            val subDir = if (movie != null) {
                                "[${movie.code}] ${movie.title}"
                            } else {
                                "gallery"
                            }
                            saveImage(url, subDir)
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
                imageUrl = images.getOrNull(if (images.size > 1) page % images.size else page) ?: "",
                onTap = { showBars = !showBars; autoHideJob?.cancel() },
                onLongPress = { showSaveDialog = true }
            )
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("保存图片") },
            text = { Text("将当前图片保存到本地？") },
            confirmButton = {
                TextButton(onClick = {
                    showSaveDialog = false
                    val url = images.getOrNull(currentPageIndex) ?: return@TextButton
                    val subDir = if (movie != null) {
                        "[${movie.code}] ${movie.title}"
                    } else {
                        "gallery"
                    }
                    saveImage(url, subDir)
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

