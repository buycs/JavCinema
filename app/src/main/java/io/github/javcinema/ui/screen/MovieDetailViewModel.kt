package io.github.javcinema.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.javcinema.JAViewer
import io.github.javcinema.data.model.Movie
import io.github.javcinema.data.model.MovieDetail
import io.github.javcinema.network.provider.AVMOProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class MovieDetailUiState {
    data object Loading : MovieDetailUiState()
    data class Success(val detail: MovieDetail) : MovieDetailUiState()
    data class Error(val message: String) : MovieDetailUiState()
}

class MovieDetailViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<MovieDetailUiState>(MovieDetailUiState.Loading)
    val uiState: StateFlow<MovieDetailUiState> = _uiState.asStateFlow()

    private val _detail = MutableStateFlow<MovieDetail?>(null)
    val detail: StateFlow<MovieDetail?> = _detail.asStateFlow()

    private val _isStarred = MutableStateFlow(false)
    val isStarred: StateFlow<Boolean> = _isStarred.asStateFlow()

    private val _relatedMovies = MutableStateFlow<List<Movie>>(emptyList())
    val relatedMovies: StateFlow<List<Movie>> = _relatedMovies.asStateFlow()

    private var movie: Movie? = null

    fun loadDetail(movieCode: String, movieLink: String? = null) {
        viewModelScope.launch {
            _uiState.value = MovieDetailUiState.Loading
            try {
                val ds = JAViewer.getDataSource()
                val isAvmoo = ds.name?.contains("AVMOO", ignoreCase = true) == true ||
                    ds.name == "骑兵" || ds.name == "步兵" || ds.name == "欧美"

                if (isAvmoo) {
                    loadDetailFromApi(movieCode, movieLink)
                } else {
                    loadDetailFromHtml(movieCode, movieLink)
                }
            } catch (e: Exception) {
                _uiState.value = MovieDetailUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun loadDetailFromApi(movieCode: String, movieLink: String?) {
        val api = JAViewer.AVMOO_API_SERVICE ?: run {
            _uiState.value = MovieDetailUiState.Error("API service not initialized")
            return
        }

        val movieId = movieLink ?: movieCode
        val response = withContext(Dispatchers.IO) {
            api.getMovie(listOf(movieId, "cn"))
        }

        val apiDetail = response.data ?: run {
            _uiState.value = MovieDetailUiState.Error("Movie not found")
            return
        }

        val parsed = withContext(Dispatchers.IO) { AVMOProvider.fromApiDetail(apiDetail) }

        movie = Movie().apply {
            id = apiDetail.movieId
            code = apiDetail.movieFanHao
            title = parsed.title
            coverUrl = parsed.coverUrl
        }

        _detail.value = parsed
        checkStarred()
        _uiState.value = MovieDetailUiState.Success(parsed)

        loadRelatedMovies(movieId)
    }

    private suspend fun loadRelatedMovies(movieId: String) {
        val api = JAViewer.AVMOO_API_SERVICE ?: return
        try {
            val response = withContext(Dispatchers.IO) {
                api.getRelatedMovies(listOf(movieId, "cn", 12))
            }
            val related = response.data?.let { AVMOProvider.fromApiList(it) } ?: emptyList()
            _relatedMovies.value = related
        } catch (_: Exception) { }
    }

    private suspend fun loadDetailFromHtml(movieCode: String, movieLink: String?) {
        val link = movieLink ?: "/cn/${movieCode.replace("-", "/")}"
        val service = JAViewer.SERVICE ?: run {
            _uiState.value = MovieDetailUiState.Error("Service not initialized")
            return
        }

        val response = withContext(Dispatchers.IO) { service.get(link) }
        val html = withContext(Dispatchers.IO) { response.string() }
        val parsed = withContext(Dispatchers.IO) { AVMOProvider.parseMoviesDetail(html) }

        movie = Movie().apply {
            code = movieCode
            title = parsed.title
            coverUrl = parsed.coverUrl
        }

        _detail.value = parsed
        checkStarred()
        _uiState.value = MovieDetailUiState.Success(parsed)
    }

    fun toggleStar() {
        val config = JAViewer.CONFIGURATIONS ?: return
        val m = movie ?: return

        if (config.starredMovies?.contains(m) == true) {
            config.starredMovies?.remove(m)
            _isStarred.value = false
        } else {
            config.starredMovies?.add(0, m)
            _isStarred.value = true
        }
        config.save()
    }

    private fun checkStarred() {
        val m = movie
        _isStarred.value = JAViewer.CONFIGURATIONS?.starredMovies?.contains(m) == true
    }
}
