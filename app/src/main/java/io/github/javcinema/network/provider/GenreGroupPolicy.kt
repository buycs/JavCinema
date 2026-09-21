package io.github.javcinema.network.provider

/**
 * 类别页的分组标签。
 *
 * 站点把类别按 `type` 分成若干组，分组名由站点前端 i18n 的 `genreTypes.*` 定义，
 * 语义依次为 theme / character / costume / body / sexActs / sexPlays / genre / other。
 *
 * 标签**直接取站点自己的 cn 字典**（主题 / 角色 / 服装 / 身体 / 性行为 / 玩法 / 类别 / 其他）：
 * 类别页请求的语言就是 `cn`（见 `GenreListViewModel.loadGenresFromApi`），
 * 分组名与组内类别名出自同一套字典，tab 名不会和里面的 chip 语言割裂。
 * 三个源共用同一套前端，所以步兵与欧美的标签完全相同。
 *
 * ⚠️ 骑兵的 `type 7` 是个例外：实测该组 27 条**整组都是 AV OPEN 2016 各部门**
 * （站点自己给它标「其他」）。单独成组并叫「AV OPEN」比出现两个同名「其他」可读。
 * 步兵与欧美的 `type 7` 是普通类别（场所 / 节日），仍叫「其他」。
 *
 * ⚠️ 骑兵还会出现 `-1`（64 条：パラダイスTV、促销精选、AV OPEN 2014/2015 等），
 * 它不在 0~7 里，落到兜底标签「其他」，并且**排在最后一组**。
 */
data class GenreGroupLabels(
    private val byType: List<String>,
    private val fallback: String
) {
    /** 受支持的 type 个数（不含骑兵特有的 `-1`）。 */
    val typeCount: Int get() = byType.size

    /**
     * 取某个 `type` 的分组标签。
     *
     * `null`（响应里缺 `type` 字段）与越界值（骑兵的 `-1`）都落到兜底标签。
     */
    fun at(type: Int?): String =
        if (type == null || type < 0 || type >= byType.size) fallback else byType[type]

    companion object {
        /** 站点 cn 字典给出的分组名，下标即 `type`；三源共用这一份。 */
        private val CN = listOf("主题", "角色", "服装", "身体", "性行为", "玩法", "类别", "其他")

        /** 骑兵（jav）：第 8 段整组都是 AV OPEN 专题，与「其他」分开。 */
        val JAV = GenreGroupLabels(
            CN.dropLast(1) + "AV OPEN",
            "其他"
        )

        /** 步兵（javu）：没有 `-1` 组，`type 7` 就是普通的「其他」。 */
        val JAVU = GenreGroupLabels(CN, "其他")

        /** 欧美（wav）：与 [JAVU] 同标签；仍单独保留一个常量，按 `apiPath` 分支更清楚。 */
        val WAV = GenreGroupLabels(CN, "其他")

        /**
         * 按数据源的 `apiPath` 选分组标签。
         *
         * 站点标识是 apiPath 的第一段路径：`/jav/` 骑兵、`/javu/` 步兵、`/wav/` 欧美。
         *
         * ⚠️ 这里**必须按 apiPath 判断，不能按数据源的中文名**（`ds.name == "步兵"`）——
         * 名字是用户可改的展示文案，改个名或加个源就失效了。
         * 无法识别时按骑兵处理（骑兵是默认数据源）。
         */
        fun forApiPath(apiPath: String?): GenreGroupLabels {
            val site = apiPath.orEmpty()
                .trimStart('/')
                .substringBefore('/')
            return when {
                site.equals("wav", ignoreCase = true) -> WAV
                site.equals("javu", ignoreCase = true) -> JAVU
                else -> JAV
            }
        }
    }
}

/**
 * 类别的显示名：**优先简体中文**，没有中文才回退。
 *
 * 接口每条类别自带四种语言：
 * ```
 * {"genreName_ja":"セクシー","genreName_en":"Sexy",
 *  "genreName_cn":"性感的","genreName_tw":"性感的","genreName":"性感的"}
 * ```
 * `genreName` 是站点按**请求语言**填好的名字：类别页传 `cn`，它给的就是中文
 * （实测骑兵 235/366 条与 `genreName_cn` 相同、步兵与欧美 100% 相同），
 * 缺中文的那 131 条站点自己回退成日文。
 *
 * ⚠️ 但仍要判 `isBlank()`：`genreName_cn` 这个字段缺中文时给的是**空串而不是 null**，
 * 只写 `?:` 回退会拿到空串、类别名直接消失。这里显式再兜一层，
 * 万一将来某个调用点忘了传 `cn`，界面也不会整页变日文。
 */
internal fun preferredGenreName(
    cn: String?,
    siteDefault: String?,
    ja: String?
): String = listOf(cn, siteDefault, ja)
    .firstOrNull { !it.isNullOrBlank() }
    ?.trim()
    .orEmpty()

/**
 * 把「(type, 该组类别)」列表按标签归并成类别页要显示的 `标签 -> 类别` 有序表。
 *
 * 纯函数，可单测。**按下标猜 type 是错的**：步兵 / 欧美的 `data` 是 list of list，
 * 一旦站点多返回一段，靠「最后一段就是 -1」的假设就会把中间那段整组丢掉
 * （实测步兵 `type 7` 有 39 条类别）。这里一律用元素自带的 `type` 字段。
 *
 * 相同标签会**合并**（而不是覆盖），且保留首次出现的位置。
 */
fun <T> groupGenresByLabel(
    groups: List<Pair<Int?, List<T>>>,
    labels: GenreGroupLabels
): LinkedHashMap<String, List<T>> {
    val map = linkedMapOf<String, List<T>>()
    for ((type, items) in groups) {
        if (items.isEmpty()) continue
        val label = labels.at(type)
        val existing = map[label]
        map[label] = if (existing == null) items else existing + items
    }
    return map
}
