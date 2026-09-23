package io.github.javcinema.ui.screen

import io.github.javcinema.data.model.DownloadLink
import io.github.javcinema.data.model.MagnetFile

sealed class MagnetSourceUi {
    data object Idle : MagnetSourceUi()
    data object Loading : MagnetSourceUi()
    class Success(val items: List<DownloadLink>) : MagnetSourceUi()
    data class Error(val message: String) : MagnetSourceUi()
}

internal fun toMagnetSourceUi(
    success: Boolean,
    items: List<DownloadLink>,
    errorMessage: String?
): MagnetSourceUi {
    return if (success) {
        MagnetSourceUi.Success(items)
    } else {
        MagnetSourceUi.Error(errorMessage?.takeIf { it.isNotBlank() } ?: "搜索失败")
    }
}

internal fun shouldLoadFiles(
    files: List<MagnetFile>?,
    alreadyLoading: Boolean
): Boolean = files == null && !alreadyLoading

/**
 * 磁力搜索结果里要展示的条目 —— 过滤掉疑似广告标题，但**绝不把列表清空**。
 *
 * ⚠️ 广告规则再准也可能误伤（历史事故 UZU-040：`"live" in title` 这类子串判断把唯一
 * 候选当广告杀了，站点明明有资源却报「未收录」），所以过滤后若一条不剩，必须原样退回
 * 全部 —— 宁可让用户看到一条疑似广告，也不能让「站点有结果」变成「未找到结果」。
 * 判据与 [visibleMagnetFiles] 同构：**只要「筛」存在，就必须保证不可能把整份结果筛没**。
 */
internal fun visibleSearchResults(items: List<DownloadLink>): List<DownloadLink> =
    items.filter { !isAdText(it.title) }.ifEmpty { items }
