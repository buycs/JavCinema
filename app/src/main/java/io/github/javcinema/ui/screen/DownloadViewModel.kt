package io.github.javcinema.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.javcinema.data.model.DownloadLink
import io.github.javcinema.data.model.MagnetFile
import io.github.javcinema.data.model.MagnetLink
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
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class DownloadViewModel : ViewModel() {
    private val btsoProvider = BTSOLinkProvider()
    private val ciliProvider = CiliInfoLinkProvider()
    private val btSearchProvider = BtSearchLinkProvider()
    // ⚠️ 不能用 ConcurrentHashMap.newKeySet()：它需要 API 24，而本应用 minSdk 21，
    // 在 API 21~23 的设备上会抛 NoSuchMethodError（Lint: NewApi）。
    // Collections.newSetFromMap 自 API 9 起可用，语义完全一致 ——
    // 一个由 ConcurrentHashMap 支撑的并发 Set。
    private val loadingFiles: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    private val _btsoState = MutableStateFlow<MagnetSourceUi>(MagnetSourceUi.Idle)
    val btsoState: StateFlow<MagnetSourceUi> = _btsoState.asStateFlow()

    private val _ciliState = MutableStateFlow<MagnetSourceUi>(MagnetSourceUi.Idle)
    val ciliState: StateFlow<MagnetSourceUi> = _ciliState.asStateFlow()

    private val _btSearchState = MutableStateFlow<MagnetSourceUi>(MagnetSourceUi.Idle)
    val btSearchState: StateFlow<MagnetSourceUi> = _btSearchState.asStateFlow()

    private val _magnetLink = MutableStateFlow<String?>(null)
    val magnetLink: StateFlow<String?> = _magnetLink.asStateFlow()

    private val _isGettingMagnet = MutableStateFlow(false)
    val isGettingMagnet: StateFlow<Boolean> = _isGettingMagnet.asStateFlow()

    fun search(keyword: String, providerName: String) {
        if (keyword.isBlank()) return
        val state = sourceState(providerName)
        viewModelScope.launch {
            state.value = MagnetSourceUi.Loading
            try {
                val results = withContext(Dispatchers.IO) {
                    when (providerName.lowercase()) {
                        "btsearch" -> btSearchProvider.searchApi(keyword, 1)
                        "btso" -> btsoProvider.searchApi(keyword, 1)
                        "ciliinfo", "cili" -> {
                            val response = ciliProvider.search(keyword, 1)
                            val html = response.string()
                            ciliProvider.parseDownloadLinks(html)
                        }
                        else -> emptyList()
                    }
                }
                state.value = toMagnetSourceUi(true, results, null)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                state.value = toMagnetSourceUi(false, emptyList(), e.message)
            }
        }
    }

    fun loadFiles(link: DownloadLink, providerName: String) {
        val key = itemKey(link)
        if (!shouldLoadFiles(link.files, loadingFiles.contains(key))) return
        loadingFiles.add(key)
        viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    when (providerName.lowercase()) {
                        "btsearch" -> {
                            val id = link.link ?: throw IllegalStateException("缺少资源 id")
                            val keyword = link.title ?: throw IllegalStateException("缺少标题")
                            val detail = btSearchProvider.getDetail(id, keyword)
                            val torrentFiles = detail?.torrentfile
                                ?: throw IllegalStateException("未获取到文件列表")
                            LoadedFiles(btSearchProvider.parseFilesFromTorrentFiles(torrentFiles))
                        }
                        "btso" -> {
                            val hash = link.link ?: throw IllegalStateException("缺少 hash")
                            val files = btsoProvider.getMagnetDetail(hash)
                            LoadedFiles(files.ifEmpty { throw IllegalStateException("未获取到文件列表") })
                        }
                        "ciliinfo", "cili" -> {
                            val detailUrl = link.link ?: throw IllegalStateException("缺少详情地址")
                            val detailResponse = ciliProvider.get(detailUrl)
                            val detailHtml = detailResponse.string()
                            val magnet = ciliProvider.parseMagnetLink(detailHtml)
                            // 不再从详情页取「发布日期」回填：详情页那份带时分秒，
                            // 而且和列表页的日期会差一天；另外两个源（BTSO / BTSEARCH）
                            // 展开时都只补文件列表和磁力链接、不动日期。保持三者行为一致。
                            LoadedFiles(
                                files = ciliProvider.parseFiles(detailHtml).ifEmpty {
                                    listOf(MagnetFile().apply {
                                        filename = link.title ?: magnet.magnetLink ?: ""
                                    })
                                },
                                magnetLink = magnet
                            )
                        }
                        else -> throw IllegalStateException("未知磁力源")
                    }
                }
                replaceItem(providerName, key) { item ->
                    item.copy(
                        files = loaded.files,
                        filesError = null,
                        magnetLink = loaded.magnetLink ?: item.magnetLink
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                replaceItem(providerName, key) { item ->
                    item.copy(filesError = e.message?.takeIf { it.isNotBlank() } ?: "加载失败")
                }
            } finally {
                loadingFiles.remove(key)
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
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _magnetLink.value = null
            } finally {
                _isGettingMagnet.value = false
            }
        }
    }

    fun dismissMagnet() {
        _magnetLink.value = null
    }

    fun resetSearch() {
        _btSearchState.value = MagnetSourceUi.Idle
        _ciliState.value = MagnetSourceUi.Idle
        _btsoState.value = MagnetSourceUi.Idle
    }

    private fun sourceState(providerName: String): MutableStateFlow<MagnetSourceUi> {
        return when (providerName.lowercase()) {
            "btsearch" -> _btSearchState
            "ciliinfo", "cili" -> _ciliState
            else -> _btsoState
        }
    }

    private fun itemKey(link: DownloadLink): String =
        "${link.link.orEmpty()}|${link.title.orEmpty()}"

    private fun replaceItem(
        providerName: String,
        key: String,
        transform: (DownloadLink) -> DownloadLink
    ) {
        val state = sourceState(providerName)
        val current = state.value as? MagnetSourceUi.Success ?: return
        state.value = MagnetSourceUi.Success(
            current.items.map { item ->
                if (itemKey(item) == key) transform(item) else item
            }
        )
    }

    private data class LoadedFiles(
        val files: List<MagnetFile>,
        val magnetLink: MagnetLink? = null
    )

    private fun getProvider(name: String): DownloadLinkProvider {
        return when (name.lowercase()) {
            "btso" -> btsoProvider
            "ciliinfo", "cili" -> ciliProvider
            "btsearch" -> btSearchProvider
            else -> btsoProvider
        }
    }
}
