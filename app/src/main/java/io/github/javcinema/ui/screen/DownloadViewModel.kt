package io.github.javcinema.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.javcinema.data.model.DownloadLink
import io.github.javcinema.data.model.MagnetFile
import io.github.javcinema.network.provider.BTSOLinkProvider
import io.github.javcinema.network.provider.BtSearchLinkProvider
import io.github.javcinema.network.provider.CiliInfoLinkProvider
import io.github.javcinema.network.provider.DownloadLinkProvider
import org.jsoup.Jsoup
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

    private val _searchCount = java.util.concurrent.atomic.AtomicInteger(0)
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _magnetLink = MutableStateFlow<String?>(null)
    val magnetLink: StateFlow<String?> = _magnetLink.asStateFlow()

    private val _isGettingMagnet = MutableStateFlow(false)
    val isGettingMagnet: StateFlow<Boolean> = _isGettingMagnet.asStateFlow()

    fun search(keyword: String, providerName: String) {
        if (keyword.isBlank()) return
        viewModelScope.launch {
            _searchCount.incrementAndGet()
            _isSearching.value = true
            try {
                when (providerName.lowercase()) {
                    "btsearch" -> {
                        val results = withContext(Dispatchers.IO) { btSearchProvider.searchApi(keyword, 1) }
                        withContext(Dispatchers.IO) {
                            results.forEach { link ->
                                try {
                                    val id = link.link ?: return@forEach
                                    val kw = link.title ?: return@forEach
                                    val detail = btSearchProvider.getDetail(id, kw)
                                    val torrentFiles = detail?.torrentfile
                                    if (torrentFiles != null) {
                                        link.files = btSearchProvider.parseFilesFromTorrentFiles(torrentFiles)
                                    }
                                } catch (_: Exception) { }
                            }
                        }
                        _btSearchResults.value = results
                    }
                    "btso" -> {
                        val results = withContext(Dispatchers.IO) { btsoProvider.searchApi(keyword, 1) }
                        withContext(Dispatchers.IO) {
                            results.forEach { link ->
                                val hash = link.link ?: return@forEach
                                val files = btsoProvider.getMagnetDetail(hash)
                                if (files.isNotEmpty()) {
                                    link.files = files
                                }
                            }
                        }
                        _btsoResults.value = results
                    }
                    "ciliinfo", "cili" -> {
                        val provider = getProvider(providerName)
                        val response = withContext(Dispatchers.IO) { provider.search(keyword, 1) }
                        val html = withContext(Dispatchers.IO) { response?.string() ?: "" }
                        val results = withContext(Dispatchers.IO) { provider.parseDownloadLinks(html) }
                        withContext(Dispatchers.IO) {
                            results.forEach { link ->
                                try {
                                    val detailUrl = link.link ?: return@forEach
                                    val detailResponse = ciliProvider.get(detailUrl)
                                    val detailHtml = detailResponse?.string() ?: return@forEach
                                    val magnet = ciliProvider.parseMagnetLink(detailHtml)
                                    link.magnetLink = magnet
                                    val doc = Jsoup.parse(detailHtml)
                                    val dateEl = doc.select("dt:contains(发布日期)").first()?.nextElementSibling()
                                    val date = dateEl?.text()?.trim() ?: ""
                                    if (date.isNotEmpty()) link.date = date
                                    val files = ciliProvider.parseFiles(detailHtml)
                                    link.files = files.ifEmpty {
                                        listOf(MagnetFile().apply {
                                            filename = link.title ?: magnet?.magnetLink ?: ""
                                        })
                                    }
                                } catch (_: Exception) { }
                            }
                        }
                        _ciliResults.value = results
                    }
                }
            } catch (_: Exception) {
            } finally {
                if (_searchCount.decrementAndGet() <= 0) {
                    _isSearching.value = false
                }
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
                val torrentFiles = detail?.torrentfile
                if (torrentFiles != null) {
                    val files = btSearchProvider.parseFilesFromTorrentFiles(torrentFiles)
                    link.files = files
                }
            } catch (_: Exception) {
            }
        }
    }

    fun loadBTSODetail(link: DownloadLink) {
        viewModelScope.launch {
            val hash = link.link ?: return@launch
            try {
                val files = withContext(Dispatchers.IO) { btsoProvider.getMagnetDetail(hash) }
                if (files.isNotEmpty()) {
                    link.files = files
                }
            } catch (_: Exception) {
            }
        }
    }

    fun getMagnetLinkInline(link: DownloadLink) {
        viewModelScope.launch {
            if (link.files != null) return@launch
            _isGettingMagnet.value = true
            try {
                val detailUrl = link.link ?: return@launch
                val response = withContext(Dispatchers.IO) { ciliProvider.get(detailUrl) }
                val html = withContext(Dispatchers.IO) { response?.string() ?: "" }
                val magnet = withContext(Dispatchers.IO) { ciliProvider.parseMagnetLink(html) }
                link.magnetLink = magnet
                val files = withContext(Dispatchers.IO) { ciliProvider.parseFiles(html) }
                link.files = files.ifEmpty {
                    listOf(io.github.javcinema.data.model.MagnetFile().apply {
                        filename = link.title ?: magnet?.magnetLink ?: ""
                    })
                }
            } catch (_: Exception) {
            } finally {
                _isGettingMagnet.value = false
            }
        }
    }

    fun dismissMagnet() {
        _magnetLink.value = null
    }

    fun startSearch() {
        _isSearching.value = true
    }

    fun resetSearch() {
        _searchCount.set(0)
        _isSearching.value = false
        _btSearchResults.value = emptyList()
        _ciliResults.value = emptyList()
        _btsoResults.value = emptyList()
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
