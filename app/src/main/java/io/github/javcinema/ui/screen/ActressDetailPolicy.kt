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
 * 女优资料页顶部「标签 + 值」的组装 —— 界面把它渲染成胶囊。
 *
 * 纯函数，可单测 —— 界面上**不该出现 null、空串、或原始 JSON**：
 * 每个字段先过 [cleanValue]，没有值就不产生这一项；全都没有值时返回空列表，
 * 调用方据此整块不渲染。
 *
 * 三围拆成**胸 / 腰 / 臀**三项分别展示（罩杯跟在胸围后面，如 `88(D)`），
 * 比挤成 `88(D)-59-85` 一行更好认；只有形状无法识别时才退回原文一项。
 *
 * ## 顺序（界面把非标题字段从内容区左边缘起铺满一行再换行，所以顺序就是读的顺序）
 *
 * 大体按「覆盖率高的在前」分四组（血型是例外，按语义归进基本资料），
 * 免得有价值的字段被稀疏字段压到要滚动才看得到：
 * 1. **统计**：作品数（含可下载）、最近作品 —— 三源实测 60/60 全有值。
 *    这两项同时是 [splitActressHeadline] 挑出来的**标题行**，界面各占一行、
 *    与头像等高对齐，所以必须排在最前
 * 2. **基本资料**：生日、星座、血型 —— 骑兵 68/180 有生日，步兵/欧美没有；星座由生日
 *    推得，与生日必定同现，所以挨着放；血型 7/60，但它和生日/星座同属人的固有属性，
 *    排在一起比按覆盖率拆到末尾更好读
 * 3. **身体数据**：身高、胸、腰、臀（或识别不出时的三围原文）—— 骑兵 27/60，其余两源无
 * 4. **其他**：出生地 15/60、兴趣 21/60
 *
 * ## 字段取舍（依据真实接口 `getStars` 三源各 60 条的统计，别照着字段名猜）
 * - **不取站点的 `constellation`**：180 条里只有 8 条非零，而这 8 条**全都同时有生日**
 *   —— 相对生日零增量信息；且其中一条（`1987-05-25` 给了 4 = 巨蟹，应为双子）与自身生日矛盾。
 *   星座改由 [zodiacOf] 从生日算，覆盖率直接抬到与生日同档（37.8%）。
 * - **排除 `weight`**：三源都 60/60 有值，但**单位不统一也不可信** —— 骑兵 31~107，
 *   163cm/107 只能是「斤」，而同批 24 条里 16 条按 kg 才落在正常 BMI 区间；
 *   步兵 6~29、欧美 1~4 更是压根不是体重。带单位显示必然有一半是错的。
 * - **排除 `blog` / `createTime`**：blog 三源全空；createTime 是入库时间戳，对用户无意义。
 * - `from`（heyzo / brazzers 这类所属片商）只有步兵、欧美有，骑兵响应里根本没这个键，
 *   且值是英文 slug，暂未收录。
 */
internal fun buildActressInfoRows(profile: ActressProfile): List<Pair<String, String>> {
    val size = profile.size
    return buildList<Pair<String, String>> {
        addMovieCountRow(profile)
        profile.lastReleaseDate.cleanValue()?.let { add(LABEL_LAST_RELEASE to it) }
        profile.birthday.cleanValue()?.let { birthday ->
            add("生日" to birthday)
            // 星座跟着生日走：它本来就是从生日推出来的。
            zodiacOf(birthday)?.let { add("星座" to it) }
        }
        profile.bloodType.cleanValue()?.let { add("血型" to it) }
        size?.height.cleanValue()?.let { add("身高" to "${it}cm") }
        size?.bustDisplay.cleanValue()?.let { add("胸" to it) }
        size?.waist.cleanValue()?.let { add("腰" to it) }
        size?.hip.cleanValue()?.let { add("臀" to it) }
        // 形状无法识别时（例如 "T170 B88"）没有胸腰臀可拆，退回原文一项。
        size?.raw.cleanValue()?.let { add("三围" to it) }
        profile.hometown.cleanValue()?.let { add("出生地" to it) }
        profile.hobby.cleanValue()?.let { add("兴趣" to it) }
    }
}

/**
 * 作品数与可下载数并成一项：`4617 部 · 可下载 2433`。
 *
 * 拆成两个胶囊会让「4617 部」和「2433」看起来是两回事，而它们其实是同一个数的总量与子集；
 * 参考项目也是一行写完（`197 部作品 · 68 部可下载`）。
 * 站点没给 `downloadMovieCount` 时退化成只有总数。
 *
 * [LABEL_DOWNLOADABLE] 单独抽成常量：界面渲染时要把这一段按**标签字重**显示
 * （整串都加粗的话，「可下载」会被读成第三个数字），按字面量找串太脆。
 */
private fun MutableList<Pair<String, String>>.addMovieCountRow(profile: ActressProfile) {
    val total = profile.actress.movieCount ?: return
    val downloadable = profile.downloadMovieCount?.takeIf { it <= total }
    add(
        LABEL_MOVIE_COUNT to
            if (downloadable == null) "$total 部" else "$total 部 · $LABEL_DOWNLOADABLE $downloadable"
    )
}

internal const val LABEL_MOVIE_COUNT = "作品数"
internal const val LABEL_LAST_RELEASE = "最近作品"
internal const val LABEL_DOWNLOADABLE = "可下载"

/**
 * 把 [buildActressInfoRows] 的结果分成「标题行」与「逐行显示的细节」两组。
 *
 * 界面上头像右侧是：名称一行、[LABEL_MOVIE_COUNT] 一行、[LABEL_LAST_RELEASE] 一行 ——
 * 这三行整体与头像等高对齐；剩下的字段挪到头像下面，从内容区左边缘起铺满一行再换行。
 * 所以在策略层就切好，而不是在界面里按位置写死「取前两项」：某项没值时按位置取会错抓
 * 一个字段到标题区。
 */
internal fun splitActressHeadline(
    rows: List<Pair<String, String>>
): Pair<List<Pair<String, String>>, List<Pair<String, String>>> =
    rows.partition { it.first == LABEL_MOVIE_COUNT || it.first == LABEL_LAST_RELEASE }

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

/**
 * 星座档位：每项是「该星座起始的 月\*100+日」，按月份升序，[ZODIAC_NAMES] 与之同下标。
 *
 * ⚠️ 档位表**从 1/20 起、不含 12/22 之后的跨年处理**：1 月 1~19 日属于摩羯座，
 * 而摩羯在表里排在末尾（1222），所以要靠 [zodiacOf] 里「比第一档还早就回绕到末档」
 * 这一条特判来处理，别以为漏了一档。
 */
private val ZODIAC_FROM_MONTH_DAY = intArrayOf(
    120,   // 水瓶座 1/20
    219,   // 双鱼座 2/19
    321,   // 白羊座 3/21
    420,   // 金牛座 4/20
    521,   // 双子座 5/21
    622,   // 巨蟹座 6/22
    723,   // 狮子座 7/23
    823,   // 处女座 8/23
    923,   // 天秤座 9/23
    1024,  // 天蝎座 10/24
    1123,  // 射手座 11/23
    1222   // 摩羯座 12/22
)

private val ZODIAC_NAMES = listOf(
    "水瓶座", "双鱼座", "白羊座", "金牛座", "双子座", "巨蟹座",
    "狮子座", "处女座", "天秤座", "天蝎座", "射手座", "摩羯座"
)

private val BIRTHDAY_PATTERN = Regex("""^\s*(\d{4})-(\d{1,2})-(\d{1,2})\s*$""")

/**
 * 由生日（`yyyy-MM-dd`）推算星座，取不到或格式不对时返回 null。
 *
 * 不用站点的 `constellation` 字段：实测 180 条里只有 8 条非零、且这 8 条全都同时有生日
 * （相对生日零增量），其中 `1987-05-25` 那条还给了 4 = 巨蟹（应为双子座），自身就矛盾。
 *
 * 边界日按通行的「起始日含在内」处理：1/19 摩羯、1/20 水瓶，2/18 水瓶、2/19 双鱼，以此类推。
 * minSdk 21 且没开 core library desugaring，所以这里只做整数比较，不引 `java.time`。
 */
internal fun zodiacOf(birthday: String?): String? {
    val matched = birthday?.let { BIRTHDAY_PATTERN.matchEntire(it) } ?: return null
    val month = matched.groupValues[2].toIntOrNull() ?: return null
    val day = matched.groupValues[3].toIntOrNull() ?: return null
    if (month !in 1..12 || day !in 1..31) return null

    val monthDay = month * 100 + day
    val index = ZODIAC_FROM_MONTH_DAY.indexOfLast { monthDay >= it }
    // 1/1~1/19 落在第一档之前，回绕到末档摩羯座。
    return ZODIAC_NAMES[if (index < 0) ZODIAC_NAMES.lastIndex else index]
}
