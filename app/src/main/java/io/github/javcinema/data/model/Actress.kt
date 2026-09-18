package io.github.javcinema.data.model

import io.github.javcinema.JavCinema

/**
 * Project: JavCinema
 */
open class Actress : Linkable() {

    var name: String? = null
    var imageUrl: String? = null
    var movieCount: Int? = null
    var dataSourceName: String? = null

    companion object {
        fun create(name: String, imageUrl: String, detailUrl: String): Actress {
            return Actress().apply {
                this.name = name
                this.imageUrl = imageUrl
                this.link = detailUrl
                dataSourceName = JavCinema.getDataSource()?.name
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Actress) return false
        return sameFavoriteActress(this, other)
    }

    override fun hashCode(): Int {
        return actressFavoriteKey(name, dataSourceName).hashCode()
    }
}

fun Actress.toggleStar() {
    val config = JavCinema.CONFIGURATIONS ?: return
    val a = Actress().apply {
        name = this@toggleStar.name
        imageUrl = this@toggleStar.imageUrl
        link = this@toggleStar.link
        dataSourceName = this@toggleStar.dataSourceName ?: JavCinema.getDataSource()?.name
    }
    val actresses = config.starredActresses ?: return
    val existing = actresses.firstOrNull { sameFavoriteActress(it, a) }
    if (existing != null) {
        actresses.remove(existing)
    } else {
        actresses.add(0, a)
    }
    JavCinema.favoritesVersionFlow.value++
}
