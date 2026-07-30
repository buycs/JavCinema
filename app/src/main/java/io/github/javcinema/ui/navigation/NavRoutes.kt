package io.github.javcinema.ui.navigation

import java.net.URLEncoder

object NavRoutes {
    const val HOME = "home"
    const val POPULAR = "popular"
    const val RELEASED = "released"
    const val ACTRESSES = "actresses"
    const val GENRE = "genre"
    const val FAVOURITE = "favourite"
    const val MOVIE_DETAIL = "movie_detail/{movieCode}?link={link}&coverUrl={coverUrl}"
    const val MOVIE_LIST = "movie_list/{title}/{url}"
    const val GALLERY = "gallery/{index}"
    const val DOWNLOAD = "download/{keyword}"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val WEBVIEW = "webview/{url}"

    fun movieDetail(movieCode: String, link: String? = null, coverUrl: String? = null): String {
        val base = "movie_detail/$movieCode"
        val parts = mutableListOf<String>()
        if (!link.isNullOrBlank()) parts.add("link=$link")
        if (!coverUrl.isNullOrBlank()) parts.add("coverUrl=${URLEncoder.encode(coverUrl, "UTF-8")}")
        return if (parts.isEmpty()) base else "$base?${parts.joinToString("&")}"
    }
    fun movieList(title: String, url: String) = "movie_list/$title/$url"
    fun gallery(index: Int) = "gallery/$index"
    fun download(keyword: String) = "download/$keyword"
    fun webview(url: String) = "webview/$url"
}
