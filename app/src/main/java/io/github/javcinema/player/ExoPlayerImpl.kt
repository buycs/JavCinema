package io.github.javcinema.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource

// ⚠️ 注意这里用的是 androidx.annotation.OptIn，**不是** kotlin.OptIn。
// Media3 的 UnstableApi 是 AndroidX 的 lint 注解（没有 @RequiresOptIn 元注解），
// kotlin.OptIn 对它无效 —— 只会换来一条 "has no effect" 警告。
// 本类用到的 DefaultHttpDataSource.Factory 的 setter、HlsMediaSource.Factory、
// Player.setMediaSource 都属于 @UnstableApi，必须这样 opt-in 才能过 Lint。
@androidx.annotation.OptIn(UnstableApi::class)
class ExoPlayerImpl(context: Context) {

    val player: ExoPlayer = createPlayer(context)

    private fun createPlayer(context: Context): ExoPlayer {
        return ExoPlayer.Builder(context).build()
    }

    fun prepare(context: Context, url: String, headers: Map<String, String> = emptyMap()) {
        val userAgent = "JavCinema/${android.os.Build.VERSION.SDK_INT}"

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
        if (headers.isNotEmpty()) {
            httpDataSourceFactory.setDefaultRequestProperties(headers)
        }

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        val mediaItem = MediaItem.fromUri(url)

        if (url.contains(".m3u8")) {
            val hlsMediaSource = HlsMediaSource.Factory(httpDataSourceFactory)
                .createMediaSource(mediaItem)
            player.setMediaSource(hlsMediaSource)
        } else {
            player.setMediaItem(mediaItem)
        }
        player.prepare()
        player.playWhenReady = true
    }

    fun play() {
        player.playWhenReady = true
    }

    fun pause() {
        player.playWhenReady = false
    }

    fun togglePlay() {
        player.playWhenReady = !player.playWhenReady
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun getCurrentPosition(): Long = player.currentPosition

    fun getDuration(): Long = player.duration

    /**
     * 已经缓冲到的位置（毫秒）。
     *
     * 用在进度条上画「已缓存」那一段。HLS 是分片拉取的，这个值会**跳着**涨
     * （一整个分片下完才前进），不是平滑爬升 —— 所以别拿它做「网速」之类的判断。
     */
    fun getBufferedPosition(): Long = player.bufferedPosition

    fun isPlaying(): Boolean = player.isPlaying

    /**
     * 设置播放倍速。传 1.0f 即恢复正常速度。
     *
     * ⚠️ 这是**有状态**的：ExoPlayer 会一直保持这个倍速，直到再次设置。
     * 长按快放那种「临时加速」必须在松手时显式设回用户选的倍速，
     * 不能指望它自己恢复。
     */
    fun setPlaybackSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
    }

    fun setVolume(volume: Float) {
        player.volume = volume
    }

    fun release() {
        player.release()
    }
}
