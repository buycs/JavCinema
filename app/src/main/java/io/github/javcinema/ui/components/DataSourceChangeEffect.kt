package io.github.javcinema.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.javcinema.JavCinema

/**
 * 数据源（站点源）切换后执行一次 [onChanged]，通常用来把列表滚回顶部 ——
 * 换了源之后内容整体变掉，停留在原来的滚动位置没有意义。
 *
 * ⚠️ 为什么要有这个函数，而不是各页面自己写 `LaunchedEffect`：
 *
 * 原来的写法是在**组合期直接读** `JavCinema.dataSourceVersionFlow.value` 当 key：
 * ```kotlin
 * val dsVersionAtCreation = remember { JavCinema.dataSourceVersionFlow.value }
 * LaunchedEffect(JavCinema.dataSourceVersionFlow.value) { ... }
 * ```
 * 组合期读 `StateFlow.value` **不会建立订阅**（不产生快照读），所以数据源变化本身
 * 不会让这个 composable 重组，key 也就不会被重新求值 —— 这段滚动逻辑实际上是
 * 靠「数据源变了 → ViewModel 重新加载 → 列表状态变化顺带触发了一次重组」**侥幸生效**。
 * 一旦新源返回的内容和旧源恰好让重组不发生，就会静默失效。
 * Lint 的 `StateFlowValueCalledInComposition` 报的就是这个。
 *
 * 这里改用 [collectAsStateWithLifecycle] 真正订阅，切换数据源时必定重跑。
 * `remember { version }` 记录进入页面时的版本，只有版本**真的变了**才触发 ——
 * 首次进入页面不滚动（此时列表本来就在顶部）。
 */
@Composable
fun DataSourceChangeEffect(onChanged: suspend () -> Unit) {
    val version by JavCinema.dataSourceVersionFlow.collectAsStateWithLifecycle()
    val versionAtEntry = remember { version }
    LaunchedEffect(version) {
        if (version != versionAtEntry) {
            onChanged()
        }
    }
}
