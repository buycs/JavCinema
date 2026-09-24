package io.github.javcinema.player

/**
 * 播放时间的显示格式。
 *
 * 抽成纯函数是为了能单测 —— 磁力片源动辄两三个小时，分钟数一路涨到 `125:30`
 * 这种显示必须避免；而「刚好 1 小时」「时长为 0」这些边界在界面上几乎手工验不到。
 */

private const val MILLIS_PER_SECOND = 1_000L

/**
 * 毫秒 → `MM:SS`，满一小时起改成 `H:MM:SS`。
 *
 * - 非正数（时长还没拿到 / 播放出错）一律 `00:00`，绝不显示负数。
 * - 不足 1 小时**不加**小时位（`05:30` 而不是 `0:05:30`），短片读起来更快。
 * - 小时位不补零（`1:05:30`），所以超过 24 小时也能正常显示（`26:00:00`）。
 */
internal fun formatPlaybackTime(millis: Long): String {
    if (millis <= 0L) return "00:00"
    val totalSeconds = millis / MILLIS_PER_SECOND
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
