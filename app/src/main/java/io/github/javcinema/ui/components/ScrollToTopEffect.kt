package io.github.javcinema.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember

/**
 * 用户再次点按当前底部 tab 时（[trigger] 换了新值）执行一次 [onScrollToTop]。
 * [trigger] 是 MainScreen 持有的 `System.nanoTime()`，只在「tab 已选中又被点一下」时自增。
 *
 * ⚠️ 为什么不能写成 `LaunchedEffect(trigger) { if (trigger > 0) 滚顶 }`：
 * 页面跳去详情页时会离开组合，返回时这个 composable 重新进入组合，
 * LaunchedEffect 是**新建**的，会带着仍然非零的旧 [trigger] 再跑一次 body ——
 * 于是「进详情再返回」被误判成「用户按了回到顶部」，列表凭空跳回第 0 项。
 * `trigger > 0` 挡不住它：它只能区分「从来没按过 tab」，区分不了「按过但那是上一次的事」。
 *
 * 这里用 [remember] 记下进入组合时看到的 trigger 值，只有值**真的变了**才滚。
 * 重新进入组合时 remember 重新初始化成当前值，两边相等，因此返回后保持原滚动位置。
 */
@Composable
fun ScrollToTopEffect(trigger: Long, onScrollToTop: suspend () -> Unit) {
    val lastTrigger = remember { mutableLongStateOf(trigger) }
    LaunchedEffect(trigger) {
        if (trigger != lastTrigger.longValue) {
            lastTrigger.longValue = trigger
            onScrollToTop()
        }
    }
}
