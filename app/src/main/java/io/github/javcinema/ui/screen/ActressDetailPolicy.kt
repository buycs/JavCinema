package io.github.javcinema.ui.screen

import com.google.gson.JsonElement
import io.github.javcinema.data.model.AvmooStar

internal fun starDisplayName(star: AvmooStar): String {
    return star.starName
        ?: star.starName_ja
        ?: star.starName_en
        ?: star.starName_cn
        ?: star.starName_tw
        ?: ""
}

/**
 * 女优身体数据。
 *
 * API 的 `size` 字段**形状不固定**：多数时候是
 * `{"T":"163","B":"88","C":"D","W":"59","H":"85"}` 这样的对象
 * （T 身高 / B 胸围 / C 罩杯 / W 腰围 / H 臀围），偶尔是 `"T170 B88"` 这样的普通字符串。
 *
 * ⚠️ 早先的实现只判断「是不是字符串」，不是就直接 `toString()` —— 于是女优详情页顶部
 * 把**原始 JSON 连大括号**原样显示了出来（`{"T":"163",...}`），看起来像一串代码。
 * 现在把已知键翻成人能读的写法；形状无法识别时返回 null，宁可不显示，
 * 也不把 JSON 丢到界面上。
 */
data class ActressSize(
    val height: String? = null,
    val bust: String? = null,
    val cup: String? = null,
    val waist: String? = null,
    val hip: String? = null,
    /** 形状无法识别时保留的原文（例如 `"T170 B88"`）。 */
    val raw: String? = null
) {
    val isEmpty: Boolean
        get() = height == null && bust == null && cup == null && waist == null &&
            hip == null && raw == null

    /** 胸围的展示值：有罩杯时写成 `88(D)`，只有罩杯时退化成 `D`。 */
    val bustDisplay: String?
        get() = when {
            bust != null && cup != null -> "$bust($cup)"
            bust != null -> bust
            else -> cup
        }
}

private const val KEY_HEIGHT = "T"
private const val KEY_BUST = "B"
private const val KEY_CUP = "C"
private const val KEY_WAIST = "W"
private const val KEY_HIP = "H"

/**
 * 只保留真正有值的内容。
 *
 * 接口对「没有值」的表达**不统一**：`hometown` / `hobby` 给 null，
 * 而 `birthday` / `bloodType` 给的是**空串**（实测 60 条里 38 条生日是 `''`），
 * 字符串 `"null"` 也偶尔出现。三者在这里一并归一成「没有值」，
 * 避免界面上出现「生日：」这种后面空着的行。
 */
internal fun String?.cleanValue(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

/**
 * 女优资料页顶部「标签 + 值」的组装 —— 界面把它渲染成一排可换行的胶囊。
 *
 * 纯函数，可单测 —— 界面上**不该出现 null、空串、或原始 JSON**：
 * 每个字段先过 [cleanValue]，没有值就不产生这一项；全都没有值时返回空列表，
 * 调用方据此整块不渲染。
 *
 * 三围拆成**胸 / 腰 / 臀**三项分别展示（罩杯跟在胸围后面，如 `88(D)`），
 * 比挤成 `88(D)-59-85` 一行更好认；只有形状无法识别时才退回原文一项。
 *
 * 关于字段取舍（依据真实接口 `getStars` 60 条的统计，别照着字段名猜）：
 * - 收录：`birthday` 22/60、`hobby` 21/60、`hometown` 15/60、`bloodType` 7/60、
 *   `lastReleaseDate` 60/60
 * - **排除 `weight`**：60/60 有值，但**单位不统一** —— 163cm/108 像「斤」，
 *   158cm/58、164cm/50 又像 kg，带单位显示必然有一半是错的
 * - **排除 `constellation`**：59/60 是 `0`，属于废数据
 */
internal fun buildActressInfoRows(profile: ActressProfile): List<Pair<String, String>> {
    val size = profile.size
    return buildList<Pair<String, String>> {
        profile.actress.movieCount?.let { add("作品数" to "$it 部") }
        profile.birthday.cleanValue()?.let { add("生日" to it) }
        size?.height.cleanValue()?.let { add("身高" to "${it}cm") }
        size?.bustDisplay.cleanValue()?.let { add("胸" to it) }
        size?.waist.cleanValue()?.let { add("腰" to it) }
        size?.hip.cleanValue()?.let { add("臀" to it) }
        // 形状无法识别时（例如 "T170 B88"）没有胸腰臀可拆，退回原文一项。
        size?.raw.cleanValue()?.let { add("三围" to it) }
        profile.lastReleaseDate.cleanValue()?.let { add("最近作品" to it) }
        profile.hometown.cleanValue()?.let { add("出生地" to it) }
        profile.hobby.cleanValue()?.let { add("兴趣" to it) }
        profile.bloodType.cleanValue()?.let { add("血型" to it) }
    }
}

internal fun parseActressSize(size: JsonElement?): ActressSize? {
    if (size == null || size.isJsonNull) return null

    if (size.isJsonPrimitive) {
        val text = size.asString.trim()
        return text.takeIf { it.isNotEmpty() && it != "null" }?.let { ActressSize(raw = it) }
    }

    if (!size.isJsonObject) return null

    val obj = size.asJsonObject
    fun value(key: String): String? = obj.get(key)
        ?.takeIf { it.isJsonPrimitive }
        ?.asString
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    val parsed = ActressSize(
        height = value(KEY_HEIGHT),
        bust = value(KEY_BUST),
        cup = value(KEY_CUP),
        waist = value(KEY_WAIST),
        hip = value(KEY_HIP)
    )
    return parsed.takeIf { !it.isEmpty }
}
