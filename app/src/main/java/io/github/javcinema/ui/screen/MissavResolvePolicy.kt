package io.github.javcinema.ui.screen

/** 搜索页解析一轮之后的处置动作。 */
internal enum class MissavResolveAction {
    /** 选出了可用资源，直接跳过去。 */
    PLAY,

    /** 页面是人机验证插页 —— 交给用户过一次验证，通过后自动回到 [RETRY] 之外的正常流程。 */
    CHALLENGE,

    /** 候选还是 0，但列表可能是异步渲染还没出来 —— 再等一轮。 */
    RETRY,

    /** 站点确认没有这部片。 */
    NOT_FOUND
}

internal data class MissavResolveDecision(
    val action: MissavResolveAction,
    /** 仅 [MissavResolveAction.PLAY] 有值。 */
    val url: String? = null,
    /** 解析出的候选数，只用于日志定位问题。 */
    val candidates: Int = 0
)

/** 搜索页最多解析几轮：列表是异步渲染的，第一轮可能只拿到空壳。 */
internal const val MISSAV_RESOLVE_ATTEMPTS = 2

/** 站点确实没有这部片时给用户看的文案。 */
internal const val MISSAV_NOT_FOUND_MESSAGE = "资源库还未收录，播放失败"

/**
 * 决定搜索页这一轮解析结果怎么处理。
 *
 * 顺序有讲究：**先解析、后判验证页**。反过来的话，带 Cloudflare JS Detections 的
 * 正常页面会被误判成验证页（判据说明见 [isMissavChallengeHtml]），
 * 于是明明搜到了结果却把人送去过验证。
 *
 * 「解析不出候选」有两种截然不同的原因，必须分开：
 * - 页面是验证插页 → [MissavResolveAction.CHALLENGE]，过一次验证就能继续；
 * - 页面正常但确实没有 → 先 [MissavResolveAction.RETRY] 再 [MissavResolveAction.NOT_FOUND]，
 *   直接判未收录会把「列表还没渲染完」误报成「资源库未收录」。
 */
internal fun decideSearchOutcome(
    html: String,
    code: String,
    attempt: Int,
    maxAttempts: Int = MISSAV_RESOLVE_ATTEMPTS
): MissavResolveDecision {
    val results = parseMissavSearchResults(html, code)
    selectBestMissavResult(results, code)?.let {
        return MissavResolveDecision(MissavResolveAction.PLAY, it.url, results.size)
    }
    if (isMissavChallengeHtml(html)) {
        return MissavResolveDecision(MissavResolveAction.CHALLENGE, candidates = results.size)
    }
    val retry = attempt + 1 < maxAttempts
    return MissavResolveDecision(
        if (retry) MissavResolveAction.RETRY else MissavResolveAction.NOT_FOUND,
        candidates = results.size
    )
}

/**
 * 人机验证是否已经通过 —— 判据是「当前页既不是验证 URL、标题也不像验证页」。
 *
 * 单独抽出来是因为它要同时看 URL 与标题，而标题只有 WebView 拿得到；
 * 抽成纯函数后这个判据能被单测覆盖。
 */
internal fun isChallengePassed(url: String?, title: String?): Boolean =
    !isMissavChallengeUrl(url) && !isMissavChallengeTitle(title)
