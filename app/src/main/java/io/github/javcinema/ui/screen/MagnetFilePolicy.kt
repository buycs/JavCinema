package io.github.javcinema.ui.screen

import io.github.javcinema.data.model.MagnetFile

private val AD_MARKERS = listOf(
    "广告", "廣告", "官网", "官網", "最新地址", "点击访问", "更多资源",
    "www.", "http://", "https://", ".com/", ".net/", ".cc/"
)

private val AD_EXTENSIONS = listOf(".txt", ".url", ".html", ".htm", ".exe", ".lnk")

private val VIDEO_EXTENSIONS = listOf(
    ".mp4", ".mkv", ".avi", ".wmv", ".iso", ".ts", ".m2ts", ".mov", ".flv", ".m4v",
    ".webm", ".mpg", ".mpeg", ".rmvb", ".rm", ".vob", ".asf", ".3gp", ".f4v"
)

/**
 * 番号形态：字母（2-8）+ 可选连字符 + 数字（2-6）+ 可选字母后缀。
 *
 * 无连字符时要求字母至少 3 个。原规则 `^[A-Za-z]{2,8}-?\d{2,6}[A-Za-z]?$` 过宽：
 * 「ab123」这类普通用户名/密码也会被判为番号，进而在影片搜索无结果时触发一次
 * 毫无意义的磁力搜索。真实番号（SSIS-001、IPX-001、ssis001）不受影响。
 */
private val MOVIE_CODE_RE = Regex(
    """^(?:[A-Za-z]{2,8}-\d{2,6}[A-Za-z]?|[A-Za-z]{3,8}\d{2,6}[A-Za-z]?)$"""
)

internal fun looksLikeMovieCode(query: String): Boolean {
    return MOVIE_CODE_RE.matches(query.trim())
}

internal fun isAdText(text: String?): Boolean {
    val value = text.orEmpty().lowercase()
    if (value.isBlank()) return false
    if (AD_EXTENSIONS.any { value.endsWith(it) }) return true
    return AD_MARKERS.any { value.contains(it.lowercase()) }
}

internal fun isVideoFile(filename: String?): Boolean {
    val value = filename.orEmpty().lowercase()
    return VIDEO_EXTENSIONS.any { value.endsWith(it) }
}

/**
 * 挑出「主文件」在文件列表里的下标 —— **体积最大的那个视频**。
 *
 * 判据故意只用体积，不用广告标记。广告片段实测只有十几 MB
 * （`x u u 6 2 . c o m.mp4` 12.09 MB、`社 區 最 新 情 報.mp4` 14.39 MB），
 * 正片是 GB 级，体积本身就能把两者分开；反过来拿「名字不像广告」当偏好会**挑错**：
 * 水印正片（`www.98t.la@SSIS-001.mp4`）会被跳过，加粗落到十几 MB 的赠品片段上。
 */
internal fun largestVideoIndex(files: List<MagnetFile>): Int? =
    files.mapIndexed { index, file -> index to file }
        .filter { (_, file) -> isVideoFile(file.filename) }
        .maxByOrNull { it.second.size }
        ?.first

/**
 * 附件 / 广告片段的体积上限：**不足 20 MB 的条目不展示**。
 *
 * 实测广告片段都在这个量级以下（SSIS-001：12.09 MB + 14.39 MB vs 正片 6.46 GB；
 * ROE-556：1.91 MB vs 正片 5.94 GB），而正片是 GB 级，20 MB 这条线余量很大。
 *
 * 注意单位是 **1024 进制**：体积由 `parseSize()` 按 `KB/MB/GB/TB` × 1024 解析，
 * 所以这里的 MB 也是 MiB，不能用 `20_000_000`。
 */
private const val TINY_FILE_BYTES = 20L * 1024 * 1024

/**
 * 文件列表里要展示的条目 —— **只按体积筛，不看扩展名、不看名字**。
 *
 * ⚠️ **为什么不看扩展名**：扩展名白名单和名字黑名单是同一类东西 —— 都会**静默**藏掉真东西。
 * `movie.mp4.part1`（分卷下载）`endsWith(".mp4")` 匹配不上、`.rar` / `.7z` 打包的种子
 * 整个列表都会被判成非媒体。既然体积已经能把附件和正片分开，就没必要再叠一层白名单。
 * （历史教训：这里原先要求「媒体扩展名」，于是被 Cloudflare 吃掉扩展名的正片
 * `4k2.me@roe-556.mp4` → `[email protected]` 直接消失，见 `CfEmailPolicy`。）
 *
 * ⚠️ **广告标记（[isAdText]）不参与隐藏**：那套标记既漏又误 ——
 * 漏的实测有 `x u u 6 2 . c o m.mp4`（字符被空格拆开）、`社 區 最 新 情 報.mp4`（繁体、不在词表里），
 * 误的有 `4k688.com@ROE-556.mp4`（**5.94 GB 的正片本身**，整条种子才 5.94 GB）、
 * `www.98t.la@SSIS-001.mp4` 这类站点水印名。体积比名字可靠得多，所以隐藏只看体积。
 *
 * ⚠️ **兜底：筛完为空就退回全部条目。** 只要「筛」这个动作存在，
 * 就必须保证它不可能把整条种子筛没 —— 整条种子都是十几 MB 短片时（只有一条时更是），
 * 退回原样展示，不能给用户看「没有文件」。
 */
internal fun visibleMagnetFiles(files: List<MagnetFile>): List<MagnetFile> =
    files.filter { it.size >= TINY_FILE_BYTES }.ifEmpty { files }
