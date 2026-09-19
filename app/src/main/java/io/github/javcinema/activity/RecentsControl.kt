package io.github.javcinema.activity

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

private const val TAG = "RecentsControl"

/**
 * 让本应用的任务在系统「最近任务」列表中隐藏（或恢复显示）。
 *
 * ## 为什么不能只改 intent flag
 * 最近任务条目对应的是**整个任务（task）**，其判定依据是创建该任务时的基准 intent。
 * 本应用的启动入口是 [StartActivity]（`android.intent.category.LAUNCHER`），
 * 它拉起 [MainActivity] 后立刻 `finish()`；任务的基准 intent 始终是启动
 * [StartActivity] 的那个 LAUNCHER intent。
 *
 * 因此之前在 [MainActivity] 上调用 `intent.addFlags(FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)`
 * 是无效的 —— 它改不到任务的基准 intent，表现为「设置里开关已开启，
 * 最近任务列表中却依然能看到本应用」。
 *
 * ## 实现策略
 * - **Android 11（API 30）及以上**：使用官方的
 *   [ActivityManager.AppTask.setExcludeFromRecents]，可在运行时立即生效。
 * - **Android 10（API 29）及以下**：系统未提供运行时 API，退化为改写
 *   [Activity.getIntent] 的标志位。该 flag 只在任务创建时被读取，
 *   对已存在的任务通常不生效，需重启应用后才可能生效。
 *   （UI 文案中的「重启后生效」即由此而来。）
 *
 * @param excluded true 表示从最近任务列表隐藏，false 表示恢复显示
 */
internal fun Activity.applyRecentsExclusion(excluded: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        applyViaAppTask(excluded)
    } else {
        applyViaIntentFlag(excluded)
    }
}

/** API 30+ 的官方途径：直接设置本 Activity 所属 task 的 excludeFromRecents。 */
private fun Activity.applyViaAppTask(excluded: Boolean) {
    try {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val task = manager.appTasks.firstOrNull { it.taskInfo.taskId == taskId }
        task?.setExcludeFromRecents(excluded)
    } catch (e: Exception) {
        // 个别 ROM 会限制 AppTask 操作，失败不应导致崩溃。
        Log.w(TAG, "setExcludeFromRecents failed: ${e.message}")
    }
}

/** API 29- 的兜底途径：改写 intent 标志位，重启后生效。 */
private fun Activity.applyViaIntentFlag(excluded: Boolean) {
    try {
        intent?.let { current ->
            if (excluded) {
                current.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            } else {
                current.flags = current.flags and Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS.inv()
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "intent flag fallback failed: ${e.message}")
    }
}
