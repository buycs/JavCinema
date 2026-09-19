package io.github.javcinema.ui.screen

/**
 * 缓存清理结果的汇总描述。
 *
 * 抽成纯函数以便单测：Android 侧的清理动作本身依赖平台 API，
 * 但「哪些项成功、失败原因如何拼装」是可确定的纯逻辑。
 */
data class CacheClearOutcome(
    val imageCacheCleared: Boolean,
    val webViewCacheCleared: Boolean,
    val memoryCacheCleared: Boolean
) {
    val allSucceeded: Boolean
        get() = imageCacheCleared && webViewCacheCleared && memoryCacheCleared

    /** 是否存在任何一项成功（用于决定提示语气）。 */
    val anySucceeded: Boolean
        get() = imageCacheCleared || webViewCacheCleared || memoryCacheCleared
}

/**
 * 把清理结果拼成给用户的提示文案。
 *
 * @param outcome 各项清理结果
 */
fun buildCacheClearMessage(outcome: CacheClearOutcome): String {
    if (outcome.allSucceeded) return "缓存已清理"
    val failed = buildList {
        if (!outcome.imageCacheCleared) add("图片")
        if (!outcome.webViewCacheCleared) add("网页")
        if (!outcome.memoryCacheCleared) add("内存")
    }
    return if (outcome.anySucceeded) {
        "部分清理完成，失败：${failed.joinToString("、")}"
    } else {
        "清理失败: ${failed.joinToString("、")}"
    }
}
