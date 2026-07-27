package io.github.javcinema.player

import android.content.Context
import android.media.AudioManager
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

class SimpleVideoPlayer(private val context: Context) {

    var playbackState: PlayerPlaybackState by mutableStateOf(PlayerPlaybackState.IDLE)
    var currentPosition: Long by mutableLongStateOf(0L)
    var duration: Long by mutableLongStateOf(0L)
    var bufferedPercent: Int by mutableIntStateOf(0)
    var volumePercent: Int by mutableIntStateOf(50)
    var brightnessPercent: Int by mutableIntStateOf(100)
    var isControlsVisible: Boolean by mutableStateOf(true)
    var gestureMode: GestureMode by mutableStateOf(GestureMode.NONE)
    var seekTimeText: String by mutableStateOf("")
    var totalTimeText: String by mutableStateOf("")

    private var downX = 0f
    private var downY = 0f
    private var gestureDownPosition: Long = 0L
    private var gestureDownVolume: Int = 0
    private var gestureDownBrightness: Float = 0f
    private var seekTimePosition: Long = 0L

    companion object {
        private const val THRESHOLD = 10
        private const val SEEK_SCALE = 90000
    }

    private val audioManager: AudioManager
        get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun onTouchDown(x: Float, y: Float, screenWidth: Int) {
        downX = x
        downY = y
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
        }

        when (gestureMode) {
            GestureMode.SEEK -> {
                val totalDuration = if (duration > 0) duration else 1L
                seekTimePosition = (gestureDownPosition + (deltaX * SEEK_SCALE / screenWidth).toLong())
                    .coerceIn(0, totalDuration)
                seekTimeText = formatTime(seekTimePosition)
                totalTimeText = formatTime(totalDuration)
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
        when (gestureMode) {
            GestureMode.SEEK -> {
                exoPlayer.seekTo(seekTimePosition)
            }
            else -> {}
        }
        gestureMode = GestureMode.NONE
    }

    fun toggleControls() {
        isControlsVisible = !isControlsVisible
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}
