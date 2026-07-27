package io.github.javcinema.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.javcinema.data.model.DownloadLink
import io.github.javcinema.network.provider.BTSOLinkProvider
import io.github.javcinema.network.provider.BtSearchLinkProvider
import io.github.javcinema.network.provider.CiliInfoLinkProvider
import io.github.javcinema.network.provider.DownloadLinkProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadViewModel : ViewModel() {
    private val btsoProvider = BTSOLinkProvider()
    private val ciliProvider = CiliInfoLinkProvider()
    private val btSearchProvider = BtSearchLinkProvider()

    private val _btsoResults = MutableStateFlow<List<DownloadLink>>(emptyList())
    val btsoResults: StateFlow<List<DownloadLink>> = _btsoResults.asStateFlow()

    private val _ciliResults = MutableStateFlow<List<DownloadLink>>(emptyList())
    val ciliResults: StateFlow<List<DownloadLink>> = _ciliResults.asStateFlow()

    private val _btSearchResults = MutableStateFlow<List<DownloadLink>>(emptyList())
    val btSearchResults: StateFlow<List<DownloadLink>> = _btSearchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _magnetLink = MutableStateFlow<String?>(null)
    val magnetLink: StateFlow<String?> = _magnetLink.asStateFlow()

    private val _isGettingMagnet = MutableStateFlow(false)
    val isGettingMagnet: StateFlow<Boolean> = _isGettingMagnet.asStateFlow()

    fun search(keyword: String, providerName: String) {
        if (keyword.isBlank()) return
        viewModelScope.launch {
            _isSearching.value = true
            try {
                if (providerName.lowercase() == "btsearch") {
                    val results = withContext(Dispatchers.IO) { btSearchProvider.searchApi(keyword, 1) }
                    _btSearchResults.value = results
                } else {
                    val provider = getProvider(providerName)
                    val response = withContext(Dispatchers.IO) { provider.search(keyword, 1) }
                    val html = withContext(Dispatchers.IO) { response?.string() ?: "" }
                    val results = withContext(Dispatchers.IO) { provider.parseDownloadLinks(html) }

                    when (providerName.lowercase()) {
                        "btso" -> _btsoResults.value = results
                        "ciliinfo", "cili" -> _ciliResults.value = results
                    }
                }
            } catch (_: Exception) {
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun getMagnetLink(link: DownloadLink, providerName: String) {
        if (link.hasMagnetLink()) {
            _magnetLink.value = link.magnetLink?.magnetLink
            return
        }

        viewModelScope.launch {
            _isGettingMagnet.value = true
            try {
                val provider = getProvider(providerName)
                val detailUrl = link.link ?: return@launch
                val response = withContext(Dispatchers.IO) { provider.get(detailUrl) }
                val html = withContext(Dispatchers.IO) { response?.string() ?: "" }
                val magnet = withContext(Dispatchers.IO) { provider.parseMagnetLink(html) }
                _magnetLink.value = magnet?.magnetLink
            } catch (_: Exception) {
                _magnetLink.value = null
            } finally {
                _isGettingMagnet.value = false
            }
        }
    }

    fun loadBtSearchDetail(link: DownloadLink) {
        viewModelScope.launch {
            val id = link.link ?: return@launch
            val keyword = link.title ?: return@launch
            try {
                val detail = withContext(Dispatchers.IO) { btSearchProvider.getDetail(id, keyword) }
                val torrentFiles = detail?.data?.torrentfile
                if (torrentFiles != null) {
                    val files = btSearchProvider.parseFilesFromTorrentFiles(torrentFiles)
                    link.files = files
                }
            } catch (_: Exception) {
            }
        }
    }

    fun dismissMagnet() {
        _magnetLink.value = null
    }

    private fun getProvider(name: String): DownloadLinkProvider {
        return when (name.lowercase()) {
            "btso" -> btsoProvider
            "ciliinfo", "cili" -> ciliProvider
            "btsearch" -> btSearchProvider
            else -> btsoProvider
        }
    }
}
