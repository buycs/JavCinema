package io.github.javcinema.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import io.github.javcinema.JavCinema
import io.github.javcinema.data.model.Movie
import io.github.javcinema.network.BasicService
import io.github.javcinema.network.provider.AVMOProvider
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class SearchUiState {
    data object Idle : SearchUiState()
    data object Loading : SearchUiState()
    data class Success(val hasMore: Boolean = true) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}

class SearchViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _movies = MutableStateFlow<List<Movie>>(emptyList())
    val movies: StateFlow<List<Movie>> = _movies.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private var currentPage = 1
    private var hasMore = true
    private var currentQuery = ""
    private var loadJob: Job? = null
    private var lastVersion: Int = -1

    private fun preloadCovers(movies: List<Movie>) {
        val urls = movies.mapNotNull { it.coverUrl }
        if (urls.isEmpty()) return
        val loader = runCatching { JavCinema.instance.imageLoader }.getOrNull() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            urls.forEach { url ->
                try {
                    loader.enqueue(ImageRequest.Builder(JavCinema.instance)
                        .data(url)
                        .memoryCacheKey(url)
                        .build())
                } catch (e: Exception) {
                    android.util.Log.w("SearchVM", "preload failed: $url - ${e.message}")
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            JavCinema.dataSourceVersionFlow.drop(1).collectLatest { version ->
                lastVersion = version
                if (currentQuery.isNotEmpty()) search(currentQuery)
            }
        }
    }

    fun reset() {
        loadJob?.cancel()
        currentQuery = ""
        currentPage = 1
        hasMore = true
        _movies.value = emptyList()
        _isLoadingMore.value = false
        _uiState.value = SearchUiState.Idle
    }

    fun search(query: String) {
        val currentVersion = JavCinema.dataSourceVersionFlow.value
        if (query == currentQuery && lastVersion == currentVersion) return
        lastVersion = currentVersion
        if (query.isBlank()) return
        currentQuery = query
        currentPage = 1
        hasMore = true
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = SearchUiState.Loading
            loadPage(1)
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value || !hasMore) return
        _isLoadingMore.value = true
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loadPage(currentPage + 1)
            _isLoadingMore.value = false
        }
    }

    private suspend fun loadPage(page: Int) {
        try {
            val ds = JavCinema.getDataSource()
            val isAvmoo = ds.name?.contains("AVMOO", ignoreCase = true) == true ||
                ds.name == "骑兵" || ds.name == "步兵" || ds.name == "欧美"

            val parsed: List<Movie> = if (isAvmoo && JavCinema.AVMOO_API_SERVICE != null) {
                loadPageFromApi(page)
            } else {
                loadPageFromHtml(page)
            }

            if (parsed.isEmpty()) {
                hasMore = false
            }
            preloadCovers(parsed)

            _movies.value = if (page == 1) parsed else _movies.value + parsed
            currentPage = page
            _uiState.value = SearchUiState.Success(hasMore = hasMore)
        } catch (e: Exception) {
            android.util.Log.e("SearchVM", "loadPage error: ${e.message}", e)
            _uiState.value = SearchUiState.Error(e.message ?: "搜索失败")
        }
    }

    private suspend fun loadPageFromApi(page: Int): List<Movie> {
        val api = JavCinema.AVMOO_API_SERVICE ?: return emptyList()
        val body = listOf<Any>(mapOf("search" to currentQuery, "lang" to "cn"), 60, page)
        val response = withContext(Dispatchers.IO) { api.search(body) }
        val apiMovies = response.data ?: emptyList()
        return withContext(Dispatchers.IO) { AVMOProvider.fromApiList(apiMovies) }
    }

    private suspend fun loadPageFromHtml(page: Int): List<Movie> {
        val service = JavCinema.SERVICE ?: return emptyList()
        val encodedQuery = withContext(Dispatchers.IO) {
            URLEncoder.encode(currentQuery, "UTF-8")
        }
        val response = withContext(Dispatchers.IO) {
            service.get("${BasicService.LANGUAGE_NODE}/search/$encodedQuery/page/$page")
        }
        val html = withContext(Dispatchers.IO) { response.string() }
        return withContext(Dispatchers.IO) { AVMOProvider.parseMovies(html) }
    }
}
