package io.github.javcinema.player

import android.content.res.Configuration

/**
 * 退出播放器时，最多等窗口转回竖屏多久。
 *
 * 正常情况下一转完就退出（实测约 0.2~0.7s），这个值只是**兜底** ——
 * 万一方向请求没生效（ROM 忽略、Activity 被别的逻辑改了方向），也不能把用户困在播放页。
 */
internal const val PLAYER_EXIT_ORIENTATION_TIMEOUT_MS = 1_000L

/** 轮询「窗口转回竖屏没有」的间隔。 */
internal const val PLAYER_EXIT_POLL_INTERVAL_MS = 50L

/**
 * pop 之后等多久再确认「没退成功」。
 *
 * 正常情况 pop 会立刻把播放页移出组合、协程随之取消，这个等待不会走完；
 * 只有 pop 失败（回退栈为空等）时才会走完，用来把退出状态复位 ——
 * 否则 BackHandler 一直禁着，用户会被困在播放页。
 */
internal const val PLAYER_EXIT_POP_CONFIRM_MS = 1_000L

/** 退出播放器时，现在能不能 pop 掉本页。 */
internal enum class PlayerExitDecision {
    /** 窗口还没转回竖屏 —— 再等一会儿。 */
    WAIT,

    /** 可以退出了。 */
    POP
}

/**
 * 退出播放器要不要再等一等。
 *
 * ## 为什么退出必须「先转屏、后 pop」（血泪）
 *
 * 播放页是横屏，上一页（详情页等）是竖屏。原来的退出顺序是
 * **先 `popBackStack()`、再由 `onDispose` 还原方向**，于是出现了两个错开的阶段：
 * 播放页已经没了，窗口却还是横屏 —— 上一页被塞进横屏窗口里渲染，被旋转 + 拉伸，
 * 直到窗口转回竖屏才恢复。模拟器逐帧实测这段**持续约 0.7s**（约 10 帧），
 * 看起来就是「画面撕裂」。
 *
 * 正确顺序是：按返回 → **先把方向还回去** → 等窗口真的转回竖屏 → 再 pop。
 * 这样横屏→竖屏的旋转发生在播放页还占着屏幕的时候，上一页一出现就已经是竖屏，不会变形。
 *
 * 判据用「配置里的朝向」而不是「请求的朝向」：`requestedOrientation` 一设就变，
 * 但窗口真正转完要等下一帧，只看请求值会立刻 pop，等于没修。
 *
 * @param orientation 当前 `Configuration.orientation`。
 * @param waitedMs 从按下返回到现在过了多久。
 */
internal fun decidePlayerExit(
    orientation: Int,
    waitedMs: Long,
    timeoutMs: Long = PLAYER_EXIT_ORIENTATION_TIMEOUT_MS
): PlayerExitDecision = when {
    orientation == Configuration.ORIENTATION_PORTRAIT -> PlayerExitDecision.POP
    waitedMs >= timeoutMs -> PlayerExitDecision.POP
    else -> PlayerExitDecision.WAIT
}
