package io.github.javcinema.player

/**
 * 画面比例的三种模式。
 *
 * ⚠️ 这里**故意不引用** `AspectRatioFrameLayout.RESIZE_MODE_*` —— 那几个常量带
 * `@UnstableApi`，引进来会让这个纯策略文件也得加 `@OptIn`，而且没法在 JVM 单测里跑。
 * 映射关系放在 `PlayerScreen` 里，一个 `when` 就够了。
 *
 * 可见性刻意**不是** `internal`（与同目录其它策略符号不同）：它通过公开类
 * `SimpleVideoPlayer.resizeMode` 暴露出去，标 internal 会直接编译报
 * 「public property exposes its internal type」。`nextVideoResizeMode` 保持 internal。
 */
enum class VideoResizeMode(
    /** 按钮上显示的文案。用站点/系统里已有的说法，别自造译名。 */
    val label: String
) {
    /**
     * 保持比例缩到能完整放进屏幕，多出来的地方留黑边。**默认值。**
     *
     * 之所以默认它：站点上同一部片子的比例五花八门（16:9 / 4:3 / 竖屏手机拍摄），
     * 只有 FIT 能保证任何一种都不变形、不丢画面。
     */
    FIT("适应"),

    /** 保持比例放大到铺满屏幕，超出部分裁掉。画面更满，但会切掉边缘。 */
    ZOOM("裁剪"),

    /** 不保持比例，直接拉满整屏。会变形，只在用户明确要看满屏时用。 */
    FILL("拉伸")
}

/** 循环切换：适应 → 裁剪 → 拉伸 → 适应。 */
internal fun nextVideoResizeMode(current: VideoResizeMode): VideoResizeMode {
    val modes = VideoResizeMode.entries
    return modes[(current.ordinal + 1) % modes.size]
}
