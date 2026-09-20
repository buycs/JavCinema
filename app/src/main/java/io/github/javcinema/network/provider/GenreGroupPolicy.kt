package io.github.javcinema.network.provider

/**
 * 类别页的分组标签。
 *
 * 站点把类别按 `type` 分成若干组，分组名由站点前端 i18n 的 `genreTypes.*` 定义，
 * 语义依次为 theme / character / costume / body / sexActs / sexPlays / genre / other。
 * 应用请求类别时站点按自己的语言返回类别名：骑兵与步兵是日文，欧美没有日文名、回退英文，
 * 因此**分组标签也跟随同样的语言**，避免出现「中文 tab + 日文类别名」的割裂。
 *
 * ⚠️ 骑兵的 `type 7` 是个例外：实测该组 27 条**整组都是 AV OPEN 2016 各部门**
 * （站点自己给它标「其他」）。单独成组并叫「AV OPEN」比出现两个同名「其他」可读。
 * 步兵与欧美的 `type 7` 是普通类别（場所 / 节日），仍按站点语义叫「その他」/「Other」。
 *
 * ⚠️ 骑兵还会出现 `-1`（64 条：パラダイスTV、促销精选、AV OPEN 2014/2015 等），
 * 它不在 0~7 里，落到兜底标签「その他」，并且**排在最后一组**。
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
        /** 骑兵（jav）：第 8 段整组都是 AV OPEN 专题。 */
        val JAV = GenreGroupLabels(
            listOf("テーマ", "キャラクター", "コスチューム", "身体", "性行為", "プレイ", "ジャンル", "AV OPEN"),
            "その他"
        )

        /** 步兵（javu）：没有 `-1` 组，`type 7` 就是普通的「その他」。 */
        val JAVU = GenreGroupLabels(
            listOf("テーマ", "キャラクター", "コスチューム", "身体", "性行為", "プレイ", "ジャンル", "その他"),
            "その他"
        )

        /** 欧美（wav）：站点没有日文类别名、回退英文，标签也跟随英文。 */
        val WAV = GenreGroupLabels(
            listOf("Theme", "Character", "Costume", "Body", "Sex Acts", "Sex Plays", "Genre", "Other"),
            "Other"
        )

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
