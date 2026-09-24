package io.github.javcinema.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.provider.Settings
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import kotlinx.coroutines.delay

// ⚠️ 注意这里用的是 androidx.annotation.OptIn，**不是** kotlin.OptIn。
// Media3 的 UnstableApi 是 AndroidX 的 lint 注解（没有 @RequiresOptIn 元注解），
// kotlin.OptIn 对它无效 —— 只会换来一条 "has no effect" 警告。
// 本页用到的 AspectRatioFrameLayout / RESIZE_MODE_FIT / setVideoSurfaceView
// 都属于 @UnstableApi，必须这样 opt-in 才能过 Lint。
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    url: String,
    modifier: Modifier = Modifier,
    referer: String = "",
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val exoPlayer = remember { ExoPlayerImpl(context) }
    val playerController = remember { SimpleVideoPlayer(context) }

    // 退出分两步走：按返回 → 先把方向还回去 → 等窗口真的转回竖屏 → 再 pop。
    // 反过来（先 pop 再转）上一页会在横屏窗口里被渲染约 0.7s，看起来就是画面撕裂。
    // 完整原因见 PlayerExitPolicy 的注释。
    var exiting by remember { mutableStateOf(false) }

    // 方向恢复要用的两个「进入时快照」。必须在组合期取：下面的 DisposableEffect
    // 一执行就把方向改成横屏了，之后再去读 `requestedOrientation` 就拿不到原值。
    val activity = remember(context) { context.findActivity() }
    val previousOrientation = remember(activity) {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    val orientationAtEntry = remember(activity) {
        activity?.resources?.configuration?.orientation ?: Configuration.ORIENTATION_UNDEFINED
    }

    /**
     * 把屏幕方向还给进入播放器前的状态。
     *
     * 退出路径（[requestExit]）与 `onDispose` 兜底都走这一份实现，避免两处逻辑漂移。
     */
    fun restoreOrientation() {
        activity?.requestedOrientation = PlayerOrientationPolicy.restoreOrientation(
            previousRequested = previousOrientation,
            autoRotateEnabled = context.isAutoRotateEnabled(),
            orientationAtEntry = orientationAtEntry
        )
    }

    /** 请求退出：先还方向，等窗口转回竖屏后由下面的 LaunchedEffect 真正 pop。 */
    fun requestExit() {
        if (exiting) return
        exiting = true
        restoreOrientation()
    }

    // 视频显示宽高比（宽/高），0 = 还不知道，按铺满处理。
    var videoAspectRatio by remember { mutableFloatStateOf(VideoLayoutPolicy.UNKNOWN_ASPECT_RATIO) }
    // 在组合期读一次，让本 Composable 订阅这个 state。
    //
    // 不能只在下面 AndroidView 的 update 里读：update 是布局期回调，
    // 那时读 state 拿不到订阅，比例更新了也不会触发重组，画面会一直停在首次的比例上。
    val frameAspectRatio = videoAspectRatio

    // 画面比例同理：也要在组合期读一次，update 是布局期回调，在那里读 state 拿不到订阅，
    // 用户点了「适应 / 裁剪 / 拉伸」按钮画面不会变。
    //
    // ⚠️ 变量名**不能**叫 `resizeMode`：下面 `AspectRatioFrameLayout(...).apply { }` 里
    // 要写 `resizeMode = RESIZE_MODE_*`，而 Kotlin 解析标识符时局部变量的优先级高于
    // `apply` 的隐式接收者 —— 同名会被解析成这个 val，直接报「val 不能重新赋值」。
    val videoResizeMode = playerController.resizeMode

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
    //    **还方向的时机也很关键**：必须先把方向还回去、等窗口真的转回竖屏、再 pop。
    //    原来的顺序（先 pop 再由 onDispose 还方向）会让上一页在横屏窗口里被渲染
    //    约 0.7s，看起来就是画面撕裂。详见 PlayerExitPolicy。
    //
    // 3) 全屏：把状态栏和导航栏一起藏掉。这不只是为了好看 —— Scaffold 的
    //    contentWindowInsets 取自 systemBars，系统栏一旦隐藏，insets 归零，
    //    播放页拿到的顶部内边距也就变成 0，画面才真的铺满整块屏幕。
    //    用 WindowInsetsControllerCompat 而非老的 systemUiVisibility：Activity 已经
    //    enableEdgeToEdge()，老 flag 在部分 ROM 上会被忽略。
    //
    // 4) 屏幕常亮：播放中不该被系统息屏打断。用 Window flag 而不是 View.keepScreenOn ——
    //    本页是全屏沉浸式，flag 挂在 Activity 窗口上，不受视图树重组/替换影响。
    //    退出时只在「进来之前本来没设」的情况下清掉，避免误伤别人的设置。
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        val window = activity?.window
        val keepScreenOnAlreadySet =
            window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        if (!keepScreenOnAlreadySet) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previousBarsBehavior = insetsController?.systemBarsBehavior
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        onDispose {
            // 正常退出时 requestExit() 已经把方向还回去了；这里是「没走退出路径就被
            // 移出组合」的兜底。幂等，重复设置没有副作用。
            restoreOrientation()
            if (!keepScreenOnAlreadySet) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
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

    // 退出过程中禁掉 BackHandler：否则连按两次返回时，第二次会绕过「等转屏」直接 pop，
    // 等于把刚修好的顺序又破坏掉。
    BackHandler(enabled = !exiting) { requestExit() }

    // 等窗口真的转回竖屏再 pop。用轮询而不是靠配置变化触发重组：判据抽成了纯函数
    // decidePlayerExit（有单测覆盖），而 50ms 一次的轮询对一次性的退出流程可以忽略。
    LaunchedEffect(exiting) {
        if (!exiting) return@LaunchedEffect
        val host = activity
        if (host == null) {
            onBackClick()
            return@LaunchedEffect
        }
        val startMs = SystemClock.elapsedRealtime()
        while (true) {
            val orientation = host.resources.configuration.orientation
            val waitedMs = SystemClock.elapsedRealtime() - startMs
            if (decidePlayerExit(orientation, waitedMs) == PlayerExitDecision.POP) break
            delay(PLAYER_EXIT_POLL_INTERVAL_MS)
        }
        onBackClick()
        // 兜底：万一 popBackStack() 没成功（回退栈空等），本 Composable 会继续活着。
        // 此时必须把 exiting 复位，否则 BackHandler 一直禁着，用户会被困在播放页。
        // 正常退出时本页已被移出组合、协程被取消，这行不会执行。
        delay(PLAYER_EXIT_POP_CONFIRM_MS)
        exiting = false
    }

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
                playerController.playbackState = when {
                    isPlaying -> PlayerPlaybackState.PLAYING
                    playerController.playbackState == PlayerPlaybackState.BUFFERING ->
                        PlayerPlaybackState.BUFFERING
                    // ⚠️ 不能把 COMPLETED 降级成 PAUSED。
                    //
                    // ExoPlayer 播完时会**连着**发两个回调，顺序固定：
                    // 先 `onPlaybackStateChanged(STATE_ENDED)`，再 `onIsPlayingChanged(false)`。
                    // 这里若无条件写 PAUSED，就会把上一步刚设好的 COMPLETED 覆盖掉 ——
                    // 结果是 `COMPLETED` 分支（画面正中的「重播」浮层）**永远不显示**，
                    // 用户看到的是「播完了，进度条停在末尾，点什么都没反应」。
                    // 这个分支以前是死的，实测才发现（进度条与总时长都显示 00:37 而浮层没出来）。
                    playerController.playbackState == PlayerPlaybackState.COMPLETED ->
                        PlayerPlaybackState.COMPLETED
                    else -> PlayerPlaybackState.PAUSED
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
            // 进度条上的「已缓存」那一段。HLS 是分片拉取的，这个值会跳着涨。
            playerController.bufferedPosition = exoPlayer.getBufferedPosition()
        }
    }

    // 控件自动隐藏：可见、手指没按着、且正在播放时，静置 3 秒就淡出，把画面让出来。
    // 暂停 / 缓冲 / 出错时控件会留在画面上（见 PlayerControlsPolicy.shouldAutoHide）。
    // key 里带上 controlsIdleTick —— 任何一次触摸都会让它变化，倒计时因此重新开始，
    // 而不是沿用上一次的剩余时间。
    LaunchedEffect(
        playerController.isControlsVisible,
        playerController.isTouching,
        playerController.controlsIdleTick,
        playerController.playbackState
    ) {
        val shouldHide = PlayerControlsPolicy.shouldAutoHide(
            controlsVisible = playerController.isControlsVisible,
            touching = playerController.isTouching,
            playing = playerController.playbackState == PlayerPlaybackState.PLAYING
        )
        if (shouldHide) {
            delay(PlayerControlsPolicy.AUTO_HIDE_DELAY_MS)
            playerController.isControlsVisible = false
        }
    }

    // 单击切换控件要**等过了双击窗口**再执行。
    //
    // 不延后的话，双击的第一下会先把控件翻一下、第二下再翻回来，中间那一下就是
    // 肉眼可见的闪烁；延后的代价是单击有 300ms 延迟 —— 这是「单击 + 双击共存」
    // 绕不开的取舍。双击时 [SimpleVideoPlayer.consumePendingTap] 会返回 false，
    // 这次排队就作废，改成执行快进/快退。
    LaunchedEffect(playerController.pendingTapTick) {
        if (playerController.pendingTapTick == 0L) return@LaunchedEffect
        delay(DOUBLE_TAP_TIMEOUT_MS)
        if (playerController.consumePendingTap()) {
            playerController.toggleControls()
        }
    }

    // 长按快放：按住不动满 500ms 就临时 3x。手指一离开 isTouching 变 false，本效果自动取消。
    // 判定条件在 onLongPressTick 里会再自查一遍 —— 计时器和手指状态之间有竞态。
    LaunchedEffect(playerController.isTouching) {
        if (!playerController.isTouching) return@LaunchedEffect
        delay(LONG_PRESS_TIMEOUT_MS)
        playerController.onLongPressTick(exoPlayer)
    }

    // 双击快进/快退的浮层，1 秒后自动消失。用 tick 当 key 而不是文案本身 ——
    // 连续两次「+10秒」文案没变，只按文案当 key 不会重新计时。
    LaunchedEffect(playerController.seekFlashTick) {
        if (playerController.seekFlashTick == 0L) return@LaunchedEffect
        delay(SEEK_FLASH_DURATION_MS)
        playerController.clearSeekFlash()
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
                // 用户可以在控制栏里切成 ZOOM（裁剪）/ FILL（拉伸），见 VideoResizeMode。
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
                frame.resizeMode = videoResizeMode.toFrameLayoutResizeMode()
            }
        )

        // 手势层：**必须是独立的一层，而且必须排在控件下面**。
        //
        // 原先这段 pointerInput 挂在 AndroidView 上。加可拖进度条之后就出问题了：
        // 同一次拖动会同时喂给「全屏快进」和「进度条拖拽」，两个都在跳，还互相打架。
        //
        // 现在靠 Compose 兄弟节点的派发顺序解决：Main 阶段**从最上层往下**派发，
        // 所以控件先拿到事件；控件里的 clickable / 进度条会把 down 消费掉，
        // 本层看到 `isConsumed` 就整段跳过。
        // 顺带修掉一个老毛病：点播放键、点倍速键原本会**顺带把控件收起来**。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            // 控件已经接手的事件一律不碰。
                            if (event.changes.any { it.isConsumed }) continue
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
                onClick = { requestExit() },
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
                    !playerController.isLocked &&
                    playerController.gestureMode == GestureMode.NONE &&
                    playerController.playbackState != PlayerPlaybackState.COMPLETED,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            PlayerControls(
                // 拖动进度条期间显示手指所在的位置，而不是播放器还没跳过去的位置 ——
                // 否则松手前进度条会「黏」在旧进度上，看起来像没拖动。
                positionMs = if (playerController.isScrubbing) {
                    playerController.scrubPosition
                } else {
                    playerController.currentPosition
                },
                bufferedMs = playerController.bufferedPosition,
                durationMs = playerController.duration,
                isPlaying = exoPlayer.isPlaying(),
                isScrubbing = playerController.isScrubbing,
                speedLabel = formatPlaybackSpeed(playerController.playbackSpeed),
                resizeLabel = playerController.resizeMode.label,
                onTogglePlay = { exoPlayer.togglePlay() },
                onScrubStart = { x, width -> playerController.beginScrub(x, width) },
                onScrubMove = { x, width -> playerController.updateScrub(x, width) },
                onScrubEnd = { playerController.endScrub(exoPlayer) },
                onCycleSpeed = { playerController.cyclePlaybackSpeed(exoPlayer) },
                onCycleResize = { playerController.cycleResizeMode() },
                onLock = { playerController.toggleLock(exoPlayer) }
            )
        }

        AnimatedVisibility(
            visible = playerController.isControlsVisible &&
                    !playerController.isLocked &&
                    playerController.gestureMode == GestureMode.NONE &&
                    playerController.playbackState != PlayerPlaybackState.COMPLETED &&
                    playerController.playbackState != PlayerPlaybackState.BUFFERING &&
                    // 双击浮层出现时让位：浮层是半透明黑底，压在白色播放键上会透出鬼影。
                    playerController.seekFlashText.isEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            // ⚠️ 必须显式给 IconButton 尺寸。M3 的 IconButton 内部是
            // `size(IconButtonTokens.StateLayerSize)` = **40dp**，所以原先的
            // `Modifier.fillMaxSize(0.4f)` 实际只画出 **16dp** 的图标 ——
            // 在 2400px 宽的横屏上基本看不见。
            IconButton(
                onClick = { exoPlayer.togglePlay() },
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = if (exoPlayer.isPlaying()) Icons.Default.Pause
                    else Icons.Default.PlayArrow,
                    contentDescription = if (exoPlayer.isPlaying()) "暂停" else "播放",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // 双击快进/快退的浮层。放在正中，和拖动快进的浮层同一个位置 ——
        // 两种操作本来就是同一件事，位置一致用户才不会看花。
        AnimatedVisibility(
            visible = playerController.seekFlashText.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            SeekFlashOverlay(text = playerController.seekFlashText)
        }

        // 长按快放的提示。放在顶部，避免和画面正中的播放键、浮层打架。
        AnimatedVisibility(
            visible = playerController.isLongPressSpeedActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            LongPressSpeedBadge()
        }

        // 锁定时**只留**这一个解锁按钮，而且刻意常驻、不跟控件一起淡出 ——
        // 跟控件走的话，锁屏后用户根本找不到解锁入口，只能退出去。
        if (playerController.isLocked) {
            IconButton(
                onClick = { playerController.toggleLock(exoPlayer) },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = "解锁",
                    tint = Color.White
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

/**
 * 底部控制栏。**只有一行** —— 横屏看片时画面高度本来就紧张，
 * 两行（进度条一行 + 按钮一行）会吃掉近 60dp，等于把画面顶上去。
 *
 * 从左到右：播放/暂停 · 当前时间 · 可拖进度条 · 总时长 · 倍速 · 画面比例 · 锁定。
 *
 * ⚠️ 倍速 / 比例这两个按钮用的是 [Text] + `clickable`，**不是** `TextButton`：
 * `TextButton` 内部硬编码了 `ButtonDefaults.MinHeight = 40.dp`，会把整条栏撑高，
 * 和「只有一行」的目标直接冲突（人机验证提示条上踩过同一个坑）。
 */
@Composable
private fun PlayerControls(
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    isScrubbing: Boolean,
    speedLabel: String,
    resizeLabel: String,
    onTogglePlay: () -> Unit,
    onScrubStart: (Float, Float) -> Unit,
    onScrubMove: (Float, Float) -> Unit,
    onScrubEnd: () -> Unit,
    onCycleSpeed: () -> Unit,
    onCycleResize: () -> Unit,
    onLock: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(onClick = onTogglePlay) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                tint = Color.White
            )
        }

        Text(
            text = formatPlaybackTime(positionMs),
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false
        )

        ScrubBar(
            positionMs = positionMs,
            bufferedMs = bufferedMs,
            durationMs = durationMs,
            isScrubbing = isScrubbing,
            onScrubStart = onScrubStart,
            onScrubMove = onScrubMove,
            onScrubEnd = onScrubEnd,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = formatPlaybackTime(durationMs),
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false
        )

        ControlsChip(text = speedLabel, onClick = onCycleSpeed)
        ControlsChip(text = resizeLabel, onClick = onCycleResize)

        IconButton(onClick = onLock) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "锁定",
                tint = Color.White
            )
        }
    }
}

/**
 * 进度条：已缓存段 + 已播放段 + 可拖拽的圆点。
 *
 * 用 [Canvas] 手画而不是叠三个 `Box`：三段的圆角、圆点位置都要按比例算，
 * 叠 `Box` 得靠 `fillMaxWidth(fraction)` 反复测宽，圆角还会因为「段太短」而算错。
 *
 * ⚠️ 触摸区高 24dp、可视条高 3dp。视觉细是为了不挡画面，但**触摸区不能跟着细**，
 * 3dp 的条子在手指下根本按不住。
 */
@Composable
private fun ScrubBar(
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    isScrubbing: Boolean,
    onScrubStart: (Float, Float) -> Unit,
    onScrubMove: (Float, Float) -> Unit,
    onScrubEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = progressFraction(positionMs, durationMs)
    // 已缓存不可能少于已播放，取 max 兜住「HLS 缓存位置偶尔落后于播放位置」的抖动。
    val buffered = progressFraction(bufferedMs, durationMs).coerceAtLeast(progress)

    // 按下/拖动时条子变粗、圆点变大。
    // ⚠️ 只动**粗细**，不动位置 —— 位置是手指 x 的线性换算，给它加动画
    // 会让圆点落后于手指，跟手就没了。这两个值只影响观感，与「拖到哪」无关。
    val barHeightDp by animateDpAsState(
        targetValue = if (isScrubbing) SCRUB_BAR_HEIGHT_DRAGGING else SCRUB_BAR_HEIGHT,
        animationSpec = tween(SCRUB_FEEDBACK_DURATION_MS),
        label = "scrubBarHeight"
    )
    val thumbRadiusDp by animateDpAsState(
        targetValue = if (isScrubbing) SCRUB_THUMB_RADIUS_DRAGGING else SCRUB_THUMB_RADIUS,
        animationSpec = tween(SCRUB_FEEDBACK_DURATION_MS),
        label = "scrubThumbRadius"
    )

    Canvas(
        modifier = modifier
            .height(SCRUB_TOUCH_HEIGHT)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        // ⚠️ 不要求事件「未被消费」：全屏手势层是个铺满屏幕的兄弟节点，
                        // 它可能先看到 down。这里必须无条件接住 —— 进度条拖不动比重复响应更糟。
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val width = size.width.toFloat()
                        onScrubStart(down.position.x, width)
                        // ⚠️ 立刻消费掉 down：否则下面的全屏手势层会把它当成
                        // 「横向拖动快进」，一边拖进度条一边弹快进浮层。
                        down.consume()

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            // ⚠️ 抬手事件**也必须消费**。
                            //
                            // 手势层是个铺满屏幕的兄弟节点，它只跳过「有任何 change 被消费」的事件。
                            // 这里若只消费 MOVE、不消费 UP，那个 UP 就是「未被消费」的 ——
                            // 手势层会把它当成一次单击，300ms 后调用 toggleControls() 把控件收起来。
                            // 症状：**拖完进度条一松手，整条控制栏自己消失了**（实测复现）。
                            change.consume()
                            if (!change.pressed) break
                            onScrubMove(change.position.x, width)
                        }
                        // 抬手 / 事件流中断都要收尾，否则 isScrubbing 会一直挂着，
                        // 全屏手势从此全部失效（用户感觉是「播放器卡死了」）。
                        onScrubEnd()
                    }
                }
            }
    ) {
        val barHeight = barHeightDp.toPx()
        val centerY = size.height / 2f
        val radius = CornerRadius(barHeight / 2f)
        val top = centerY - barHeight / 2f

        drawRoundRect(
            color = SCRUB_TRACK_COLOR,
            topLeft = Offset(0f, top),
            size = Size(size.width, barHeight),
            cornerRadius = radius
        )
        if (buffered > 0f) {
            drawRoundRect(
                color = SCRUB_BUFFERED_COLOR,
                topLeft = Offset(0f, top),
                size = Size(size.width * buffered, barHeight),
                cornerRadius = radius
            )
        }
        if (progress > 0f) {
            drawRoundRect(
                color = SCRUB_PROGRESS_COLOR,
                topLeft = Offset(0f, top),
                size = Size(size.width * progress, barHeight),
                cornerRadius = radius
            )
        }
        drawCircle(
            color = SCRUB_PROGRESS_COLOR,
            radius = thumbRadiusDp.toPx(),
            center = Offset(size.width * progress, centerY)
        )
    }
}

/** 控制栏上的文字按钮（倍速 / 画面比例）。刻意不用 `TextButton`，见 [PlayerControls] 的注释。 */
@Composable
private fun ControlsChip(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 12.sp,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

/** 双击快进/快退的浮层。文案形如 `+10秒` / `-10秒`。 */
@Composable
private fun SeekFlashOverlay(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(horizontal = 24.dp, vertical = 12.dp)
    )
}

/** 长按快放的提示徽标。放顶部，避开画面正中的播放键和浮层。 */
@Composable
private fun LongPressSpeedBadge() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = Icons.Default.FastForward,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = "快放中 ${formatPlaybackSpeed(LONG_PRESS_SPEED)}",
            color = Color.White,
            fontSize = 14.sp
        )
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

/** 进度条的可视高度。细是为了不挡画面。 */
private val SCRUB_BAR_HEIGHT = 3.dp

/**
 * 拖动中的进度条高度。
 *
 * ⚠️ 只加**视觉反馈**，不动进度值本身 —— 进度值必须是手指 x 的线性换算（跟手），
 * 给它加动画只会让圆点落后于手指。这里放大的是条子与圆点的**粗细**，
 * 与「拖到哪」无关，所以不会破坏跟手。
 */
private val SCRUB_BAR_HEIGHT_DRAGGING = 6.dp

/**
 * 进度条的**触摸区**高度。
 *
 * ⚠️ 不能跟可视高度一样细 —— 3dp 的条子在手指下根本按不住。
 * 24dp 是「不用瞄准也能拖到」和「不占太多画面」之间的折中。
 */
private val SCRUB_TOUCH_HEIGHT = 24.dp

/** 进度条圆点的半径。比条子粗，让人一眼看出这里是可拖的。 */
private val SCRUB_THUMB_RADIUS = 5.dp

/** 按住/拖动时圆点放大到这么大 —— 「我抓住它了」的即时反馈。 */
private val SCRUB_THUMB_RADIUS_DRAGGING = 8.dp

/**
 * 进度条按下反馈的动画时长。
 *
 * ⚠️ 必须**短**：这是对「手指已经按下去」的响应，超过 ~150ms 就会被感觉成「卡」，
 * 反而不像跟手。120ms 是「看得见动画」和「不觉得延迟」之间的折中。
 */
private const val SCRUB_FEEDBACK_DURATION_MS = 120

private val SCRUB_TRACK_COLOR = Color.White.copy(alpha = 0.3f)

/**
 * 已缓存段。
 *
 * 刻意比 [SCRUB_TRACK_COLOR] 亮、比 [SCRUB_PROGRESS_COLOR] 暗 ——
 * 三段要一眼能分开，否则「缓冲到哪了」这个信息等于没给。
 */
private val SCRUB_BUFFERED_COLOR = Color.White.copy(alpha = 0.55f)

private val SCRUB_PROGRESS_COLOR = Color(0xFFFF4081)

/** 已播放 / 已缓存 占时长的比例。时长未知时一律 0（没有可换算的坐标系）。 */
private fun progressFraction(valueMs: Long, durationMs: Long): Float =
    if (durationMs > 0L) (valueMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

/**
 * 把 [VideoResizeMode] 映射到 Media3 的 `RESIZE_MODE_*` 常量。
 *
 * ⚠️ 映射**必须**留在这里，不能让 `VideoResizePolicy` 去引用这几个常量：
 * 它们带 `@UnstableApi`，引过去会让那个纯策略文件也得加 `@OptIn`、还没法跑 JVM 单测。
 */
@androidx.annotation.OptIn(UnstableApi::class)
private fun VideoResizeMode.toFrameLayoutResizeMode(): Int = when (this) {
    VideoResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    VideoResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    VideoResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
}
