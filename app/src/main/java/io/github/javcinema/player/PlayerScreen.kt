package io.github.javcinema.player

import android.view.SurfaceView
import android.view.ViewGroup
import android.webkit.CookieManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    url: String,
    referer: String = "",
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val exoPlayer = remember { ExoPlayerImpl(context) }
    val playerController = remember { SimpleVideoPlayer(context) }
    var progress by remember { mutableFloatStateOf(0f) }

    // 站点直链通常校验来源，带上 Referer 与会话 Cookie 才可能放行。
    fun streamHeaders(): Map<String, String> = buildMap {
        if (referer.isNotBlank()) put("Referer", referer)
        CookieManager.getInstance().getCookie(url)
            ?.takeIf { it.isNotBlank() }
            ?.let { put("Cookie", it) }
    }

    LaunchedEffect(url) {
        if (url.isNotBlank()) {
            exoPlayer.prepare(context, url, streamHeaders())
        }
    }

    BackHandler { onBackClick() }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                playerController.playbackState = when (playbackState) {
                    Player.STATE_IDLE -> PlayerPlaybackState.IDLE
                    Player.STATE_BUFFERING -> PlayerPlaybackState.BUFFERING
                    Player.STATE_READY -> PlayerPlaybackState.PLAYING
                    Player.STATE_ENDED -> PlayerPlaybackState.COMPLETED
                    else -> PlayerPlaybackState.IDLE
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playerController.playbackState = if (isPlaying) {
                    PlayerPlaybackState.PLAYING
                } else if (playerController.playbackState == PlayerPlaybackState.BUFFERING) {
                    PlayerPlaybackState.BUFFERING
                } else {
                    PlayerPlaybackState.PAUSED
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                playerController.playbackState = PlayerPlaybackState.ERROR
            }
        }
        exoPlayer.player.addListener(listener)
        onDispose {
            exoPlayer.player.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(200)
            val pos = exoPlayer.getCurrentPosition()
            val dur = exoPlayer.getDuration()
            playerController.currentPosition = pos
            playerController.duration = dur
            if (dur > 0) {
                progress = (pos.toFloat() / dur).coerceIn(0f, 1f)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.firstOrNull() ?: break
                            val x = pointer.position.x
                            val y = pointer.position.y
                            when {
                                pointer.pressed && pointer.previousPressed.not() -> {
                                    playerController.onTouchDown(
                                        x, y,
                                        size.width
                                    )
                                }
                                pointer.pressed -> {
                                    playerController.onTouchMove(
                                        x, y,
                                        size.width, size.height
                                    )
                                }
                                pointer.previousPressed && pointer.pressed.not() -> {
                                    playerController.onTouchUp(exoPlayer)
                                }
                            }
                        }
                    }
                }
                .then(
                    Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                ),
            factory = { ctx ->
                SurfaceView(ctx).also { surfaceView ->
                    exoPlayer.player.setVideoSurfaceView(surfaceView)
                    surfaceView.layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            }
        )

        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = Color.White
            )
        }

        when (playerController.playbackState) {
            PlayerPlaybackState.BUFFERING -> {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(48.dp)
                )
                Text(
                    text = "加载中...",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center).padding(top = 56.dp)
                )
            }
            PlayerPlaybackState.ERROR -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "重试",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp).clickable {
                            exoPlayer.prepare(context, url, streamHeaders())
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "加载失败，点击重试",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }
            }
            PlayerPlaybackState.COMPLETED -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    IconButton(onClick = { exoPlayer.seekTo(0); exoPlayer.play() }) {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = "重播",
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                    Text(
                        text = "重播",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }
            else -> {}
        }

        AnimatedVisibility(
            visible = playerController.gestureMode == GestureMode.SEEK,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            SeekOverlay(
                seekTime = playerController.seekTimeText,
                totalTime = playerController.totalTimeText
            )
        }

        AnimatedVisibility(
            visible = playerController.gestureMode == GestureMode.VOLUME,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            VolumeBrightnessOverlay(
                value = playerController.volumePercent,
                label = "音量"
            )
        }

        AnimatedVisibility(
            visible = playerController.gestureMode == GestureMode.BRIGHTNESS,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            VolumeBrightnessOverlay(
                value = playerController.brightnessPercent,
                label = "亮度"
            )
        }

        AnimatedVisibility(
            visible = playerController.isControlsVisible &&
                    playerController.gestureMode == GestureMode.NONE &&
                    playerController.playbackState != PlayerPlaybackState.COMPLETED,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            PlayerControls(
                progress = progress,
                isPlaying = exoPlayer.isPlaying(),
                onTogglePlay = { exoPlayer.togglePlay() },
                currentPosition = exoPlayer.getCurrentPosition(),
                duration = exoPlayer.getDuration(),
                onSeek = { exoPlayer.seekTo(it) }
            )
        }

        AnimatedVisibility(
            visible = playerController.isControlsVisible &&
                    playerController.gestureMode == GestureMode.NONE &&
                    playerController.playbackState != PlayerPlaybackState.COMPLETED &&
                    playerController.playbackState != PlayerPlaybackState.BUFFERING,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            IconButton(onClick = { exoPlayer.togglePlay() }) {
                Icon(
                    imageVector = if (exoPlayer.isPlaying()) Icons.Default.Pause
                    else Icons.Default.PlayArrow,
                    contentDescription = if (exoPlayer.isPlaying()) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.fillMaxSize(0.4f)
                )
            }
        }
    }
}

@Composable
private fun SeekOverlay(seekTime: String, totalTime: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = seekTime,
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = totalTime,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun VolumeBrightnessOverlay(value: Int, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .background(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "$value%",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PlayerControls(
    progress: Float,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    currentPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = Color(0xFFFF4081),
            trackColor = Color.White.copy(alpha = 0.3f),
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatDuration(currentPosition),
                color = Color.White,
                fontSize = 12.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause
                        else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White
                    )
                }
            }
            Text(
                text = formatDuration(duration),
                color = Color.White,
                fontSize = 12.sp
            )
        }
    }
}

private fun formatDuration(millis: Long): String {
    if (millis <= 0L) return "00:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
