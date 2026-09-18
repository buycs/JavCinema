package io.github.javcinema.data.model

import io.github.javcinema.JavCinema

/**
 * Project: JavCinema
 */

fun Movie.toggleStar() {
    val config = JavCinema.CONFIGURATIONS ?: return
    val m = Movie().apply {
        code = this@toggleStar.code
        title = this@toggleStar.title
        link = this@toggleStar.link
        coverUrl = this@toggleStar.coverUrl
        date = this@toggleStar.date
        dataSourceName = this@toggleStar.dataSourceName ?: JavCinema.getDataSource()?.name
    }
    val movies = config.starredMovies ?: return
    val existing = movies.firstOrNull { sameFavoriteMovie(it, m) }
    if (existing != null) {
        movies.remove(existing)
    } else {
        movies.add(0, m)
    }
    JavCinema.favoritesVersionFlow.value++
}
class Movie : Linkable() {

    var id: String? = null
    var title: String? = null
    var code: String? = null
    var coverUrl: String? = null
    var date: String? = null
    var hot: Boolean = false
    var dataSourceName: String? = null

    companion object {
        fun create(title: String, code: String, date: String, coverUrl: String, detailUrl: String, hot: Boolean): Movie {
            return Movie().apply {
                this.title = title
                this.date = date
                this.code = code
                this.coverUrl = coverUrl
                this.hot = hot
                this.link = detailUrl
                dataSourceName = JavCinema.getDataSource()?.name
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Movie) return false
        return sameFavoriteMovie(this, other)
    }

    override fun hashCode(): Int {
        return movieFavoriteKey(code, dataSourceName).hashCode()
    }

    override fun toString(): String {
        return "Movie{" +
                "title='" + title + '\'' +
                ", code='" + code + '\'' +
                ", coverUrl='" + coverUrl + '\'' +
                ", date='" + date + '\'' +
                ", hot=" + hot +
                '}'
    }
}
