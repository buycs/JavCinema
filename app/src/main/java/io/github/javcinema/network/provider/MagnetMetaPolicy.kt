package io.github.javcinema.network.provider

import java.util.Locale

/**
 * 磁力结果的「体积 + 日期」归一化。
 *
 * 三个磁力源的字段来源不一样：BTSO / BTSEARCH 走 JSON API，字段天生是干净的
 * （体积 `2.02 GB`、日期 `2025-10-28`）；无极磁链（cili.info）抓的是 HTML，
 * 而且**每个搜索结果行只有 2 个 `<td>`** —— 标题 + 一个 meta 单元格，
 * 体积和日期被塞在同一个 meta 里：
 *
 * ```html
 * <tr>
 *   <td class="result-title"><a href="/!ko2s">SSIS-001</a></td>
 *   <td class="result-meta"><div>2.02GB</div><div class="result-date">2025-10-28</div></td>
 * </tr>
 * ```
 *
 * 所以 `cells[1].text()` 拿到的是 `"2.02GB 2025-10-28"` —— 体积与日期粘成一串；
 * 而按「第 3 列才是日期」取值的旧写法永远只能取到空串。UI 上的表现就是
 * 无极那一栏的副标题跟另外两个源长得不一样（大小和日期连在一起、中间也没有空格）。
 *
 * 这里改成从**整段文本**里各取所需，因此 2 列 / 3 列结构、带不带时分秒都能处理；
 * 并且是纯函数，可以直接单测。
 */
internal data class MagnetMeta(val size: String, val date: String)

private val SIZE_RE = Regex("""(\d+(?:\.\d+)?)\s*(B|KB|MB|GB|TB)""", RegexOption.IGNORE_CASE)
private val DATE_RE = Regex("""\d{4}-\d{2}-\d{2}""")

/** 从一段混杂文本里抽出体积与日期。 */
internal fun parseMagnetMeta(text: String?): MagnetMeta =
    MagnetMeta(size = normalizeSize(text), date = normalizeDate(text))

/**
 * `"2.02GB"` → `"2.02 GB"`、`"3.3 GB"` → `"3.30 GB"`。
 *
 * 统一成与 BTSO / BTSEARCH 相同的 `%.2f 单位` 写法（带空格、两位小数），
 * 三个源的副标题看起来才是一套。取不到体积时返回空串 —— UI 对空串不渲染。
 *
 * 固定用 [Locale.US]：小数点必须是 `.`，不能跟随系统语言变成 `,`。
 */
internal fun normalizeSize(text: String?): String {
    val match = SIZE_RE.find(text.orEmpty()) ?: return ""
    val value = match.groupValues[1].toDoubleOrNull() ?: return ""
    val unit = match.groupValues[2].uppercase(Locale.US)
    return String.format(Locale.US, "%.2f %s", value, unit)
}

/**
 * 取文本里第一个 `yyyy-MM-dd`。
 * 详情页的「发布日期」带时分秒（`"2025-10-29 04:30:14"`），列表页不带 ——
 * 截成同一种格式，避免同一个日期在两处显示成两个样子。
 */
internal fun normalizeDate(text: String?): String =
    DATE_RE.find(text.orEmpty())?.value.orEmpty()
