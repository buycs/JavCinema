package io.github.javcinema.ui.navigation

import java.net.URLEncoder

object NavRoutes {
    const val HOME = "home"
    const val POPULAR = "popular"
    const val RELEASED = "released"
    const val ACTRESSES = "actresses"
    const val FAVOURITE = "favourite"
    const val MOVIE_DETAIL = "movie_detail/{movieCode}?link={link}&coverUrl={coverUrl}"
    const val MOVIE_LIST = "movie_list/{title}/{url}"
    const val ACTRESS_DETAIL = "actress_detail/{starId}?name={name}&imageUrl={imageUrl}"
    const val GALLERY = "gallery/{index}"
    const val DOWNLOAD = "download/{keyword}"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val WEBVIEW = "webview/{url}"
    const val MISSAV_PLAY = "missav_play/{movieCode}"
    const val PLAYER = "player?url={url}&referer={referer}"

    private fun encodePath(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    fun movieDetail(movieCode: String, link: String? = null, coverUrl: String? = null): String {
        val base = "movie_detail/${encodePath(movieCode)}"
        val parts = mutableListOf<String>()
        if (!link.isNullOrBlank()) parts.add("link=${encodePath(link)}")
        if (!coverUrl.isNullOrBlank()) parts.add("coverUrl=${encodePath(coverUrl)}")
        return if (parts.isEmpty()) base else "$base?${parts.joinToString("&")}"
    }

    fun movieList(title: String, url: String) = "movie_list/${encodePath(title)}/${encodePath(url)}"
    fun actressDetail(starId: String, name: String? = null, imageUrl: String? = null): String {
        val base = "actress_detail/${encodePath(starId)}"
        val parts = mutableListOf<String>()
        if (!name.isNullOrBlank()) parts.add("name=${encodePath(name)}")
        if (!imageUrl.isNullOrBlank()) parts.add("imageUrl=${encodePath(imageUrl)}")
        return if (parts.isEmpty()) base else "$base?${parts.joinToString("&")}"
    }
    fun gallery(index: Int) = "gallery/$index"
    fun download(keyword: String) = "download/${encodePath(keyword)}"
    fun webview(url: String) = "webview/${encodePath(url)}"
    fun missavPlay(movieCode: String) = "missav_play/${encodePath(movieCode)}"
    fun player(url: String, referer: String = "") =
        "player?url=${encodePath(url)}&referer=${encodePath(referer)}"
}
