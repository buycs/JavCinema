package io.github.javcinema.ui.screen

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.javcinema.JavCinema
import io.github.javcinema.data.model.Actress
import io.github.javcinema.data.model.Movie
import io.github.javcinema.data.model.sameFavoriteActress
import io.github.javcinema.data.model.sameFavoriteMovie

internal const val FAVORITE_BACKUP_FORMAT = "javcinema-favorites"
internal const val FAVORITE_BACKUP_VERSION = 1
internal const val FAVORITE_BACKUP_FILE_NAME = "javcinema-favorites.json"

/**
 * 单次导入的条目上限（影片、女优各自计数）。
 *
 * 不加限制时，一个构造过的超大 JSON 会把海量条目灌进收藏：导入过程要在已有列表上
 * 逐条线性查重，条目数一大就是 O(n²)，主线程外的解析也会长时间占用内存。
 * 上限取 2000 —— 远超正常用户收藏量，同时把最坏情况约束在可控范围。
 */
internal const val MAX_IMPORT_ITEMS = 2000

internal data class FavoriteImportItem(
    val codeOrName: String,
    val title: String? = null,
    val source: String? = null,
    val isActress: Boolean = false,
    val link: String? = null,
    val coverUrl: String? = null,
    val date: String? = null,
    val imageUrl: String? = null
)

internal data class FavoriteImport(
    val movies: List<FavoriteImportItem>,
    val actresses: List<FavoriteImportItem>
)

private val favoriteGson = GsonBuilder()
    .setPrettyPrinting()
    .disableHtmlEscaping()
    .create()

internal fun favoritesToJson(
    movies: List<FavoriteImportItem>,
    actresses: List<FavoriteImportItem>
): String {
    val root = linkedMapOf<String, Any>(
        "format" to FAVORITE_BACKUP_FORMAT,
        "version" to FAVORITE_BACKUP_VERSION,
        "movies" to movies.mapNotNull { item ->
            val code = item.codeOrName.trim()
            if (code.isEmpty()) return@mapNotNull null
            linkedMapOf<String, String>().apply {
                put("code", code)
                item.title?.trim()?.takeIf { it.isNotEmpty() }?.let { put("title", it) }
                item.source?.trim()?.takeIf { it.isNotEmpty() }?.let { put("source", it) }
                item.link?.trim()?.takeIf { it.isNotEmpty() }?.let { put("link", it) }
                item.coverUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { put("coverUrl", it) }
                item.date?.trim()?.takeIf { it.isNotEmpty() }?.let { put("date", it) }
            }
        },
        "actresses" to actresses.mapNotNull { item ->
            val name = item.codeOrName.trim()
            if (name.isEmpty()) return@mapNotNull null
            linkedMapOf<String, String>().apply {
                put("name", name)
                item.source?.trim()?.takeIf { it.isNotEmpty() }?.let { put("source", it) }
                item.link?.trim()?.takeIf { it.isNotEmpty() }?.let { put("link", it) }
                item.imageUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { put("imageUrl", it) }
            }
        }
    )
    return favoriteGson.toJson(root)
}

internal fun parseFavoriteExport(text: String): FavoriteImport {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) throw IllegalArgumentException("文件为空")
    val element = JsonParser.parseString(trimmed)
    if (!element.isJsonObject) throw IllegalArgumentException("不是有效的收藏 JSON")
    val root = element.asJsonObject
    val format = root.str("format")
    if (format != null && format != FAVORITE_BACKUP_FORMAT) {
        throw IllegalArgumentException("不是 JavCinema 收藏文件")
    }
    // 先看数组长度再逐条解析：超大文件在进入映射逻辑前就被拒绝。
    val moviesArray = root.array("movies")
    val actressesArray = root.array("actresses")
    if (moviesArray.size() > MAX_IMPORT_ITEMS || actressesArray.size() > MAX_IMPORT_ITEMS) {
        throw IllegalArgumentException(
            "条目过多（影片 ${moviesArray.size()}、女优 ${actressesArray.size()}），单次上限 $MAX_IMPORT_ITEMS"
        )
    }
    val movies = moviesArray.mapNotNull { item ->
        if (!item.isJsonObject) return@mapNotNull null
        val obj = item.asJsonObject
        val code = obj.str("code") ?: return@mapNotNull null
        FavoriteImportItem(
            codeOrName = code,
            title = obj.str("title"),
            source = obj.str("source", "dataSource", "dataSourceName"),
            link = obj.str("link"),
            coverUrl = obj.str("coverUrl", "cover"),
            date = obj.str("date")
        )
    }
    val actresses = actressesArray.mapNotNull { item ->
        if (!item.isJsonObject) return@mapNotNull null
        val obj = item.asJsonObject
        val name = obj.str("name") ?: return@mapNotNull null
        FavoriteImportItem(
            codeOrName = name,
            source = obj.str("source", "dataSource", "dataSourceName"),
            isActress = true,
            link = obj.str("link"),
            imageUrl = obj.str("imageUrl", "image")
        )
    }
    return FavoriteImport(movies, actresses)
}

internal fun hasFavoritesToExport(): Boolean {
    val movies = JavCinema.CONFIGURATIONS?.starredMovies.orEmpty()
    val actresses = JavCinema.CONFIGURATIONS?.starredActresses.orEmpty()
    return movies.any { !it.code.isNullOrBlank() } || actresses.any { !it.name.isNullOrBlank() }
}

internal fun exportFavoritesJson(): String {
    val movies = JavCinema.CONFIGURATIONS?.starredMovies.orEmpty().map { movie ->
        FavoriteImportItem(
            codeOrName = movie.code.orEmpty(),
            title = movie.title,
            source = movie.dataSourceName,
            link = movie.link,
            coverUrl = movie.coverUrl,
            date = movie.date
        )
    }
    val actresses = JavCinema.CONFIGURATIONS?.starredActresses.orEmpty().map { actress ->
        FavoriteImportItem(
            codeOrName = actress.name.orEmpty(),
            source = actress.dataSourceName,
            isActress = true,
            link = actress.link,
            imageUrl = actress.imageUrl
        )
    }
    return favoritesToJson(movies, actresses)
}

internal fun exportFavoritesToUri(context: Context, uri: Uri): Boolean {
    return runCatching {
        val json = exportFavoritesJson()
        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(json.toByteArray(Charsets.UTF_8))
            output.flush()
        } ?: return false
        true
    }.getOrDefault(false)
}

internal fun importFavoritesFromUri(context: Context, uri: Uri): Int {
    val text = context.contentResolver.openInputStream(uri)?.use { input ->
        input.bufferedReader(Charsets.UTF_8).readText()
    } ?: throw IllegalArgumentException("无法读取文件")
    return importFavoritesText(context, text, showToast = false)
}

internal fun importFavoritesText(context: Context, text: String, showToast: Boolean = true): Int {
    val parsed = parseFavoriteExport(text)
    val config = JavCinema.CONFIGURATIONS ?: return 0
    val movies = config.starredMovies ?: return 0
    val actresses = config.starredActresses ?: return 0
    var added = 0
    parsed.movies.forEach { item ->
        val movie = Movie().apply {
            code = item.codeOrName
            title = item.title
            dataSourceName = item.source
            link = item.link
            coverUrl = item.coverUrl
            date = item.date
        }
        if (movies.none { sameFavoriteMovie(it, movie) }) {
            movies.add(0, movie)
            added++
        }
    }
    parsed.actresses.forEach { item ->
        val actress = Actress().apply {
            name = item.codeOrName
            dataSourceName = item.source
            link = item.link
            imageUrl = item.imageUrl
        }
        if (actresses.none { sameFavoriteActress(it, actress) }) {
            actresses.add(0, actress)
            added++
        }
    }
    config.save()
    JavCinema.favoritesVersionFlow.value++
    if (showToast) {
        Toast.makeText(context, "已导入 $added 条", Toast.LENGTH_SHORT).show()
    }
    return added
}

private fun JsonObject.str(vararg keys: String): String? {
    for (key in keys) {
        val value = get(key) ?: continue
        if (!value.isJsonPrimitive) continue
        val text = value.asString.trim()
        if (text.isNotEmpty()) return text
    }
    return null
}

private fun JsonObject.array(key: String) =
    get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: com.google.gson.JsonArray()
