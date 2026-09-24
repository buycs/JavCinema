package io.github.javcinema.ui.screen

/** 已登记的 AVMOO 系站点标识（`apiPath` 的第一段路径）。 */
private val AVMOO_SITE_PREFIXES = setOf("jav", "javu", "wav")

/**
 * 当前数据源走不走 AVMOO 系 JSON 接口。
 *
 * ⚠️ 判据是 `apiPath`（数据源的接口路径），**不是数据源的中文名**。
 * 名字（`骑兵` / `步兵` / `欧美`）只是展示文案：用户在设置里改、`properties.json` 换个叫法、
 * 站点换个译名，旧的中文名判断就失效了，然后**静默**落到上游遗留的 HTML 抓取器 ——
 * 那套抓取器面对现在的站点只会返回空列表，界面于是显示「暂无数据」，
 * 把「取数路径选错了」说成「站点没有内容」。
 *
 * 站点标识是 `apiPath` 的第一段路径：
 *
 * | apiPath | 名称 | 站点 |
 * |---|---|---|
 * | `/jav/data/api/` | 骑兵 | AVMOO |
 * | `/javu/data/api/` | 步兵 | AVSOX |
 * | `/wav/data/api/` | 欧美 | AVMEMO |
 *
 * 这套标识与 [io.github.javcinema.network.provider.GenreGroupLabels.forApiPath] 是同一套，
 * 改动时两处要一起看。
 *
 * ## 关于兜底分支
 *
 * `apiPath` 为空、或者出现没见过的站点标识时，**一律走接口**，不走 HTML：
 *
 * - 为空时与 `JavCinema.recreateService` 的兜底完全一致
 *   （`ds.apiPath?.trim('/')?.let { "/$it/" } ?: "/jav/data/api/"`，即按骑兵处理）；
 * - 没见过的标识宁可让接口去报错 —— 接口失败会抛异常、界面显示错误文案，
 *   而 HTML 抓取器失败会静默返回空列表、界面显示「暂无数据」。
 *   两个都是坏结果，但**前者是诚实的**，后者是本项目反复踩的「把失败说成没有」。
 *
 * 三个数据源目前都是 AVMOO 系，所以 HTML 分支实际不可达；
 * 保留它是为了将来 `properties.json` 真的要接一个非 AVMOO 站点时，有唯一的登记点。
 */
internal fun isAvmooApiSource(apiPath: String?): Boolean {
    val site = apiPath.orEmpty()
        .trim()
        .trimStart('/')
        .substringBefore('/')
    if (site.isEmpty()) return true
    if (AVMOO_SITE_PREFIXES.any { it.equals(site, ignoreCase = true) }) return true
    // ⚠️ 没见过的标识也走接口 —— **不要**把它改成 false，理由见上面「关于兜底分支」。
    return true
}
