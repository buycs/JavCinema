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

    fun isPlaying(): Boolean = player.isPlaying

    fun setVolume(volume: Float) {
        player.volume = volume
    }

    fun release() {
        player.release()
    }
}
