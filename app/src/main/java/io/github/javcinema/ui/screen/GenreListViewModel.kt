package io.github.javcinema.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import io.github.javcinema.JAViewer
import io.github.javcinema.data.model.AvmooGenre
import io.github.javcinema.data.model.Genre
import io.github.javcinema.network.provider.AVMOProvider
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
        lastVersion = JAViewer.dataSourceVersionFlow.value
        viewModelScope.launch {
            JAViewer.dataSourceVersionFlow.collectLatest { version ->
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
                val ds = JAViewer.getDataSource()
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
        val api = JAViewer.AVMOO_API_SERVICE ?: run {
            _uiState.value = GenreListUiState.Error("API service not initialized")
            return
        }

        val response = withContext(Dispatchers.IO) {
            api.getGenres(listOf("types", 60))
        }

        val rawData = response.data
        val apiGenres = if (rawData != null && rawData.isJsonObject) {
            val map = linkedMapOf<String, List<AvmooGenre>>()
            for ((key, element) in rawData.asJsonObject.entrySet()) {
                val list = Gson().fromJson(element, Array<AvmooGenre>::class.java)?.toList()
                if (list != null) map[key] = list
            }
            map
        } else if (rawData != null && rawData.isJsonArray) {
            val map = linkedMapOf<String, List<AvmooGenre>>()
            val arr = rawData.asJsonArray
            for (i in 0 until arr.size()) {
                val element = arr[i]
                val list = Gson().fromJson(element, Array<AvmooGenre>::class.java)?.toList()
                if (list != null) {
                    val key = if (i == arr.size() - 1 && arr.size() > 7) "-1" else i.toString()
                    map[key] = list
                }
            }
            map
        } else {
            emptyMap()
        }

        val parsed = linkedMapOf<String, List<Genre>>()

        val typeLabels = mapOf(
            "0" to "热门类型",
            "1" to "职业扮演",
            "2" to "衣着造型",
            "3" to "身材特征",
            "4" to "性爱玩法",
            "5" to "道具调教",
            "6" to "制作系列",
            "7" to "AV OPEN",
            "-1" to "其他"
        )

        val ds = JAViewer.getDataSource()
        val noAvOpen = ds.name == "步兵" || ds.name == "欧美"

        val orderedKeys = listOf("0", "1", "2", "3", "4", "5", "6", "7", "-1")
        for (key in orderedKeys) {
            if (noAvOpen && key == "7") continue
            val genres = apiGenres[key] ?: continue
            val label = typeLabels[key] ?: "类型 $key"
            parsed[label] = genres.map { g ->
                Genre.create(
                    g.genreName ?: g.genreName_ja ?: g.genreName_cn ?: "",
                    g.genreId ?: ""
                )
            }
        }
        for ((key, genres) in apiGenres) {
            if (key in orderedKeys) continue
            parsed["类型 $key"] = genres.map { g ->
                Genre.create(
                    g.genreName ?: g.genreName_ja ?: g.genreName_cn ?: "",
                    g.genreId ?: ""
                )
            }
        }

        _genres.value = parsed
        _uiState.value = GenreListUiState.Success(genres = parsed)
    }

    private suspend fun loadGenresFromHtml() {
        val service = JAViewer.SERVICE ?: run {
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
