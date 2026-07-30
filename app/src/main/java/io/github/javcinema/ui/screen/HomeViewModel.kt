package io.github.javcinema.ui.screen

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import io.github.javcinema.JAViewer
import io.github.javcinema.data.model.AvmooMovieListResponse
import io.github.javcinema.data.model.Movie
import io.github.javcinema.network.provider.AVMOProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class HomeUiState {
    data object Loading : HomeUiState()
    data class Success(
        val movies: List<Movie>,
        val hasMore: Boolean = true
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _movies = MutableStateFlow<List<Movie>>(emptyList())
    val movies: StateFlow<List<Movie>> = _movies.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private var currentPage = 1
    private var isRefreshing = false
    private var hasMore = true
    private var section: String = ""
    private var loadJob: kotlinx.coroutines.Job? = null
    private var lastVersion: Int = -1

    private fun preloadCovers(movies: List<Movie>) {
        val urls = movies.mapNotNull { it.coverUrl }
        if (urls.isEmpty()) return
        val loader = runCatching { JAViewer.instance.imageLoader }.getOrNull() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            urls.forEach { url ->
                try {
                    loader.enqueue(ImageRequest.Builder(JAViewer.instance)
                        .data(url)
                        .memoryCacheKey(url)
                        .build())
                } catch (e: Exception) {
                    Log.w("HomeViewModel", "preload failed: $url - ${e.message}")
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            JAViewer.dataSourceVersionFlow.drop(1).collectLatest { version ->
                lastVersion = version
                if (section.isNotEmpty()) refresh()
            }
        }
    }

    fun setSection(section: String) {
        val currentVersion = JAViewer.dataSourceVersionFlow.value
        if (this.section != section || lastVersion != currentVersion) {
            this.section = section
            lastVersion = currentVersion
            refresh()
        }
    }

    fun refresh() {
        isRefreshing = true
        currentPage = 1
        hasMore = true
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
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
            val ds = JAViewer.getDataSource()
            val isAvmoo = ds.name?.contains("AVMOO", ignoreCase = true) == true ||
                ds.name == "骑兵" || ds.name == "步兵" || ds.name == "欧美"

            if (isAvmoo) {
                loadPageFromApi(page)
            } else {
                loadPageFromHtml(page)
            }
        } catch (e: Exception) {
            Log.e("HomeViewModel", "loadPage error: ${e.message}", e)
            _uiState.value = HomeUiState.Error(e.message ?: "Unknown error")
            isRefreshing = false
        }
    }

    private suspend fun loadPageFromApi(page: Int) {
        val api = JAViewer.AVMOO_API_SERVICE ?: run {
            _uiState.value = HomeUiState.Error("API service not initialized")
            return
        }

        val response: AvmooMovieListResponse = withContext(Dispatchers.IO) {
            when (section) {
                "popular" -> api.getFilterMovies(listOf("popular", "", "cn", 60, page))
                "released" -> api.getFilterMovies(listOf("released", "", "cn", 60, page))
                else -> api.getMovies(listOf("home", 60, page))
            }
        }

        val apiMovies = response.data ?: emptyList()
        Log.d("HomeViewModel", "API returned ${apiMovies.size} movies")

        val parsed = withContext(Dispatchers.IO) { AVMOProvider.fromApiList(apiMovies) }

        if (apiMovies.isEmpty()) {
            hasMore = false
        }
        preloadCovers(parsed)

        if (isRefreshing || page == 1) {
            _movies.value = parsed
            isRefreshing = false
        } else {
            _movies.value = _movies.value + parsed
        }

        currentPage = page
        _uiState.value = HomeUiState.Success(
            movies = _movies.value,
            hasMore = hasMore
        )
    }

    private suspend fun loadPageFromHtml(page: Int) {
        val service = JAViewer.SERVICE ?: run {
            _uiState.value = HomeUiState.Error("Service not initialized")
            return
        }

        val response = withContext(Dispatchers.IO) {
            when (section) {
                "popular" -> service.getPopular(page)
                "released" -> service.getReleased(page)
                else -> service.getHomePage(page)
            }
        }

        val html = withContext(Dispatchers.IO) { response.string() }
        Log.d("HomeViewModel", "HTML response length: ${html.length}, first 200: ${html.take(200)}")
        val parsed = withContext(Dispatchers.IO) { AVMOProvider.parseMovies(html) }
        Log.d("HomeViewModel", "Parsed ${parsed.size} movies")

        if (parsed.isEmpty()) {
            hasMore = false
        }
        preloadCovers(parsed)

        if (isRefreshing || page == 1) {
            _movies.value = parsed
            isRefreshing = false
        } else {
            _movies.value = _movies.value + parsed
        }

        currentPage = page
        _uiState.value = HomeUiState.Success(
            movies = _movies.value,
            hasMore = hasMore
        )
    }
}
