package io.github.javcinema.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.javcinema.JavCinema
import io.github.javcinema.data.model.Actress
import io.github.javcinema.data.model.AvmooStar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ActressProfile(
    val actress: Actress,
    val birthday: String? = null,
    val size: String? = null
)

sealed class ActressDetailUiState {
    data object Loading : ActressDetailUiState()
    data class Success(val profile: ActressProfile) : ActressDetailUiState()
    data class Error(val message: String) : ActressDetailUiState()
}

internal fun starDisplayName(star: AvmooStar): String {
    return star.starName
        ?: star.starName_ja
        ?: star.starName_en
        ?: star.starName_cn
        ?: star.starName_tw
        ?: ""
}

internal fun starSizeText(size: com.google.gson.JsonElement?): String? {
    if (size == null || size.isJsonNull) return null
    if (size.isJsonPrimitive) return size.asString.takeIf { it.isNotBlank() }
    return size.toString().takeIf { it.isNotBlank() && it != "null" }
}

class ActressDetailViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<ActressDetailUiState>(ActressDetailUiState.Loading)
    val uiState: StateFlow<ActressDetailUiState> = _uiState.asStateFlow()

    private var currentStarId: String = ""
    private var fallbackName: String = ""
    private var fallbackImage: String? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            JavCinema.dataSourceVersionFlow.drop(1).collectLatest {
                if (currentStarId.isNotEmpty()) {
                    load(currentStarId, fallbackName, fallbackImage)
                }
            }
        }
    }

    fun load(starId: String, name: String = "", imageUrl: String? = null) {
        if (starId.isBlank()) {
            _uiState.value = ActressDetailUiState.Error("缺少女优 id")
            return
        }
        currentStarId = starId
        fallbackName = name
        fallbackImage = imageUrl
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = ActressDetailUiState.Loading
            val fallback = Actress().apply {
                this.name = name.ifBlank { starId }
                this.imageUrl = imageUrl
                this.link = starId
                dataSourceName = JavCinema.getDataSource()?.name
            }
            try {
                val api = JavCinema.AVMOO_API_SERVICE
                if (api == null) {
                    _uiState.value = ActressDetailUiState.Success(ActressProfile(fallback))
                    return@launch
                }
                val response = withContext(Dispatchers.IO) {
                    api.getStar(listOf(starId, "cn"))
                }
                val star = response.data
                if (star == null) {
                    _uiState.value = ActressDetailUiState.Success(ActressProfile(fallback))
                    return@launch
                }
                val actress = Actress.create(
                    starDisplayName(star).ifBlank { name.ifBlank { starId } },
                    star.avatarUrl ?: star.avatar ?: imageUrl.orEmpty(),
                    star.starId ?: starId
                )
                actress.movieCount = star.movieCount
                _uiState.value = ActressDetailUiState.Success(
                    ActressProfile(
                        actress = actress,
                        birthday = star.birthday,
                        size = starSizeText(star.size)
                    )
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = ActressDetailUiState.Success(ActressProfile(fallback))
            }
        }
    }
}
