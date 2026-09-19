package io.github.javcinema.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 首屏功能页顶栏的统一高度。
 *
 * 影片 / 女优 / 收藏等页面都有顶栏，高度必须一致 —— 否则切换底部 tab 时
 * 内容起始位置会上下跳动。
 */
val TOP_BAR_HEIGHT = 38.dp

/** 顶栏选中态文字与图标颜色（与 TabRow 默认指示器同色）。 */
val TopBarSelectedContentColor: Color
    @Composable get() = MaterialTheme.colorScheme.primary

/** 顶栏未选中态文字与图标颜色。 */
val TopBarUnselectedContentColor: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

/**
 * 首屏功能页统一的顶栏 [TabRow]。
 *
 * 样式约定（此前影片页与女优页是橙色底 + 白字，与收藏页的白底不一致）：
 * - 背景：跟随主题的 `background`，浅色下即纯白，与页面内容区无缝衔接
 * - 高度：[TOP_BAR_HEIGHT]（比 Material 默认的 48dp 更紧凑）
 * - 无分割线：与影片页原有样式保持一致
 * - 选中态：靠文字/图标颜色区分（主色 vs `onSurfaceVariant`）。
 *   注意 Material 默认的下划线指示器需要约 48dp 高度才画得下，38dp 下会被裁掉，
 *   所以这里不依赖指示器，选中与否由颜色表达。
 *
 * 抽成共用组件是为了让各页面顶栏不会各自漂移。
 */
@Composable
fun AppTopTabRow(
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    tabs: @Composable () -> Unit
) {
    TabRow(
        selectedTabIndex = selectedIndex,
        containerColor = MaterialTheme.colorScheme.background,
        divider = {},
        modifier = modifier.height(TOP_BAR_HEIGHT),
        tabs = tabs
    )
}
