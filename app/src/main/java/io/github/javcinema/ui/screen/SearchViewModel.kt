package io.github.javcinema.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.javcinema.JavCinema
import io.github.javcinema.data.model.Actress
import io.github.javcinema.data.model.Movie
import io.github.javcinema.data.model.matchesFavoriteQuery
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

enum class SearchScope { MOVIES, ACTRESSES, FAVORITES }

sealed class SearchUiState {
    data object Idle : SearchUiState()
    data object Loading : SearchUiState()
    data class Success(val hasMore: Boolean = true) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}

internal fun actressMatchesQuery(name: String?, query: String): Boolean {
    if (query.isBlank()) return true
    return name.orEmpty().contains(query.trim(), ignoreCase = true)
}

class SearchViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _movies = MutableStateFlow<List<Movie>>(emptyList())
    val movies: StateFlow<List<Movie>> = _movies.asStateFlow()

    private val _actresses = MutableStateFlow<List<Actress>>(emptyList())
    val actresses: StateFlow<List<Actress>> = _actresses.asStateFlow()

    private val _scope = MutableStateFlow(SearchScope.MOVIES)
    val scope: StateFlow<SearchScope> = _scope.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private var currentPage = 1
    private var hasMore = true
    private var currentQuery = ""
    private var loadJob: Job? = null
    private var moreJob: Job? = null
    private var lastVersion: Int = -1

    init {
        viewModelScope.launch {
            JavCinema.dataSourceVersionFlow.drop(1).collectLatest { version ->
                lastVersion = version
                if (currentQuery.isNotEmpty() || _scope.value == SearchScope.FAVORITES) {
                    search(currentQuery, _scope.value, force = true)
                }
            }
        }
        viewModelScope.launch {
            JavCinema.favoritesVersionFlow.drop(1).collectLatest {
                if (_scope.value == SearchScope.FAVORITES) {
                    search(currentQuery, SearchScope.FAVORITES, force = true)
                }
            }
        }
    }

    fun reset() {
        loadJob?.cancel()
        moreJob?.cancel()
        currentQuery = ""
        currentPage = 1
        hasMore = true
        _movies.value = emptyList()
        _actresses.value = emptyList()
        _isLoadingMore.value = false
        _uiState.value = SearchUiState.Idle
    }

    fun setScope(scope: SearchScope) {
        if (_scope.value == scope) return
        _scope.value = scope
        when {
            scope == SearchScope.FAVORITES || currentQuery.isNotBlank() -> {
                search(currentQuery, scope, force = true)
            }
            else -> {
                loadJob?.cancel()
                moreJob?.cancel()
                currentPage = 1
                hasMore = true
                _movies.value = emptyList()
                _actresses.value = emptyList()
                _isLoadingMore.value = false
                _uiState.value = SearchUiState.Idle
            }
        }
    }

    fun search(query: String, scope: SearchScope = _scope.value, force: Boolean = false) {
        val currentVersion = JavCinema.dataSourceVersionFlow.value
        if (!force && query == currentQuery && lastVersion == currentVersion && scope == _scope.value) return
        lastVersion = currentVersion
        if (query.isBlank() && scope != SearchScope.FAVORITES) return
        currentQuery = query
        _scope.value = scope
        currentPage = 1
        hasMore = true
        moreJob?.cancel()
        _isLoadingMore.value = false
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = SearchUiState.Loading
            when (scope) {
                SearchScope.ACTRESSES -> {
                    _movies.value = emptyList()
                    loadActressPage(1)
                }
                SearchScope.FAVORITES -> loadFavorites(query)
                SearchScope.MOVIES -> {
                    _actresses.value = emptyList()
                    loadPage(1)
                }
            }
        }
    }

    fun loadMore() {
        if (!shouldStartLoadMore(_isLoadingMore.value, hasMore, loadJob?.isActive == true)) return
        _isLoadingMore.value = true
        moreJob?.cancel()
        moreJob = viewModelScope.launch {
            try {
                when (_scope.value) {
                    SearchScope.ACTRESSES -> loadActressPage(currentPage + 1)
                    SearchScope.MOVIES -> loadPage(currentPage + 1)
                    SearchScope.FAVORITES -> Unit
                }
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    private fun loadFavorites(query: String) {
        val movies = JavCinema.CONFIGURATIONS?.starredMovies.orEmpty()
            .filter { matchesFavoriteQuery(it.title, it.code, query) }
        val actresses = JavCinema.CONFIGURATIONS?.starredActresses.orEmpty()
            .filter { matchesFavoriteQuery(it.name, null, query) }
        _movies.value = movies
        _actresses.value = actresses
        hasMore = false
        _uiState.value = SearchUiState.Success(hasMore = false)
    }

    private suspend fun loadPage(page: Int) {
        try {
            val ds = JavCinema.getDataSource()
            // ⚠️ 按 apiPath 判断，**不按数据源名字** —— 名字只是展示文案，改名会静默落到
            // 已失效的 HTML 抓取器 → 全站「暂无数据」。见 isAvmooApiSource 的 KDoc。
            val parsed: List<Movie> = if (isAvmooApiSource(ds.apiPath) && JavCinema.AVMOO_API_SERVICE != null) {
                loadPageFromApi(page)
            } else {
                loadPageFromHtml(page)
            }

            if (parsed.isEmpty()) {
                hasMore = false
            }

            _movies.value = if (page == 1) parsed else _movies.value + parsed
            currentPage = page
            _uiState.value = SearchUiState.Success(hasMore = hasMore)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e("SearchVM", "loadPage error: ${e.message}", e)
            _uiState.value = SearchUiState.Error(e.message ?: "搜索失败")
        }
    }

    private suspend fun loadPageFromApi(page: Int): List<Movie> {
        val api = JavCinema.AVMOO_API_SERVICE ?: return emptyList()
        val body = listOf<Any>(mapOf("search" to currentQuery, "lang" to "cn"), 60, page)
        val response = withContext(Dispatchers.IO) { api.search(body) }
        // ⚠️ 接口永远回 HTTP 200，失败信号在 `code` 里（实测 404 + data:null）——
        // 不看 code 就会把接口报错渲染成「未找到结果」。
        requireAvmooSuccess(response.code)
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

    private suspend fun loadActressPage(page: Int) {
        try {
            var scanPage = page
            val collected = mutableListOf<Actress>()
            var sourceEmpty = false
            while (collected.size < 20 && scanPage <= page + 4) {
                val parsed = loadActressesFromApi(scanPage).ifEmpty { loadActressesFromHtml(scanPage) }
                if (parsed.isEmpty()) {
                    sourceEmpty = true
                    break
                }
                collected += parsed.filter { actressMatchesQuery(it.name, currentQuery) }
                scanPage++
            }
            if (sourceEmpty) {
                hasMore = false
            }
            _actresses.value = if (page == 1) collected else _actresses.value + collected
            currentPage = scanPage - 1
            if (currentPage < page) currentPage = page
            _uiState.value = SearchUiState.Success(hasMore = hasMore)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _uiState.value = SearchUiState.Error(e.message ?: "搜索失败")
        }
    }

    private suspend fun loadActressesFromApi(page: Int): List<Actress> {
        val api = JavCinema.AVMOO_API_SERVICE ?: return emptyList()
        val response = withContext(Dispatchers.IO) {
            api.getStars(listOf("stars", 60, page))
        }
        // ⚠️ 接口永远回 HTTP 200，失败信号在 `code` 里（实测 404 + data:null）。
        requireAvmooSuccess(response.code)
        return (response.data ?: emptyList()).map { star ->
            val name = star.starName ?: star.starName_ja ?: star.starName_en ?: star.starName_cn ?: star.starName_tw ?: ""
            Actress.create(name, star.avatarUrl ?: star.avatar ?: "", star.starId ?: "").apply {
                movieCount = star.movieCount
            }
        }
    }

    private suspend fun loadActressesFromHtml(page: Int): List<Actress> {
        val service = JavCinema.SERVICE ?: return emptyList()
        val response = withContext(Dispatchers.IO) { service.getActresses(page) }
        val html = withContext(Dispatchers.IO) { response.string() }
        return withContext(Dispatchers.IO) { AVMOProvider.parseActresses(html) }
    }
}
