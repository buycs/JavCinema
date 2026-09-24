package io.github.javcinema.player

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class PlayerPlaybackState {
    IDLE, BUFFERING, PLAYING, PAUSED, COMPLETED, ERROR
}

enum class GestureMode {
    NONE, SEEK, VOLUME, BRIGHTNESS
}

/**
 * 播放页的手势与控件状态。
 *
 * 这里只放**状态**和**状态迁移**，判定阈值全部来自 `*Policy.kt` 的纯函数（有单测）。
 * 之所以这么切：手势在模拟器上很难稳定复现边界（「隔多久算双击」「按住多久算长按」），
 * 纯函数能单测，这里只负责把事件喂进去。
 *
 * 手势分工：
 * | 操作 | 效果 |
 * |---|---|
 * | 单击 | 切换控件显隐（**延后** 300ms 执行，见 [consumePendingTap]） |
 * | 双击左 / 右 40% | 快退 / 快进 10 秒 |
 * | 横向拖动 | 快进 / 快退（按位移比例） |
 * | 左半屏纵向拖动 | 亮度 |
 * | 右半屏纵向拖动 | 音量 |
 * | 长按不动 500ms | 临时 3x 快放，松手还原 |
 * | 锁定后 | 以上全部失效，只留解锁按钮 |
 */
class SimpleVideoPlayer(private val context: Context) {

    var playbackState: PlayerPlaybackState by mutableStateOf(PlayerPlaybackState.IDLE)
    var currentPosition: Long by mutableLongStateOf(0L)
    var duration: Long by mutableLongStateOf(0L)

    /** 已缓冲到的位置。进度条用它画「已缓存」那一段。 */
    var bufferedPosition: Long by mutableLongStateOf(0L)

    var volumePercent: Int by mutableIntStateOf(50)
    var brightnessPercent: Int by mutableIntStateOf(100)
    var isControlsVisible: Boolean by mutableStateOf(true)

    /** 手指是否正按在画面上。按住期间不自动隐藏控件。 */
    var isTouching: Boolean by mutableStateOf(false)
        private set

    /**
     * 「用户动了」的计数器，每次触摸都会 +1。
     *
     * 自动隐藏是 `LaunchedEffect` + `delay` 实现的，把这个值放进 key 里，
     * 就能让任何一次触摸都重新开始倒计时（而不是沿用上一次的剩余时间）。
     */
    var controlsIdleTick: Long by mutableLongStateOf(0L)
        private set

    var gestureMode: GestureMode by mutableStateOf(GestureMode.NONE)
    var seekTimeText: String by mutableStateOf("")
    var totalTimeText: String by mutableStateOf("")

    /** 锁屏。锁定时所有全屏手势都不响应，界面上只留一个解锁按钮。 */
    var isLocked: Boolean by mutableStateOf(false)
        private set

    /** 用户自己选的倍速。长按快放结束后要**还回它**，不能留在 3x。 */
    var playbackSpeed: Float by mutableFloatStateOf(DEFAULT_PLAYBACK_SPEED)
        private set

    /** 长按快放是否正在生效。 */
    var isLongPressSpeedActive: Boolean by mutableStateOf(false)
        private set

    /** 画面比例模式。 */
    var resizeMode: VideoResizeMode by mutableStateOf(VideoResizeMode.FIT)
        private set

    /** 正在拖进度条。拖动期间全屏手势要让路，控件也不自动隐藏。 */
    var isScrubbing: Boolean by mutableStateOf(false)
        private set

    /** 拖动中的目标位置（毫秒）。非拖动期间无意义。 */
    var scrubPosition: Long by mutableLongStateOf(0L)
        private set

    /** 双击快进/快退的浮层文案；空串 = 不显示。 */
    var seekFlashText: String by mutableStateOf("")
        private set

    /** 双击浮层的重放计数 —— 连续两次同样的「+10秒」也要能重新计时。 */
    var seekFlashTick: Long by mutableLongStateOf(0L)
        private set

    /**
     * 「有一次单击待处理」的计数器。
     *
     * ⚠️ 单击切换控件是**延后** 300ms 执行的，不延后的话双击的第一下会先把控件翻一下、
     * 第二下再翻回来，中间那一下就是肉眼可见的闪烁。延后的代价是单击有 300ms 延迟 ——
     * 这是「单击 + 双击共存」绕不开的取舍。
     */
    var pendingTapTick: Long by mutableLongStateOf(0L)
        private set

    private var pendingTapActive = false

    private var downX = 0f
    private var downY = 0f
    private var screenWidthPx = 0f
    private var gestureDownPosition: Long = 0L
    private var gestureDownVolume: Int = 0
    private var gestureDownBrightness: Float = 0f
    private var seekTimePosition: Long = 0L
    private var speedBeforeLongPress: Float = DEFAULT_PLAYBACK_SPEED

    /**
     * 上一次「单击抬手」的时刻。
     *
     * 用 -1 而不是 `Long.MIN_VALUE` 当「没有上一次」：双击判定要做 `now - last`，
     * 拿 `Long.MIN_VALUE` 去减会**溢出**成负数，正好落进双击窗口里。
     */
    private var lastTapUpMs = -1L

    companion object {
        private const val THRESHOLD = 10
        private const val SEEK_SCALE = 90000
    }

    private val audioManager: AudioManager
        get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // ---------------------------------------------------------------- 全屏手势

    fun onTouchDown(x: Float, y: Float, screenWidth: Int) {
        if (isLocked) return
        isTouching = true
        controlsIdleTick++
        downX = x
        downY = y
        screenWidthPx = screenWidth.toFloat()
        gestureMode = GestureMode.NONE
        gestureDownPosition = currentPosition
        seekTimePosition = currentPosition

        if (x < screenWidth * 0.5f) {
            gestureDownBrightness = try {
                Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS
                ).toFloat()
            } catch (_: Exception) {
                255f
            }
        } else {
            gestureDownVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        }
    }

    fun onTouchMove(x: Float, y: Float, screenWidth: Int, screenHeight: Int) {
        if (isLocked) return
        // 长按快放期间不接受拖动：否则会一边 3x 一边触发快进/音量，两个都乱。
        if (isLongPressSpeedActive) return

        val deltaX = x - downX
        val deltaY = y - downY
        val absDeltaX = kotlin.math.abs(deltaX)
        val absDeltaY = kotlin.math.abs(deltaY)

        if (gestureMode == GestureMode.NONE) {
            when {
                absDeltaX > THRESHOLD && playbackState != PlayerPlaybackState.ERROR -> {
                    gestureMode = GestureMode.SEEK
                }
                absDeltaY > THRESHOLD && downX < screenWidth * 0.5f -> {
                    gestureMode = GestureMode.BRIGHTNESS
                }
                absDeltaY > THRESHOLD -> {
                    gestureMode = GestureMode.VOLUME
                }
            }
            // 一旦确认是「拖动」而不是「点击」，就把控件收起来，
            // 让快进 / 音量 / 亮度浮层单独显示，别和进度条叠在一起。
            if (gestureMode != GestureMode.NONE) {
                isControlsVisible = false
                // 拖动过的这一下不能再当单击处理，否则「划一下」会顺带把控件翻出来。
                pendingTapActive = false
            }
        }

        when (gestureMode) {
            GestureMode.SEEK -> {
                val totalDuration = if (duration > 0) duration else 1L
                seekTimePosition = (gestureDownPosition + (deltaX * SEEK_SCALE / screenWidth).toLong())
                    .coerceIn(0, totalDuration)
                seekTimeText = formatPlaybackTime(seekTimePosition)
                totalTimeText = formatPlaybackTime(totalDuration)
            }
            GestureMode.VOLUME -> {
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val newVolume = (gestureDownVolume + (-deltaY * max * 3 / screenHeight).toInt())
                    .coerceIn(0, max)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                volumePercent = ((gestureDownVolume * 100 / max.coerceAtLeast(1)) +
                        (-deltaY * 3 * 100 / screenHeight).toInt())
                    .coerceIn(0, 100)
            }
            GestureMode.BRIGHTNESS -> {
                val newBrightness = gestureDownBrightness + (-deltaY * 3f / screenHeight * 255f)
                    .coerceIn(0f, 255f)
                val window = (context as? android.app.Activity)?.window
                if (window != null) {
                    val lp = window.attributes
                    lp.screenBrightness = (newBrightness / 255f).coerceIn(0.01f, 1f)
                    window.attributes = lp
                }
                brightnessPercent = ((gestureDownBrightness * 100 / 255).toInt() +
                        (-deltaY * 3 * 100 / screenHeight).toInt())
                    .coerceIn(0, 100)
            }
            else -> {}
        }
    }

    fun onTouchUp(exoPlayer: ExoPlayerImpl) {
        if (isLocked) {
            isTouching = false
            gestureMode = GestureMode.NONE
            return
        }
        val now = SystemClock.uptimeMillis()
        // 先记住本次手势的类型再清空：它决定控件是「切换」还是「收起」。
        val mode = gestureMode
        if (mode == GestureMode.SEEK) {
            exoPlayer.seekTo(seekTimePosition)
        }
        gestureMode = GestureMode.NONE
        isTouching = false
        controlsIdleTick++

        // ① 拖动（快进 / 音量 / 亮度）：收起控件，且不算点击
        if (mode != GestureMode.NONE) {
            if (PlayerControlsPolicy.afterGesture(mode) == ControlsAfterGesture.HIDE) {
                isControlsVisible = false
            }
            lastTapUpMs = -1L
            pendingTapActive = false
            return
        }

        // ② 长按快放抬手：只负责还原倍速，不当成点击（否则一松手控件就乱闪）
        if (isLongPressSpeedActive) {
            endLongPressSpeed(exoPlayer)
            lastTapUpMs = -1L
            pendingTapActive = false
            return
        }

        // ③ 双击的第二下：取消第一下排队的「切换控件」，改成快进/快退
        if (isDoubleTap(now - lastTapUpMs)) {
            lastTapUpMs = -1L
            pendingTapActive = false
            applyDoubleTapSeek(exoPlayer)
            return
        }

        // ④ 单击：延后执行，等过了双击窗口再说（见 pendingTapTick 的注释）
        lastTapUpMs = now
        pendingTapActive = true
        pendingTapTick++
    }

    /**
     * 取出并清空「待处理的单击」。
     *
     * 返回 true 表示这次单击确实该执行（没被紧随其后的双击吃掉）。
     * 由 UI 在双击窗口结束后调用。
     */
    fun consumePendingTap(): Boolean {
        if (!pendingTapActive) return false
        pendingTapActive = false
        return true
    }

    /**
     * 长按计时到点时由 UI 调用。
     *
     * ⚠️ 这里**必须**再自查一遍条件（见 [shouldStartLongPressSpeed]）：
     * 计时器和手指状态之间存在竞态（比如刚够 500ms 就抬手了、或者已经开始拖进度条了），
     * 不能只信调用时机。
     */
    fun onLongPressTick(exoPlayer: ExoPlayerImpl) {
        if (!shouldStartLongPressSpeed(isLocked, isTouching, isScrubbing, gestureMode)) return
        if (isLongPressSpeedActive) return
        speedBeforeLongPress = playbackSpeed
        isLongPressSpeedActive = true
        exoPlayer.setPlaybackSpeed(LONG_PRESS_SPEED)
        controlsIdleTick++
    }

    // ---------------------------------------------------------------- 控件操作

    /** 点击（或双击后取消）切换控件显隐。 */
    fun toggleControls() {
        isControlsVisible = !isControlsVisible
        controlsIdleTick++
    }

    /** 倍速按钮：切到下一档并立即生效。 */
    fun cyclePlaybackSpeed(exoPlayer: ExoPlayerImpl) {
        val next = nextPlaybackSpeed(playbackSpeed)
        playbackSpeed = next
        // 长按快放期间只更新「用户选的档位」，实际速度等松手时统一落回去。
        if (!isLongPressSpeedActive) {
            exoPlayer.setPlaybackSpeed(next)
        }
        controlsIdleTick++
    }

    /** 画面比例按钮：适应 → 裁剪 → 拉伸 循环。 */
    fun cycleResizeMode() {
        resizeMode = nextVideoResizeMode(resizeMode)
        controlsIdleTick++
    }

    /**
     * 锁定 / 解锁。
     *
     * 锁定前先把长按快放收干净 —— 否则会留下「解锁之后播放速度还是 3x」的幽灵状态。
     */
    fun toggleLock(exoPlayer: ExoPlayerImpl) {
        if (!isLocked) {
            endLongPressSpeed(exoPlayer)
            gestureMode = GestureMode.NONE
            isTouching = false
            pendingTapActive = false
            isControlsVisible = false
            isLocked = true
        } else {
            isLocked = false
            isControlsVisible = true
        }
        controlsIdleTick++
    }

    // ---------------------------------------------------------------- 进度条拖拽

    /**
     * 按在进度条上。
     *
     * ⚠️ 在 **DOWN** 就置 [isScrubbing]，而不是等拖动超过阈值 ——
     * 全屏手势那边靠这个标志让路，晚一步就会被它当成「横向拖动快进」抢走，
     * 结果是一边拖进度条一边触发快进浮层。
     */
    fun beginScrub(x: Float, width: Float) {
        if (isLocked) return
        isScrubbing = true
        isTouching = true
        controlsIdleTick++
        scrubPosition = scrubPositionFor(x, width, duration)
    }

    fun updateScrub(x: Float, width: Float) {
        if (!isScrubbing) return
        scrubPosition = scrubPositionFor(x, width, duration)
    }

    fun endScrub(exoPlayer: ExoPlayerImpl) {
        if (!isScrubbing) return
        exoPlayer.seekTo(scrubPosition)
        // 立刻反映到 UI，别等 200ms 的轮询 —— 松手后进度条要马上停在手指的位置。
        currentPosition = scrubPosition
        isScrubbing = false
        isTouching = false
        controlsIdleTick++
    }

    fun clearSeekFlash() {
        seekFlashText = ""
    }

    // ---------------------------------------------------------------- 内部

    private fun endLongPressSpeed(exoPlayer: ExoPlayerImpl) {
        if (!isLongPressSpeedActive) return
        isLongPressSpeedActive = false
        exoPlayer.setPlaybackSpeed(speedBeforeLongPress)
    }

    private fun applyDoubleTapSeek(exoPlayer: ExoPlayerImpl) {
        val delta = doubleTapSeekDelta(downX, screenWidthPx)
        if (delta == 0L) return
        val target = clampSeekPosition(currentPosition + delta, duration)
        exoPlayer.seekTo(target)
        currentPosition = target
        seekFlashText = formatSeekFlash(delta)
        seekFlashTick++
    }
}
