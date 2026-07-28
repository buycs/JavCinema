package io.github.javcinema.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.javcinema.JAViewer
import io.github.javcinema.data.model.Movie
import io.github.javcinema.network.BasicService
import io.github.javcinema.network.provider.AVMOProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

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

    fun search(query: String) {
        val currentVersion = JAViewer.dataSourceVersion
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
            val service = JAViewer.SERVICE ?: run {
                _uiState.value = SearchUiState.Error("Service not initialized")
                return
            }

            val encodedQuery = withContext(Dispatchers.IO) {
                URLEncoder.encode(currentQuery, "UTF-8")
            }
            val response = withContext(Dispatchers.IO) {
                service.get("${BasicService.LANGUAGE_NODE}/search/$encodedQuery/page/$page")
            }
            val html = withContext(Dispatchers.IO) { response.string() }
            val parsed = withContext(Dispatchers.IO) { AVMOProvider.parseMovies(html) }

            if (parsed.isEmpty()) {
                hasMore = false
            }

            _movies.value = if (page == 1) parsed else _movies.value + parsed
            currentPage = page
            _uiState.value = SearchUiState.Success(hasMore = hasMore)
        } catch (e: Exception) {
            _uiState.value = SearchUiState.Error(e.message ?: "搜索失败")
        }
    }
}
