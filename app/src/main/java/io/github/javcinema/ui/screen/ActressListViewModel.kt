package io.github.javcinema.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.javcinema.JavCinema
import io.github.javcinema.data.model.Actress
import io.github.javcinema.network.provider.AVMOProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ActressListUiState {
    data object Loading : ActressListUiState()
    data class Success(val hasMore: Boolean = true) : ActressListUiState()
    data class Error(val message: String) : ActressListUiState()
}

class ActressListViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<ActressListUiState>(ActressListUiState.Loading)
    val uiState: StateFlow<ActressListUiState> = _uiState.asStateFlow()

    private val _actresses = MutableStateFlow<List<Actress>>(emptyList())
    val actresses: StateFlow<List<Actress>> = _actresses.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private var currentPage = 1
    private var hasMore = true

    init {
        loadActresses()
        viewModelScope.launch {
            JavCinema.dataSourceVersionFlow.drop(1).collectLatest { loadActresses() }
        }
    }

    fun loadActresses() {
        viewModelScope.launch {
            _uiState.value = ActressListUiState.Loading
            currentPage = 1
            hasMore = true
            loadPage(1)
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value || !hasMore) return
        _isLoadingMore.value = true
        viewModelScope.launch {
            loadPage(currentPage + 1)
            _isLoadingMore.value = false
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
            _uiState.value = ActressListUiState.Error(e.message ?: "加载失败")
        }
    }

    private suspend fun loadPageFromApi(page: Int) {
        val api = JavCinema.AVMOO_API_SERVICE ?: run {
            _uiState.value = ActressListUiState.Error("API service not initialized")
            return
        }

        val response = withContext(Dispatchers.IO) {
            api.getStars(listOf("stars", 60, page))
        }

        // ⚠️ 接口永远回 HTTP 200，失败信号在 `code` 里（实测 404 + data:null）——
        // 不看 code 就会把接口报错渲染成女优列表「暂无数据」。
        requireAvmooSuccess(response.code)
        val apiStars = response.data ?: emptyList()
        if (apiStars.isNotEmpty()) {
            val s = apiStars[0]
            android.util.Log.d("ActressListVM", "first star: ja=${s.starName_ja} en=${s.starName_en} cn=${s.starName_cn} tw=${s.starName_tw} id=${s.starId}")
        }
        val parsed = apiStars.map { star ->
            val name = star.starName ?: star.starName_ja ?: star.starName_en ?: star.starName_cn ?: star.starName_tw ?: ""
            val actress = Actress.create(
                name,
                star.avatarUrl ?: star.avatar ?: "",
                star.starId ?: ""
            )
            actress.movieCount = star.movieCount
            actress
        }

        if (parsed.isEmpty()) {
            hasMore = false
        }

        _actresses.value = if (page == 1) parsed else _actresses.value + parsed
        currentPage = page
        _uiState.value = ActressListUiState.Success(hasMore = hasMore)
    }

    private suspend fun loadPageFromHtml(page: Int) {
        val service = JavCinema.SERVICE ?: run {
            _uiState.value = ActressListUiState.Error("Service not initialized")
            return
        }

        val response = withContext(Dispatchers.IO) { service.getActresses(page) }
        val html = withContext(Dispatchers.IO) { response.string() }
        val parsed = withContext(Dispatchers.IO) { AVMOProvider.parseActresses(html) }

        if (parsed.isEmpty()) {
            hasMore = false
        }

        _actresses.value = if (page == 1) parsed else _actresses.value + parsed
        currentPage = page
        _uiState.value = ActressListUiState.Success(hasMore = hasMore)
    }
}
