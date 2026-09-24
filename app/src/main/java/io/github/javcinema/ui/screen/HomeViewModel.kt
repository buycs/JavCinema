package io.github.javcinema.ui.screen

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.javcinema.JavCinema
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
    private var moreJob: kotlinx.coroutines.Job? = null
    private var lastVersion: Int = -1

    init {
        viewModelScope.launch {
            JavCinema.dataSourceVersionFlow.drop(1).collectLatest { version ->
                lastVersion = version
                if (section.isNotEmpty()) refresh()
            }
        }
    }

    fun setSection(section: String) {
        val currentVersion = JavCinema.dataSourceVersionFlow.value
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
        moreJob?.cancel()
        _isLoadingMore.value = false
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
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

    private suspend fun loadPage(page: Int) {
        try {
            val ds = JavCinema.getDataSource()
            // ⚠️ 按 apiPath 判断，**不按数据源名字** —— 名字只是展示文案，改名会静默落到
            // 已失效的 HTML 抓取器 → 全站「暂无数据」。见 isAvmooApiSource 的 KDoc。
            if (isAvmooApiSource(ds.apiPath)) {
                loadPageFromApi(page)
            } else {
                loadPageFromHtml(page)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e("HomeViewModel", "loadPage error: ${e.message}", e)
            _uiState.value = HomeUiState.Error(e.message ?: "Unknown error")
            isRefreshing = false
        }
    }

    private suspend fun loadPageFromApi(page: Int) {
        val api = JavCinema.AVMOO_API_SERVICE ?: run {
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

        // ⚠️ 这套接口**永远回 HTTP 200**，失败信号只在 JSON 的 `code` 里（实测 404 + data:null）。
        // 不看 code 就会把接口报错渲染成首页「暂无数据」—— 失败被说成「站点没有」。
        requireAvmooSuccess(response.code)
        val apiMovies = response.data ?: emptyList()
        Log.d("HomeViewModel", "API returned ${apiMovies.size} movies")

        val parsed = withContext(Dispatchers.IO) { AVMOProvider.fromApiList(apiMovies) }

        if (apiMovies.isEmpty()) {
            hasMore = false
        }

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
        val service = JavCinema.SERVICE ?: run {
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
