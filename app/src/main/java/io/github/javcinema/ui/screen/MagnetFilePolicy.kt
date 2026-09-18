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

private val MEDIA_EXTENSIONS = VIDEO_EXTENSIONS + listOf(
    ".mp3", ".flac", ".aac", ".m4a", ".wav", ".ogg", ".wma",
    ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp"
)

internal fun looksLikeMovieCode(query: String): Boolean {
    val trimmed = query.trim()
    return Regex("""^[A-Za-z]{2,8}-?\d{2,6}[A-Za-z]?$""").matches(trimmed)
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

internal fun isMediaFile(filename: String?): Boolean {
    val value = filename.orEmpty().lowercase()
    return MEDIA_EXTENSIONS.any { value.endsWith(it) }
}

internal fun largestVideoIndex(files: List<MagnetFile>): Int? {
    val videos = files.mapIndexed { index, file -> index to file }
        .filter { (_, file) -> isVideoFile(file.filename) && !isAdText(file.filename) }
    return videos.maxByOrNull { it.second.size }?.first
}

internal fun visibleMagnetFiles(files: List<MagnetFile>): List<MagnetFile> {
    return files.filter { isMediaFile(it.filename) && !isAdText(it.filename) }
}
