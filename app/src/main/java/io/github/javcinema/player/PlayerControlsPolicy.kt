package io.github.javcinema.player

/** 一次触摸手势结束后，播放器控件该怎么变。 */
internal enum class ControlsAfterGesture {
    /** 纯点击（整段触摸没有任何超过阈值的位移）→ 切换控件显隐。 */
    TOGGLE,

    /** 有位移（快进 / 音量 / 亮度）→ 收起控件，把手势浮层和画面让出来。 */
    HIDE
}

/**
 * 播放器控件显隐策略。
 *
 * 抽出来的原因：原先 `SimpleVideoPlayer.toggleControls()` 是**死代码**，从没被调用过，
 * 于是 `isControlsVisible` 永远是 `true` —— 横屏全屏看片时底部进度条和中间播放键
 * 会一直压在画面上，点击画面也没反应。这里把「什么时候该变」的判定集中成纯函数，
 * 好让这类接线遗漏能被单测挡住。
 */
internal object PlayerControlsPolicy {

    /** 控件可见时，静置多久后自动淡出。 */
    const val AUTO_HIDE_DELAY_MS = 3_000L

    /**
     * 手指抬起时控件该怎么变。
     *
     * @param mode 本次手势识别出的类型。`GestureMode.NONE` 表示整段触摸都没超过位移阈值，
     *   也就是一次点击。
     */
    fun afterGesture(mode: GestureMode): ControlsAfterGesture =
        if (mode == GestureMode.NONE) ControlsAfterGesture.TOGGLE else ControlsAfterGesture.HIDE

    /**
     * 现在是否应该给控件开始倒计时。
     *
     * 手指还按着的时候**不能**倒计时，否则长按超过 [AUTO_HIDE_DELAY_MS]
     * 会把控件从手指底下抽走。
     */
    fun shouldAutoHide(controlsVisible: Boolean, touching: Boolean): Boolean =
        controlsVisible && !touching
}
