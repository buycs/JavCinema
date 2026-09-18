package io.github.javcinema.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.javcinema.JavCinema
import io.github.javcinema.data.model.Movie
import io.github.javcinema.network.provider.AVMOProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class MovieListUiState {
    data object Loading : MovieListUiState()
    data class Success(val hasMore: Boolean = true) : MovieListUiState()
    data class Error(val message: String) : MovieListUiState()
}

class MovieListViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<MovieListUiState>(MovieListUiState.Loading)
    val uiState: StateFlow<MovieListUiState> = _uiState.asStateFlow()

    private val _movies = MutableStateFlow<List<Movie>>(emptyList())
    val movies: StateFlow<List<Movie>> = _movies.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private var currentPage = 1
    private var hasMore = true
    private var baseUrl: String = ""
    private var loadJob: Job? = null
    private var moreJob: Job? = null
    private var lastVersion: Int = -1

    init {
        viewModelScope.launch {
            JavCinema.dataSourceVersionFlow.drop(1).collectLatest { version ->
                lastVersion = version
                if (baseUrl.isNotEmpty()) refresh()
            }
        }
    }

    fun load(url: String) {
        val currentVersion = JavCinema.dataSourceVersionFlow.value
        if (url == baseUrl && lastVersion == currentVersion) return
        lastVersion = currentVersion
        baseUrl = url
        currentPage = 1
        hasMore = true
        moreJob?.cancel()
        _isLoadingMore.value = false
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = MovieListUiState.Loading
            loadPage(1)
        }
    }

    fun loadMore() {
        if (!shouldStartLoadMore(_isLoadingMore.value, hasMore, loadJob?.isActive == true)) return
        _isLoadingMore.value = true
        moreJob?.cancel()
        moreJob = viewModelScope.launch {
            try {
                loadPage(currentPage + 1)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun refresh() {
        currentPage = 1
        hasMore = true
        moreJob?.cancel()
        _isLoadingMore.value = false
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = MovieListUiState.Loading
            loadPage(1)
        }
    }

    private suspend fun loadPage(page: Int) {
        try {
            val ds = JavCinema.getDataSource()
            val isAvmoo = ds.name?.contains("AVMOO", ignoreCase = true) == true ||
                ds.name == "骑兵" || ds.name == "步兵" || ds.name == "欧美"

            if (isAvmoo) {
                loadPageFromApi(page)
            } else {
                loadPageFromHtml(page)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e("MovieListVM", "loadPage error: ${e.message}", e)
            _uiState.value = MovieListUiState.Error(e.message ?: "加载失败")
        }
    }

    private suspend fun loadPageFromApi(page: Int) {
        val api = JavCinema.AVMOO_API_SERVICE ?: run {
            _uiState.value = MovieListUiState.Error("API service not initialized")
            return
        }

        val filterType = when {
            baseUrl.contains("/actress/") || baseUrl.contains("/star/") || baseUrl.startsWith("star/") -> "star"
            baseUrl.contains("/genre/") || baseUrl.startsWith("genre/") -> "genre"
            baseUrl.contains("/studio/") || baseUrl.startsWith("studio/") -> "studio"
            baseUrl.contains("/director/") || baseUrl.startsWith("director/") -> "director"
            baseUrl.contains("/series/") || baseUrl.startsWith("series/") -> "series"
            baseUrl.contains("/label/") || baseUrl.startsWith("label/") -> "label"
            else -> null
        }

        val filterId = baseUrl.substringAfterLast("/").substringBefore('?').takeIf { it.isNotEmpty() }

        if (filterType != null && filterId != null) {
            val response = withContext(Dispatchers.IO) {
                api.getFilterMovies(listOf(filterType, filterId, "cn", 60, page))
            }

            val apiMovies = response.data ?: emptyList()
            val parsed = withContext(Dispatchers.IO) { AVMOProvider.fromApiList(apiMovies) }

            if (apiMovies.isEmpty()) {
                hasMore = false
            }

            _movies.value = if (page == 1) parsed else _movies.value + parsed
            currentPage = page
            _uiState.value = MovieListUiState.Success(hasMore = hasMore)
        } else {
            loadPageFromHtml(page)
        }
    }

    private suspend fun loadPageFromHtml(page: Int) {
        val service = JavCinema.SERVICE ?: run {
            _uiState.value = MovieListUiState.Error("Service not initialized")
            return
        }

        val response = withContext(Dispatchers.IO) {
            service.get("$baseUrl/page/$page")
        }
        val html = withContext(Dispatchers.IO) { response.string() }
        val parsed = withContext(Dispatchers.IO) { AVMOProvider.parseMovies(html) }

        if (parsed.isEmpty()) {
            hasMore = false
        }

        _movies.value = if (page == 1) parsed else _movies.value + parsed
        currentPage = page
        _uiState.value = MovieListUiState.Success(hasMore = hasMore)
    }
}
