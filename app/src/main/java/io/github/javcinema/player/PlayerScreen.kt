package io.github.javcinema.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.provider.Settings
import android.view.SurfaceView
import android.view.ViewGroup
import android.webkit.CookieManager
import android.widget.FrameLayout
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.AspectRatioFrameLayout
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

    // 视频显示宽高比（宽/高），0 = 还不知道，按铺满处理。
    var videoAspectRatio by remember { mutableFloatStateOf(VideoLayoutPolicy.UNKNOWN_ASPECT_RATIO) }
    // 在组合期读一次，让本 Composable 订阅这个 state。
    //
    // 不能只在下面 AndroidView 的 update 里读：update 是布局期回调，
    // 那时读 state 拿不到订阅，比例更新了也不会触发重组，画面会一直停在首次的比例上。
    val frameAspectRatio = videoAspectRatio

    // 播放页的窗口设置：横屏 + 真·全屏。
    //
    // 1) 横屏：这里能安全旋转的前提是 MainActivity 的 configChanges 已声明
    //    `orientation|screenSize|smallestScreenSize` —— 旋转**不会**重建 Activity，
    //    所以 remember 出来的 ExoPlayer 与播放进度都能存活，画面不会从头开始。
    //    若哪天把这两个 configChanges 去掉，本效果会变成「一转屏就重播」。
    //    用 SENSOR_LANDSCAPE 而不是 LANDSCAPE：前者跟随重力，横过来哪边都行；
    //    后者锁死一个方向，手机翻个面画面不会跟着转。
    //
    // 2) 退出时怎么还回去**必须**走 PlayerOrientationPolicy，不能直接还原成
    //    UNSPECIFIED —— 自动旋转关闭时那样会「保持横屏」，还会把横屏写进系统级
    //    的 USER_ROTATION，导致退出播放器后整个应用都是横的。原因详见该文件注释。
    //
    // 3) 全屏：把状态栏和导航栏一起藏掉。这不只是为了好看 —— Scaffold 的
    //    contentWindowInsets 取自 systemBars，系统栏一旦隐藏，insets 归零，
    //    播放页拿到的顶部内边距也就变成 0，画面才真的铺满整块屏幕。
    //    用 WindowInsetsControllerCompat 而非老的 systemUiVisibility：Activity 已经
    //    enableEdgeToEdge()，老 flag 在部分 ROM 上会被忽略。
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val previousOrientation = activity?.requestedOrientation
        // 进入本页那一刻的物理朝向，退出时用它决定还回竖还是横。
        val orientationAtEntry = activity?.resources?.configuration?.orientation
            ?: Configuration.ORIENTATION_UNDEFINED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        val window = activity?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previousBarsBehavior = insetsController?.systemBarsBehavior
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        onDispose {
            activity?.requestedOrientation = PlayerOrientationPolicy.restoreOrientation(
                previousRequested = previousOrientation
                    ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                autoRotateEnabled = context.isAutoRotateEnabled(),
                orientationAtEntry = orientationAtEntry
            )
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            if (previousBarsBehavior != null) {
                insetsController.systemBarsBehavior = previousBarsBehavior
            }
        }
    }

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

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoAspectRatio = videoSize.displayAspectRatio()
            }
        }
        exoPlayer.player.addListener(listener)
        // 补一次主动读取：重进本页（或 prepare 早于本次组合）时尺寸可能已经就绪，
        // 只等 onVideoSizeChanged 会一直拿不到回调，画面就按铺满拉伸了。
        exoPlayer.player.videoSize.let { current ->
            videoAspectRatio = current.displayAspectRatio()
        }
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

    // 控件自动隐藏：可见、且手指没按着时，静置 3 秒就淡出，把画面让出来。
    // key 里带上 controlsIdleTick —— 任何一次触摸都会让它变化，倒计时因此重新开始，
    // 而不是沿用上一次的剩余时间。
    LaunchedEffect(
        playerController.isControlsVisible,
        playerController.isTouching,
        playerController.controlsIdleTick
    ) {
        val shouldHide = PlayerControlsPolicy.shouldAutoHide(
            controlsVisible = playerController.isControlsVisible,
            touching = playerController.isTouching
        )
        if (shouldHide) {
            delay(PlayerControlsPolicy.AUTO_HIDE_DELAY_MS)
            playerController.isControlsVisible = false
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
                // 用 AspectRatioFrameLayout 包住 SurfaceView，而不是把 SurfaceView 直接铺满。
                //
                // 为什么必须包一层：Media3 默认的 codec 缩放模式是 SCALE_TO_FIT，语义是
                // 「缩放到填满 surface」—— surface 是什么比例，画面就被拉成什么比例，
                // 跟视频自己的比例无关。所以 SurfaceView 一旦 MATCH_PARENT，在 20:9 的手机
                // 上放 16:9 的片子就会被横向拉扁。
                //
                // RESIZE_MODE_FIT 则是「保持比例缩到能完整放进父容器」：宁可上下（或左右）
                // 留黑边，也不裁掉画面边缘、也不变形。这就是「全屏但不裁剪」。
                AspectRatioFrameLayout(ctx).apply {
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setBackgroundColor(android.graphics.Color.BLACK)
                    val surfaceView = SurfaceView(ctx)
                    addView(
                        surfaceView,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                    exoPlayer.player.setVideoSurfaceView(surfaceView)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { frame ->
                // 比例未知时传 0，AspectRatioFrameLayout 会退化成「铺满父容器」。
                frame.setAspectRatio(frameAspectRatio)
            }
        )

        // 返回键跟随控件一起淡出：横屏看片时画面上不该常驻任何 UI。
        // 退出仍然有两条路 —— 系统返回手势/按键（上面注册了 BackHandler），
        // 或者点一下画面唤回控件再点返回。
        AnimatedVisibility(
            visible = playerController.isControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White
                )
            }
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

/**
 * 从 Compose 拿到的 Context 往上找到宿主 Activity。
 *
 * Compose 里 `LocalContext.current` 往往是 `ContextWrapper`（主题包装等），
 * 直接 `as Activity` 会 ClassCastException，必须沿 `baseContext` 往上找。
 */
private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * 把 Media3 的 [VideoSize] 交给 [VideoLayoutPolicy]。
 *
 * `unappliedRotationDegrees` 在 media3 1.5 已标记废弃（新版渲染器会自己把旋转应用上），
 * 但字段还在、老版本仍会给出非零值，所以照样传进去做防御；这里统一压掉废弃告警。
 */
@Suppress("DEPRECATION")
private fun VideoSize.displayAspectRatio(): Float = VideoLayoutPolicy.displayAspectRatio(
    width = width,
    height = height,
    unappliedRotationDegrees = unappliedRotationDegrees,
    pixelWidthHeightRatio = pixelWidthHeightRatio
)

/**
 * 系统「自动旋转」是否开启。
 *
 * 读这个系统设置不需要任何权限。`PlayerOrientationPolicy` 需要它来区分
 * 「交回系统跟随传感器」和「必须显式指定方向」两种恢复方式。
 */
private fun Context.isAutoRotateEnabled(): Boolean = runCatching {
    Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1
}.getOrDefault(true)

private fun formatDuration(millis: Long): String {
    if (millis <= 0L) return "00:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
