package io.github.javcinema.data.model

import io.github.javcinema.JAViewer

/**
 * Project: JAViewer
 */

fun Movie.toggleStar() {
    val config = JAViewer.CONFIGURATIONS ?: return
    val m = Movie().apply {
        code = this@toggleStar.code
        title = this@toggleStar.title
        link = this@toggleStar.link
        coverUrl = this@toggleStar.coverUrl
    }
    if (config.starredMovies?.contains(m) == true) {
        config.starredMovies?.remove(m)
    } else {
        config.starredMovies?.add(0, m)
    }
}
class Movie : Linkable() {

    var id: String? = null
    var title: String? = null
    var code: String? = null
    var coverUrl: String? = null
    var date: String? = null
    var hot: Boolean = false

    companion object {
        fun create(title: String, code: String, date: String, coverUrl: String, detailUrl: String, hot: Boolean): Movie {
            return Movie().apply {
                this.title = title
                this.date = date
                this.code = code
                this.coverUrl = coverUrl
                this.hot = hot
                this.link = detailUrl
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (super.equals(other)) return true
        if (other is Movie) return this.code == other.code
        return false
    }

    override fun hashCode(): Int {
        return code?.hashCode() ?: 0
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
