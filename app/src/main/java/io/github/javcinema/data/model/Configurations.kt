package io.github.javcinema.data.model

import android.content.Context
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import io.github.javcinema.JAViewer
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
            if (field == null && JAViewer.DATA_SOURCES.isNotEmpty()) {
                field = JAViewer.DATA_SOURCES[0]
            }
            return field
        }
    private var configFile: File? = null
    private var showAds: Boolean = false
    var downloadCounter: Long = 0

    companion object {
        private const val PREFS_NAME = "javiewer_config"
        private const val KEY_CUSTOM_AVMOO = "custom_avmoo_url"
        private const val KEY_CUSTOM_AVSO = "custom_avso_url"
        private const val KEY_CUSTOM_AVXO = "custom_avxo_url"
        private const val KEY_CUSTOM_BTSEARCH = "custom_btsearch_url"
        private const val KEY_CUSTOM_CILI = "custom_cili_url"
        private const val KEY_CUSTOM_BTSOW = "custom_btsow_url"

        var customAvmooUrl: String? = null
        var customAvsoUrl: String? = null
        var customAvxoUrl: String? = null
        var customBtSearchUrl: String? = null
        var customCiliUrl: String? = null
        var customBtsowUrl: String? = null

        fun loadPrefs(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            customAvmooUrl = prefs.getString(KEY_CUSTOM_AVMOO, null)
            customAvsoUrl = prefs.getString(KEY_CUSTOM_AVSO, null)
            customAvxoUrl = prefs.getString(KEY_CUSTOM_AVXO, null)
            customBtSearchUrl = prefs.getString(KEY_CUSTOM_BTSEARCH, null)
            customCiliUrl = prefs.getString(KEY_CUSTOM_CILI, null)
            customBtsowUrl = prefs.getString(KEY_CUSTOM_BTSOW, null)
        }

        fun savePrefs(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_CUSTOM_AVMOO, customAvmooUrl)
                .putString(KEY_CUSTOM_AVSO, customAvsoUrl)
                .putString(KEY_CUSTOM_AVXO, customAvxoUrl)
                .putString(KEY_CUSTOM_BTSEARCH, customBtSearchUrl)
                .putString(KEY_CUSTOM_CILI, customCiliUrl)
                .putString(KEY_CUSTOM_BTSOW, customBtsowUrl)
                .apply()
        }
    }

    fun applyCustomUrls() {
        for (ds in JAViewer.DATA_SOURCES) {
            val custom = when (ds.name) {
                "AVMOO 日本" -> customAvmooUrl
                "AVMOO 日本无码" -> customAvsoUrl
                "AVMOO 欧美" -> customAvxoUrl
                else -> null
            }
            if (!custom.isNullOrBlank()) {
                val link = if (custom.endsWith("/")) custom else "$custom/"
                ds.link = link
            }
        }
        val savedDs = dataSource
        if (savedDs != null && JAViewer.DATA_SOURCES.isNotEmpty()) {
            dataSource = JAViewer.DATA_SOURCES.find { it.name == savedDs.name } ?: savedDs
        }
        for (ds in JAViewer.DATA_SOURCES) {
            try {
                val host = URI(ds.link!!).host
                ds.legacies?.forEach { h ->
                    JAViewer.hostReplacements[h] = host
                }
            } catch (_: Exception) {
            }
        }
        fun recreateMagnet(name: String) {
            val urlStr = when (name) {
                "BtSearch" -> customBtSearchUrl
                "Cili" -> customCiliUrl
                "BTSOW" -> customBtsowUrl
                else -> null
            }
            if (!urlStr.isNullOrBlank()) {
                val url = if (urlStr.endsWith("/")) urlStr else "$urlStr/"
                when (name) {
                    "BtSearch" -> io.github.javcinema.network.BtSearch.recreate(url)
                    "Cili" -> io.github.javcinema.network.CiliInfo.recreate(url)
                    "BTSOW" -> io.github.javcinema.network.BTSO.recreate(url)
                }
            }
        }

        val btUrl = customBtSearchUrl
        if (!btUrl.isNullOrBlank()) {
            val url = if (btUrl.endsWith("/")) btUrl else "$btUrl/"
            io.github.javcinema.network.BtSearch.recreate(url)
        }
        val ciliUrl = customCiliUrl
        if (!ciliUrl.isNullOrBlank()) {
            val url = if (ciliUrl.endsWith("/")) ciliUrl else "$ciliUrl/"
            io.github.javcinema.network.CiliInfo.recreate(url)
        }
        val btsowUrl = customBtsowUrl
        if (!btsowUrl.isNullOrBlank()) {
            val url = if (btsowUrl.endsWith("/")) btsowUrl else "$btsowUrl/"
            io.github.javcinema.network.BTSO.recreate(url)
        }
    }

    fun isShowAds(): Boolean {
        return showAds
    }

    fun setShowAds(showAds: Boolean) {
        this.showAds = showAds
    }

    fun save() {
        try {
            val f = configFile ?: return
            val writer = FileWriter(f)
            Gson().toJson(this, writer)
            writer.flush()
            writer.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun load(file: File): Configurations {
        this.configFile = file
        var config: Configurations? = null
        try {
            config = JAViewer.parseJson(Configurations::class.java, JsonReader(FileReader(file)))
        } catch (_: Exception) {
        }

        if (config == null) {
            config = Configurations()
        }

        return config
    }
}
