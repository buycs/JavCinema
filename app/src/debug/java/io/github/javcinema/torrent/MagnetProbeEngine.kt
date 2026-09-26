package io.github.javcinema.torrent

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.libtorrent4j.AlertListener
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.MetadataFailedAlert
import org.libtorrent4j.alerts.ReadPieceAlert
import org.libtorrent4j.alerts.TorrentErrorAlert
import org.libtorrent4j.swig.add_torrent_params
import org.libtorrent4j.swig.error_code
import org.libtorrent4j.swig.libtorrent
import org.libtorrent4j.swig.string_vector
import java.io.File
import java.util.Locale

/** 一条待测磁力。label 只用于报告可读性。 */
internal data class ProbeSample(val label: String, val magnet: String)

/**
 * M0 样本：全部从应用自己的磁力搜索里取（BTSEARCH 源），不是网上随便找的热门种子 ——
 * 要回答的问题是「这个 App 递给第三方播放器的那些磁力，本机 P2P 能不能喂得动」。
 *
 * 第二轮（10 条）：上一轮只有 3 条，不足以下结论。这里刻意混编 ——
 * 5 条近期新盘（2026-09 抓取）+ 5 条老番号/中文字幕盘，用来量真实命中率。
 * label 里的年份是番号发布年代，不是抓取时间。
 */
internal val M0_SAMPLES = listOf(
    ProbeSample("SSIS-063 中文字幕 1.71GB (2023)", "magnet:?xt=urn:btih:e2afab0bc6a58e2763bb6d098ac9e69dca2b86c1"),
    ProbeSample("MEYD-021 中文字幕 1.57GB (2020)", "magnet:?xt=urn:btih:b5d3da374293d2f3fe2afb86eafac86cc3f97046"),
    ProbeSample("T28-589 1.70GB (老盘)", "magnet:?xt=urn:btih:fcde11384119f2015b57280d95932c7bab33219b"),
    ProbeSample("START-638 1.44GB (2026)", "magnet:?xt=urn:btih:022755c055e03ce820b60b4a8813db72733dc818"),
    ProbeSample("MIDE-990 1.62GB (2026)", "magnet:?xt=urn:btih:4e946fd9c2fa242455f786737d2419500e8c5f89"),
    ProbeSample("IPX-779 CH.HD 3.39GB (2026)", "magnet:?xt=urn:btih:a2efc0a5285766a60785beba8e60b03350f722b6"),
    ProbeSample("FSDSS-623-C 无码 5.47GB (2026)", "magnet:?xt=urn:btih:1a80fd3636dbb7cebe3e198c304462743a39b820"),
    ProbeSample("CAWD-780-C 无码 7.68GB (2026)", "magnet:?xt=urn:btih:c8352b217f5d97932e4bed27c6b580b5e8ff4819"),
    ProbeSample("JUL-954 无码 1.45GB (2026)", "magnet:?xt=urn:btih:7857bfe41e2791038d4c644933d84347d6bac81f"),
    ProbeSample("MIAB-461 2.69GB (2026)", "magnet:?xt=urn:btih:23af071478789d8ec1365fb85d2b5a597f1cbb85")
)

/**
 * 对照组：Ubuntu 官方 `.torrent` 现算的 info-hash，全球 swarm、种子充足。
 * 它的作用是「环境自检」—— 它跑不出速率，就说明当前出口/引擎/网络在骗人，
 * 本轮 JAV 样本的 NO-GO 一律不能归因给资源。上一轮就是缺了这一步。
 */
internal val M0_CONTROL = ProbeSample(
    "对照 Ubuntu 24.04.3 desktop ISO",
    "magnet:?xt=urn:btih:d160b8d8ea35a5b4e52837468fc8f03d55cef1f7"
)

/**
 * 公共 tracker。应用里 MagnetLink.create() 把 `tr=` 截掉了（M1 要修），
 * 所以验证时必须自己补一份，否则只剩 DHT，测出来的到达率会假性偏低。
 */
private val PUBLIC_TRACKERS = listOf(
    "udp://tracker.opentrackr.org:1337/announce",
    "udp://tracker.torrent.eu.org:451/announce",
    "udp://open.stealth.si:80/announce",
    "udp://exodus.desync.com:6969/announce",
    "udp://tracker.dler.org:6969/announce"
)

private const val METADATA_TIMEOUT_MS = 45_000L

// 10 条一轮，把可播速率观测窗从 90s 压到 40s，整轮控制在 ~11 分钟内；40s 足够判持续速率。
private const val SAMPLE_WINDOW_MS = 40_000L
private const val POLL_MS = 1_000L
private const val PROGRESS_LOG_EVERY_MS = 5_000L

/** 判「可播」的持续速率下限：约 2 小时 6.5GB 的正片 ≈ 0.9MB/s，取整到 1MB/s。 */
private const val PLAYABLE_BYTES_PER_SEC = 1_000_000L

internal class MagnetProbeEngine(context: Context, private val log: (String) -> Unit) {

    private val saveDir = File(context.cacheDir, "magnet_probe").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var session: SessionManager? = null
    private var runJob: Job? = null
    private var lastHandle: TorrentHandle? = null

    fun runSamples(samples: List<ProbeSample>) {
        if (runJob != null) {
            log("上一轮还在跑，先停止")
            return
        }
        val sm = ensureSession()
        runJob = scope.launch {
            // 先跑对照组做环境自检：它挂了说明当前出口根本拿不到数据，本轮样本结果不可用。
            val control = try {
                probe(sm, M0_CONTROL)
            } finally {
                lastHandle?.let { h -> runCatching { sm.remove(h) }; lastHandle = null }
                delay(600)
            }
            log("环境自检（对照组，不计入命中率）：${control.render()}")
            if (!control.playable) log("!! 对照组不可播 —— 本轮所有 NO-GO 读数都不能归因给资源，请先查出口")
            log("")
            // 每条测完必须把 torrent 从会话摘掉：否则上一条会继续下载抢带宽，
            // 后面每条的速率读数都被污染（上一轮 10 条就是被这个废掉的）。
            val verdicts = samples.map { sample ->
                try {
                    probe(sm, sample)
                } finally {
                    lastHandle?.let { h ->
                        runCatching { sm.remove(h) }
                        lastHandle = null
                    }
                    delay(600)
                    log("会话内剩余 torrent=${sm.swig().get_torrents().size}")
                }
            }
            val playable = verdicts.count { it.playable }
            log("")
            log("========== M0 结论 ==========")
            verdicts.forEach { log(it.render()) }
            log("达到可播速率 $playable/${samples.size} → " +
                if (playable >= 2) "GO：继续 M1（引擎接入）" else "NO-GO：P2P 路线停，转远程解析")
            runJob = null
        }
    }

    fun stop() {
        runJob?.cancel()
        runJob = null
        session?.let { sm ->
            sm.swig().get_torrents().forEach { handle ->
                runCatching { sm.remove(TorrentHandle(handle)) }
            }
            runCatching { sm.stop() }
        }
        session = null
        log("会话已停止")
    }

    fun destroy() {
        stop()
        scope.cancel()
    }

    private fun ensureSession(): SessionManager {
        session?.let { return it }
        val sm = SessionManager(true)
        sm.addListener(probeAlerts)
        sm.start()
        runCatching { sm.startDht() }
        val pack = sm.settings()
        pack.setEnableDht(true)
        sm.applySettings(pack)
        session = sm
        log("会话已启动：libtorrent4j ${runCatching { org.libtorrent4j.LibTorrent.libtorrent4jVersion() }.getOrElse { "?" }}")
        return sm
    }

    private val probeAlerts = object : AlertListener {
        override fun types(): IntArray = intArrayOf(
            AlertType.METADATA_FAILED.swig(),
            AlertType.TORRENT_ERROR.swig(),
            AlertType.READ_PIECE.swig()
        )

        override fun alert(alert: Alert<*>) {
            when (alert) {
                is MetadataFailedAlert -> log("alert: 拿种子信息失败 ${alert.getError()}")
                is TorrentErrorAlert -> log("alert: 种子错误 ${alert.error()}")
                is ReadPieceAlert -> log("alert: piece ${alert.piece()} 可读，size=${alert.size()} err=${alert.error()}")
            }
        }
    }

    private suspend fun probe(sm: SessionManager, sample: ProbeSample): Verdict {
        log("")
        log("---- ${sample.label} ----")
        val parseEc = error_code()
        val params: add_torrent_params = libtorrent.parse_magnet_uri(sample.magnet, parseEc)
        if (parseEc.failed()) {
            return Verdict(sample.label, false, 0, 0, 0, 0, "磁力解析失败：${parseEc.message()}")
        }
        params.setTrackers(string_vector(PUBLIC_TRACKERS))
        params.setSave_path(saveDir.absolutePath)
        val addEc = error_code()
        val handle = TorrentHandle(sm.swig().add_torrent(params, addEc))
        lastHandle = handle
        if (addEc.failed()) {
            return Verdict(sample.label, false, 0, 0, 0, 0, "add_torrent 失败：${addEc.message()}")
        }

        val t0 = SystemClock.elapsedRealtime()
        var metadataMs = -1L
        var videoIndex = -1
        var videoSize = 0L
        var videoName = ""
        var bytesAtMeta = 0L
        var bytesEnd = 0L
        var timeAtMeta = t0
        var timeEnd = t0
        var seeds = 0
        var peers = 0
        var peakRate = 0
        var lastProgressLog = 0L

        handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)

        while (true) {
            val now = SystemClock.elapsedRealtime()
            val elapsed = now - t0
            val status = runCatching { handle.status() }.getOrNull()
            if (status == null) {
                return Verdict(sample.label, false, 0, 0, 0, 0, "取不到状态（会话未起来？）")
            }
            seeds = maxOf(seeds, status.numSeeds())
            peers = maxOf(peers, status.numPeers())
            peakRate = maxOf(peakRate, status.downloadRate())

            if (metadataMs < 0) {
                val info = runCatching { handle.torrentFile() }.getOrNull()
                if (info != null) {
                    metadataMs = now - timeAtMeta
                    val files = info.files()
                    videoIndex = pickLargestVideo(files)
                    if (videoIndex >= 0) {
                        videoName = files.filePath(videoIndex)
                        videoSize = files.fileSize(videoIndex)
                    }
                    bytesAtMeta = status.totalWantedDone()
                    timeAtMeta = now
                    isolateMainFile(handle, files, videoIndex)
                    log("种子信息 ${metadataMs}ms 到达，文件 ${files.numFiles()} 个，" +
                        "主文件 #$videoIndex ${mb(videoSize)}MB ${videoName.substringAfterLast('/')}")
                    runCatching { handle.readPiece(0) }
                        .onFailure { log("readPiece(0) 发起失败：${it.message}") }
                }
            } else {
                bytesEnd = status.totalWantedDone()
                timeEnd = now
            }

            if (now - lastProgressLog >= PROGRESS_LOG_EVERY_MS) {
                lastProgressLog = now
                log("t=${(elapsed / 1000)}s seeds=$seeds peers=$peers " +
                    "rate=${status.downloadRate() / 1024}KB/s " +
                    "dht=${sm.dhtNodes()} firewalled=${sm.isFirewalled} " +
                    "state=${status.state()} 已下=${mb(status.totalWantedDone())}MB")
            }

            val sampled = timeEnd - timeAtMeta
            if (metadataMs >= 0 && sampled >= SAMPLE_WINDOW_MS) break
            if (elapsed >= METADATA_TIMEOUT_MS && metadataMs < 0) {
                return Verdict(sample.label, false, metadataMs, 0, seeds, peers,
                    "超时未拿到种子信息（DHT/tracker 都没回）")
            }
            delay(POLL_MS)
        }

        val seconds = ((timeEnd - timeAtMeta) / 1000L).coerceAtLeast(1L)
        val sustained = ((bytesEnd - bytesAtMeta) / seconds).coerceAtLeast(0L)
        val playable = sustained >= PLAYABLE_BYTES_PER_SEC
        val note = if (playable) {
            "可播：${mb(sustained)}MB/s 持续 ${seconds}s，首 1 分钟正片约需 ${neededForOneMinute(videoSize)}MB"
        } else {
            "不可播：持续 ${mb(sustained)}MB/s < ${PLAYABLE_BYTES_PER_SEC / 1_000_000}MB/s" +
                if (seeds == 0) "（无种）" else "（有种但速度不足）"
        }
        log("结论：$note")
        return Verdict(sample.label, playable, metadataMs, sustained, seeds, peers, note)
    }

    /** 只下最大的视频文件，其余置 IGNORE —— 在线播放不需要整包。 */
    private fun isolateMainFile(handle: TorrentHandle, files: org.libtorrent4j.FileStorage, mainIndex: Int) {
        if (mainIndex < 0) return
        val priorities = Array(files.numFiles()) { index ->
            if (index == mainIndex) Priority.DEFAULT else Priority.IGNORE
        }
        runCatching { handle.prioritizeFiles(priorities) }
            .onFailure { log("设置文件优先级失败：${it.message}") }
    }

    private fun pickLargestVideo(files: org.libtorrent4j.FileStorage): Int {
        var best = -1
        var bestSize = -1L
        for (i in 0 until files.numFiles()) {
            val path = files.filePath(i).lowercase(Locale.US)
            val size = files.fileSize(i)
            if (VIDEO_EXTENSIONS.any { path.endsWith(it) } && size > bestSize) {
                best = i
                bestSize = size
            }
        }
        return best
    }

    /** 按 2 小时正片折算：播 1 分钟需要多少 MB。用来判断「速度够不够撑住播放」。 */
    private fun neededForOneMinute(totalBytes: Long): Long =
        if (totalBytes <= 0) 0 else totalBytes / ASSUMED_DURATION_MINUTES

    private fun mb(bytes: Long): String = String.format(Locale.US, "%.1f", bytes / 1024.0 / 1024.0)

    private class Verdict(
        val label: String,
        val playable: Boolean,
        val metadataMs: Long,
        val sustainedBytesPerSec: Long,
        val seeds: Int,
        val peers: Int,
        val note: String
    ) {
        fun render(): String =
            "%s %s｜metadata=%s 持续=%sMB/s seeds=%d peers=%d｜%s".format(
                if (playable) "可播" else "不可播",
                label,
                if (metadataMs < 0) "未到达" else "${metadataMs}ms",
                String.format(Locale.US, "%.2f", sustainedBytesPerSec / 1024.0 / 1024.0),
                seeds, peers, note
            )
    }

    companion object {
        private const val ASSUMED_DURATION_MINUTES = 120L
        private val VIDEO_EXTENSIONS = listOf(
            ".mp4", ".mkv", ".avi", ".wmv", ".ts", ".m2ts", ".mov", ".flv", ".m4v", ".webm", ".rmvb"
        )
    }
}
