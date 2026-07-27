package io.github.javcinema.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.javcinema.JAViewer
import io.github.javcinema.data.model.Genre
import io.github.javcinema.network.provider.AVMOProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    init {
        loadGenres()
    }

    fun loadGenres() {
        viewModelScope.launch {
            _uiState.value = GenreListUiState.Loading
            try {
                val ds = JAViewer.getDataSource()
                val isAvmoo = ds.name?.contains("AVMOO", ignoreCase = true) == true ||
                    ds.name == "骑兵" || ds.name == "步兵" || ds.name == "欧美"

                if (isAvmoo) {
                    loadGenresFromApi()
                } else {
                    loadGenresFromHtml()
                }
            } catch (e: Exception) {
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

        val apiGenres = response.data ?: emptyMap()
        val parsed = linkedMapOf<String, List<Genre>>()

        val typeLabels = mapOf(
            "-1" to "其他",
            "0" to "类别",
            "1" to "系列",
            "6" to "类型"
        )

        for ((key, genres) in apiGenres) {
            val label = typeLabels[key] ?: "类型 $key"
            parsed[label] = genres.map { g ->
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
