package io.github.javcinema.torrent

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import io.github.javcinema.player.ExoPlayerImpl
import java.util.Locale

/**
 * 无 UI 跑通「磁力在线播放」的生产链路（TorrentSession + MagnetDataSource + ExoPlayerImpl）。
 *
 * 只在 debug 包里存在。用法：
 * `adb shell am start -n io.github.javcinema/.torrent.MagnetRunActivity --es magnet <uri> --el seconds 90`
 *
 * 结论全部走 logcat（tag `MagnetRun`），不碰界面上的任何按钮。
 */
@androidx.annotation.OptIn(UnstableApi::class)
class MagnetRunActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var exo: ExoPlayerImpl? = null
    private var startAt = 0L
    private var budgetMs = 90_000L
    private var videoSeen = false
    private var maxPosition = 0L
    private var errors = 0
    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val magnet = intent.getStringExtra("magnet").orEmpty()
        budgetMs = intent.getLongExtra("seconds", 90L) * 1000L
        if (magnet.isBlank()) {
            log("用法：--es magnet <magnet:> ")
            finish()
            return
        }
        val app = applicationContext
        val player = ExoPlayerImpl(app)
        exo = player
        player.player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) = log("state=${stateName(state)}")
            override fun onIsPlayingChanged(playing: Boolean) = log("isPlaying=$playing")
            override fun onPlayerError(e: PlaybackException) {
                errors++
                log("错误 ${e.errorCodeName} <- ${e.errorCode} cause=${e.cause?.javaClass?.simpleName} msg=${e.cause?.message}")
            }
            override fun onTracksChanged(tracks: Tracks) {
                tracks.groups.forEach { group ->
                    if (group.type == C.TRACK_TYPE_VIDEO && group.length > 0) {
                        val format = runCatching { group.getTrackFormat(0) }.getOrNull() ?: return@forEach
                        videoSeen = true
                        log("视频轨 ${format.width}x${format.height} " +
                            "container=${format.containerMimeType} mime=${format.sampleMimeType}")
                    }
                }
            }
        })
        val net = TorrentSession.get(app).networkState
        log("开始 budget=${budgetMs / 1000}s firewalled=${net.firewalled} dht=${net.dhtNodes}")
        startAt = SystemClock.elapsedRealtime()
        player.prepare(app, magnet)
        player.setVolume(0f)
        handler.postDelayed({ tick(0) }, POLL_MS)
    }

    private fun tick(n: Int) {
        val player = exo?.player ?: return
        val elapsed = SystemClock.elapsedRealtime() - startAt
        maxPosition = maxOf(maxPosition, player.currentPosition)
        if (n % 2 == 0 || player.playbackState == Player.STATE_READY) {
            log("t=${elapsed / 1000}s state=${stateName(player.playbackState)} " +
                "pos=${sec(maxPosition)}s buf=${sec(player.bufferedPosition)}s " +
                "dur=${sec(player.duration)}s loading=${player.isLoading} 播放中=${player.isPlaying}")
        }
        if (player.isPlaying && maxPosition > 3_000L) {
            verdict("可播：解码后真实推进到 ${sec(maxPosition)}s，用时 ${elapsed / 1000}s")
            return
        }
        val sessionHint = TorrentSession.get(applicationContext).lastAlertText
        if (sessionHint != null && elapsed > budgetMs / 2) log("会话告警 $sessionHint")
        if (elapsed >= budgetMs) verdict("未开播：${budgetMs / 1000}s 内没有进入播放（errors=$errors）")
        else handler.postDelayed({ tick(n + 1) }, POLL_MS)
    }

    private fun verdict(text: String) {
        if (done) return
        done = true
        val reason = exo?.magnetFailure?.let { "${it.javaClass.simpleName}: ${it.message}" } ?: "无"
        log("结论 $text｜videoTrack=$videoSeen 失败原因=$reason")
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        exo?.release()
        exo = null
        super.onDestroy()
    }

    private fun log(message: String) {
        Log.i(TAG, message)
    }

    private fun sec(ms: Long): String = String.format(Locale.US, "%.1f", ms / 1000.0)

    private fun stateName(state: Int): String = when (state) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> "?$state"
    }

    companion object {
        private const val TAG = "MagnetRun"
        private const val POLL_MS = 2_000L
    }
}
