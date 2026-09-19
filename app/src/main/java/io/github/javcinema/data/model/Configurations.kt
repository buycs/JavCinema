package io.github.javcinema.data.model

import android.content.Context
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import io.github.javcinema.JavCinema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.net.URI
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException

class Configurations {

    var starredMovies: MutableList<Movie>? = null
        get() {
            if (field == null) {
                field = java.util.ArrayList()
            }
            return field
        }
    var starredActresses: MutableList<Actress>? = null
        get() {
            if (field == null) {
                field = java.util.ArrayList()
            }
            return field
        }
    var dataSource: DataSource? = null
        get() {
            if (field == null && JavCinema.DATA_SOURCES.isNotEmpty()) {
                field = JavCinema.DATA_SOURCES[0]
            }
            return field
        }
    @kotlin.jvm.Transient
    private var configFile: File? = null
    private var showAds: Boolean = false
    var downloadCounter: Long = 0

    companion object {
        private const val PREFS_NAME = "javcinema_config"
        private const val KEY_CUSTOM_AVMOO = "custom_avmoo_url"
        private const val KEY_CUSTOM_AVSO = "custom_avso_url"
        private const val KEY_CUSTOM_AVXO = "custom_avxo_url"
        private const val KEY_CUSTOM_BTSEARCH = "custom_btsearch_url"
        private const val KEY_CUSTOM_CILI = "custom_cili_url"
        private const val KEY_CUSTOM_BTSOW = "custom_btsow_url"
        private const val KEY_HOME_PAGE = "home_page"

        var customAvmooUrl: String? = null
        var customAvsoUrl: String? = null
        var customAvxoUrl: String? = null
        var customBtSearchUrl: String? = null
        var customCiliUrl: String? = null
        var customBtsowUrl: String? = null

        var homePage: String? = null
        var themeMode: String? = null
        var gridColumns: Int = 3

        /** 是否在系统「最近任务」列表中隐藏本应用。 */
        var hideFromRecents: Boolean = false

        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_GRID_COLUMNS = "grid_columns"
        private const val KEY_HIDE_FROM_RECENTS = "hide_from_recents"
        private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val saveMutex = Mutex()

        fun loadPrefs(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            customAvmooUrl = prefs.getString(KEY_CUSTOM_AVMOO, null)
            customAvsoUrl = prefs.getString(KEY_CUSTOM_AVSO, null)
            customAvxoUrl = prefs.getString(KEY_CUSTOM_AVXO, null)
            customBtSearchUrl = prefs.getString(KEY_CUSTOM_BTSEARCH, null)
            customCiliUrl = prefs.getString(KEY_CUSTOM_CILI, null)
            customBtsowUrl = prefs.getString(KEY_CUSTOM_BTSOW, null)
            homePage = prefs.getString(KEY_HOME_PAGE, null)
            themeMode = prefs.getString(KEY_THEME_MODE, "system")
            gridColumns = prefs.getInt(KEY_GRID_COLUMNS, 3).coerceIn(2, 4)
            hideFromRecents = prefs.getBoolean(KEY_HIDE_FROM_RECENTS, false)
        }

        fun savePrefs(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_CUSTOM_AVMOO, customAvmooUrl)
                .putString(KEY_CUSTOM_AVSO, customAvsoUrl)
                .putString(KEY_CUSTOM_AVXO, customAvxoUrl)
                .putString(KEY_CUSTOM_BTSEARCH, customBtSearchUrl)
                .putString(KEY_CUSTOM_CILI, customCiliUrl)
                .putString(KEY_CUSTOM_BTSOW, customBtsowUrl)
                .putString(KEY_HOME_PAGE, homePage)
                .putString(KEY_THEME_MODE, themeMode)
                .putInt(KEY_GRID_COLUMNS, gridColumns.coerceIn(2, 4))
                .putBoolean(KEY_HIDE_FROM_RECENTS, hideFromRecents)
                .apply()
            JavCinema.uiPrefsVersionFlow.value++
        }
    }

    fun applyCustomUrls() {
        for (ds in JavCinema.DATA_SOURCES) {
            val custom = when (ds.name) {
                "骑兵" -> customAvmooUrl
                "步兵" -> customAvsoUrl
                "欧美" -> customAvxoUrl
                else -> null
            }
            if (!custom.isNullOrBlank()) {
                val link = if (custom.endsWith("/")) custom else "$custom/"
                ds.link = link
            }
        }
        val savedDs = dataSource
        if (savedDs != null && JavCinema.DATA_SOURCES.isNotEmpty()) {
            dataSource = JavCinema.DATA_SOURCES.find { it.name == savedDs.name } ?: savedDs
        }
        JavCinema.hostReplacements.clear()
        val ds = JavCinema.getDataSource()
        try {
            val host = URI(ds.link!!).host
            ds.legacies?.forEach { h ->
                JavCinema.hostReplacements[h] = host
            }
        } catch (_: Exception) {
        }
        for (ds in JavCinema.MAGNET_SOURCES) {
            val urlStr = when (ds.name) {
                "BtSearch" -> customBtSearchUrl
                "Cili" -> customCiliUrl
                "BTSOW" -> customBtsowUrl
                else -> null
            }
            if (!urlStr.isNullOrBlank()) {
                val url = if (urlStr.endsWith("/")) urlStr else "$urlStr/"
                when (ds.name) {
                    "BtSearch" -> io.github.javcinema.network.BtSearch.recreate(url)
                    "Cili" -> io.github.javcinema.network.CiliInfo.recreate(url)
                    "BTSOW" -> io.github.javcinema.network.BTSO.recreate(url)
                }
            }
        }
    }

    fun isShowAds(): Boolean {
        return showAds
    }

    fun setShowAds(showAds: Boolean) {
        this.showAds = showAds
    }

    fun save() {
        val file = configFile ?: return
        val snapshot = Configurations().also { copy ->
            copy.starredMovies = starredMovies?.map { movie ->
                Movie().apply {
                    id = movie.id
                    title = movie.title
                    code = movie.code
                    coverUrl = movie.coverUrl
                    date = movie.date
                    hot = movie.hot
                    dataSourceName = movie.dataSourceName
                    link = movie.link
                }
            }?.toMutableList()
            copy.starredActresses = starredActresses?.map { actress ->
                Actress().apply {
                    name = actress.name
                    imageUrl = actress.imageUrl
                    movieCount = actress.movieCount
                    dataSourceName = actress.dataSourceName
                    link = actress.link
                }
            }?.toMutableList()
            copy.dataSource = dataSource
            copy.downloadCounter = downloadCounter
            copy.setShowAds(isShowAds())
        }
        ioScope.launch {
            saveMutex.withLock {
                try {
                    val tmp = File(file.parentFile, "${file.name}.tmp")
                    FileWriter(tmp).use { writer ->
                        Gson().toJson(snapshot, writer)
                    }
                    if (!tmp.renameTo(file)) {
                        tmp.copyTo(file, overwrite = true)
                        tmp.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun load(file: File): Configurations {
        var config: Configurations? = null
        try {
            config = JavCinema.parseJson(Configurations::class.java, JsonReader(FileReader(file)))
        } catch (_: Exception) {
        }

        if (config == null) {
            config = Configurations()
        }

        config.configFile = file
        return config
    }
}
