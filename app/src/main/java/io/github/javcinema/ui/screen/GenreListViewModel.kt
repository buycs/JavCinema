package io.github.javcinema.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonElement
import io.github.javcinema.JavCinema
import io.github.javcinema.data.model.AvmooGenre
import io.github.javcinema.data.model.Genre
import io.github.javcinema.network.provider.AVMOProvider
import io.github.javcinema.network.provider.GenreGroupLabels
import io.github.javcinema.network.provider.groupGenresByLabel
import io.github.javcinema.network.provider.preferredGenreName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class GenreListUiState {
    data object Loading : GenreListUiState()
    data class Success(val genres: Map<String, List<Genre>>) : GenreListUiState()
    data class Error(val message: String) : GenreListUiState()
}

class GenreListViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<GenreListUiState>(GenreListUiState.Loading)
    val uiState: StateFlow<GenreListUiState> = _uiState.asStateFlow()

    private val _genres = MutableStateFlow<Map<String, List<Genre>>>(emptyMap())
    val genres: StateFlow<Map<String, List<Genre>>> = _genres.asStateFlow()

    private var lastVersion: Int = -1

    init {
        loadGenres()
        lastVersion = JavCinema.dataSourceVersionFlow.value
        viewModelScope.launch {
            JavCinema.dataSourceVersionFlow.collectLatest { version ->
                if (lastVersion != version) {
                    lastVersion = version
                    loadGenres()
                }
            }
        }
    }

    fun loadGenres() {
        viewModelScope.launch {
            _uiState.value = GenreListUiState.Loading
            try {
                val ds = JavCinema.getDataSource()
                android.util.Log.d("GenreListVM", "loadGenres: ds=${ds.name} link=${ds.link} apiPath=${ds.apiPath}")
                val isAvmoo = ds.name?.contains("AVMOO", ignoreCase = true) == true ||
                    ds.name == "骑兵" || ds.name == "步兵" || ds.name == "欧美"

                if (isAvmoo) {
                    loadGenresFromApi()
                } else {
                    loadGenresFromHtml()
                }
            } catch (e: Exception) {
                android.util.Log.e("GenreListVM", "loadGenres error", e)
                _uiState.value = GenreListUiState.Error(e.message ?: "加载失败")
            }
        }
    }

    private suspend fun loadGenresFromApi() {
        val api = JavCinema.AVMOO_API_SERVICE ?: run {
            _uiState.value = GenreListUiState.Error("API service not initialized")
            return
        }

        // 语言参数与详情页 / 女优 / 筛选保持一致：传 cn，站点返回的 genreName 即中文名。
        // 实测 lang=cn：骑兵 235/366 条为中文、缺的 131 条站点回退日文；步兵与欧美 100% 中文。
        val response = withContext(Dispatchers.IO) {
            api.getGenres(listOf("cn"))
        }

        // ⚠️ 接口永远回 HTTP 200，失败信号在 `code` 里（实测 404 + data:null）——
        // 不看 code 就会把接口报错渲染成「类别页为空」。
        requireAvmooSuccess(response.code)
        val rawData = response.data
        // 分组标签取站点自己的 cn 字典，只有骑兵的 type 7 单独标成 AV OPEN（详见 GenreGroupPolicy）。
        val labels = GenreGroupLabels.forApiPath(JavCinema.getDataSource().apiPath)

        // 接口有两种形状，统一成「(type, 该组类别)」列表后交给纯函数归并：
        //  - 骑兵：data 是 dict，key 就是 type，另含站点自加的 "-1"
        //  - 步兵 / 欧美：data 是 list of list，下标即 type，元素里也带 type 字段
        val groups: List<Pair<Int?, List<Genre>>> = when {
            rawData == null -> emptyList()

            rawData.isJsonObject -> {
                val obj = rawData.asJsonObject
                val ordered = mutableListOf<Pair<Int?, List<Genre>>>()
                for (type in 0 until labels.typeCount) {
                    obj.get(type.toString())?.let { ordered += type to genresOf(it) }
                }
                // 骑兵特有的 -1 组（パラダイスTV / 促销精选 / AV OPEN 2014・2015），站点排在最后
                obj.get("-1")?.let { ordered += -1 to genresOf(it) }
                // 兜底：站点以后再加新 type 也不会被丢掉，落到兜底标签
                for ((key, element) in obj.entrySet()) {
                    val type = key.toIntOrNull() ?: continue
                    if (type in 0 until labels.typeCount || type == -1) continue
                    ordered += type to genresOf(element)
                }
                ordered
            }

            rawData.isJsonArray -> rawData.asJsonArray.mapNotNull { element ->
                val list = parseApiGenres(element)
                // ⚠️ 用元素自带的 type 字段，**不要用下标猜** —— 以前靠
                // 「最后一段就是 -1」的假设改写 key，站点一旦多返回一段，
                // 中间那段会被 noAvOpen 判断整组丢掉（步兵 type 7 有 39 条类别）。
                if (list.isEmpty()) null else list.first().type to list.map { toGenre(it) }
            }

            else -> emptyList()
        }

        val parsed = groupGenresByLabel(groups, labels)
        _genres.value = parsed
        _uiState.value = GenreListUiState.Success(genres = parsed)
    }

    private fun parseApiGenres(element: JsonElement): List<AvmooGenre> =
        Gson().fromJson(element, Array<AvmooGenre>::class.java)?.toList().orEmpty()

    private fun genresOf(element: JsonElement): List<Genre> =
        parseApiGenres(element).map { toGenre(it) }

    private fun toGenre(g: AvmooGenre) = Genre.create(
        // 请求已传 cn，站点的 genreName 就是中文名；这里再按 cn -> genreName -> ja 兜一层。
        preferredGenreName(g.genreName_cn, g.genreName, g.genreName_ja),
        g.genreId ?: ""
    )

    private suspend fun loadGenresFromHtml() {
        val service = JavCinema.SERVICE ?: run {
            _uiState.value = GenreListUiState.Error("Service not initialized")
            return
        }

        val response = withContext(Dispatchers.IO) { service.getGenre() }
        val html = withContext(Dispatchers.IO) { response.string() }
        val parsed = withContext(Dispatchers.IO) { AVMOProvider.parseGenres(html) }

        _genres.value = parsed
        _uiState.value = GenreListUiState.Success(genres = parsed)
    }
}
