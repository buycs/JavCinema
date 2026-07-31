package io.github.javcinema.data.model

/**
 * Project: JavCinema
 */
class Genre : Linkable() {

    var name: String? = null

    companion object {
        fun create(name: String, link: String): Genre {
            return Genre().apply {
                this.name = name
                this.link = link
            }
        }
    }

    override fun toString(): String {
        return name + ":" + link
    }
}
