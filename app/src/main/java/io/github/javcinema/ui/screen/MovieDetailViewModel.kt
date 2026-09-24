package io.github.javcinema.ui.screen

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.javcinema.JavCinema
import io.github.javcinema.data.model.Movie
import io.github.javcinema.data.model.MovieDetail
import io.github.javcinema.data.model.toggleStar
import io.github.javcinema.network.provider.AVMOProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class MovieDetailUiState {
    data object Loading : MovieDetailUiState()
    data class Success(val detail: MovieDetail) : MovieDetailUiState()
    data class Error(val message: String) : MovieDetailUiState()
}

internal fun shouldSkipDetailLoad(
    movieCode: String,
    currentMovieCode: String,
    version: Int,
    lastVersion: Int,
    isError: Boolean
): Boolean = currentMovieCode == movieCode && lastVersion == version && !isError

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
    private var currentMovieCode: String = ""
    private var currentMovieLink: String? = null
    private var lastVersion: Int = -1
    private var detailJob: Job? = null

    private val _defaultCover = MutableStateFlow<String?>(null)
    val defaultCover: StateFlow<String?> = _defaultCover.asStateFlow()

    init {
        viewModelScope.launch {
            JavCinema.dataSourceVersionFlow.drop(1).collectLatest {
                if (currentMovieCode.isNotEmpty()) {
                    loadDetail(currentMovieCode, currentMovieLink)
                }
            }
        }
    }

    fun loadDetail(movieCode: String, movieLink: String? = null, thumbnailUrl: String? = null) {
        val version = JavCinema.dataSourceVersionFlow.value
        if (shouldSkipDetailLoad(
                movieCode,
                currentMovieCode,
                version,
                lastVersion,
                _uiState.value is MovieDetailUiState.Error
            )
        ) {
            Log.i("MovieDetailVM", "loadDetail: skipped (already loading/success) code=$movieCode")
            return
        }
        currentMovieCode = movieCode
        currentMovieLink = movieLink
        lastVersion = version
        _defaultCover.value = thumbnailUrl
        Log.i("MovieDetailVM", "loadDetail: code=$movieCode link=$movieLink thumb=$thumbnailUrl")
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _uiState.value = MovieDetailUiState.Loading
            try {
                val ds = JavCinema.getDataSource()
                // ⚠️ 按 apiPath 判断，**不按数据源名字** —— 名字只是展示文案，改名会静默落到
                // 已失效的 HTML 抓取器 → 全站「暂无数据」。见 isAvmooApiSource 的 KDoc。
                if (isAvmooApiSource(ds.apiPath)) {
                    loadDetailFromApi(movieCode, movieLink)
                } else {
                    loadDetailFromHtml(movieCode, movieLink)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.value = MovieDetailUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun loadDetailFromApi(movieCode: String, movieLink: String?) {
        val api = JavCinema.AVMOO_API_SERVICE ?: run {
            _uiState.value = MovieDetailUiState.Error("API service not initialized")
            return
        }

        val movieId = movieLink ?: movieCode

        val (response, relatedResponse) = coroutineScope {
            val detailDeferred = async {
                withContext(Dispatchers.IO) { api.getMovie(listOf(movieId, "cn")) }
            }
            val relatedDeferred = async {
                runCatching {
                    withContext(Dispatchers.IO) { api.getRelatedMovies(listOf(movieId, "cn", 12)) }
                }.getOrNull()
            }
            detailDeferred.await() to relatedDeferred.await()
        }

        val apiDetail = response.data ?: run {
            _uiState.value = MovieDetailUiState.Error("Movie not found")
            return
        }

        val parsed = withContext(Dispatchers.IO) { AVMOProvider.fromApiDetail(apiDetail) }

        Log.i("MovieDetailVM", "API cover: small=${apiDetail.posterSmall} large=${apiDetail.posterLarge} final=${parsed.coverUrl}")

        movie = Movie().apply {
            id = apiDetail.movieId
            code = apiDetail.movieFanHao
            title = parsed.title
            coverUrl = parsed.coverUrl
            link = apiDetail.movieId
            dataSourceName = JavCinema.getDataSource()?.name
        }

        _detail.value = parsed
        if (parsed.coverUrl != null) {
            Log.i("MovieDetailVM", "upgrading cover to: ${parsed.coverUrl}")
            _defaultCover.value = parsed.coverUrl
        }
        checkStarred()
        _uiState.value = MovieDetailUiState.Success(parsed)

        relatedResponse?.data?.let { data ->
            val related = AVMOProvider.fromApiList(data)
            if (related.isNotEmpty()) {
                _relatedMovies.value = related
            }
        }
    }

    private suspend fun loadDetailFromHtml(movieCode: String, movieLink: String?) {
        val link = movieLink ?: "/cn/${movieCode.replace("-", "/")}"
        val service = JavCinema.SERVICE ?: run {
            _uiState.value = MovieDetailUiState.Error("Service not initialized")
            return
        }

        val response = withContext(Dispatchers.IO) { service.get(link) }
        val html = withContext(Dispatchers.IO) { response.string() }
        val parsed = withContext(Dispatchers.IO) { AVMOProvider.parseMoviesDetail(html) }

        Log.i("MovieDetailVM", "HTML cover: ${parsed.coverUrl}")

        movie = Movie().apply {
            code = movieCode
            title = parsed.title
            coverUrl = parsed.coverUrl
            this.link = movieLink
            dataSourceName = JavCinema.getDataSource()?.name
        }

        _detail.value = parsed
        if (parsed.coverUrl != null) {
            Log.i("MovieDetailVM", "upgrading cover to: ${parsed.coverUrl}")
            _defaultCover.value = parsed.coverUrl
        }
        checkStarred()
        _uiState.value = MovieDetailUiState.Success(parsed)
    }

    fun toggleStar() {
        val m = movie ?: return
        m.toggleStar()
        JavCinema.CONFIGURATIONS?.save()
        checkStarred()
    }

    private fun checkStarred() {
        val m = movie
        _isStarred.value = JavCinema.CONFIGURATIONS?.starredMovies?.contains(m) == true
    }

    override fun onCleared() {
        super.onCleared()
        currentMovieLink?.let { JavCinema.imageUrlsRegistry.remove(it) }
    }
}
