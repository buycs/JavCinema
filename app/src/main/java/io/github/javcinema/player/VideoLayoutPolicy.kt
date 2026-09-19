package io.github.javcinema.player

/**
 * 播放器画面布局策略。
 *
 * 这里只做「算比例」这一件纯逻辑，不碰 View —— 因为它是决定
 * 「画面会不会被拉伸 / 裁剪」的唯一变量，必须能脱离设备单测。
 */
internal object VideoLayoutPolicy {

    /**
     * 由视频尺寸算出**显示宽高比**（宽 / 高），供 `AspectRatioFrameLayout` 做信箱式布局。
     *
     * 几个必须照顾到的现实：
     * 1. `width`/`height` 任一非正 → 返回 [UNKNOWN_ASPECT_RATIO]，调用方按「铺满」处理。
     * 2. `unappliedRotationDegrees` 为 90/270 时，解码器还没帮我们转过来，
     *    此时宽高要**对调**，否则竖拍视频会被判成横的，画面被裁掉一大块。
     * 3. `pixelWidthHeightRatio != 1` 是变形像素（部分 DVD/老片源），
     *    不乘上去会导致画面轻微拉伸。
     */
    fun displayAspectRatio(
        width: Int,
        height: Int,
        unappliedRotationDegrees: Int = 0,
        pixelWidthHeightRatio: Float = 1f
    ): Float {
        if (width <= 0 || height <= 0) return UNKNOWN_ASPECT_RATIO

        val rotated = unappliedRotationDegrees == 90 || unappliedRotationDegrees == 270
        val base = if (rotated) {
            height.toFloat() / width
        } else {
            width.toFloat() / height
        }
        val pixelRatio = if (pixelWidthHeightRatio > 0f) pixelWidthHeightRatio else 1f
        val ratio = base * pixelRatio
        return if (ratio.isFinite() && ratio > 0f) ratio else UNKNOWN_ASPECT_RATIO
    }

    /** 还不知道视频尺寸时的哨兵值，语义是「按父容器铺满」。 */
    const val UNKNOWN_ASPECT_RATIO: Float = 0f
}
