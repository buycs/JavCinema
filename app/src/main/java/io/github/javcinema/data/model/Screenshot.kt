package io.github.javcinema.data.model

/**
 * Project: JavCinema
 */
class Screenshot : Linkable() {

    var thumbnailUrl: String? = null

    companion object {
        fun create(thumbnailUrl: String, imageUrl: String): Screenshot {
            return Screenshot().apply {
                this.thumbnailUrl = thumbnailUrl
                this.link = imageUrl
            }
        }
    }

    fun getImageUrl(): String? {
        return link
    }
}
