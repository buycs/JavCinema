package io.github.javcinema.data.model

import io.github.javcinema.JAViewer

/**
 * Project: JAViewer
 */
open class Actress : Linkable() {

    var name: String? = null
    var imageUrl: String? = null

    companion object {
        fun create(name: String, imageUrl: String, detailUrl: String): Actress {
            return Actress().apply {
                this.name = name
                this.imageUrl = imageUrl
                this.link = detailUrl
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (super.equals(other)) return true
        if (other is Actress) return this.name == other.name
        return false
    }

    override fun hashCode(): Int {
        return name?.hashCode() ?: 0
    }
}

fun Actress.toggleStar() {
    val config = JAViewer.CONFIGURATIONS ?: return
    val a = Actress().apply {
        name = this@toggleStar.name
        imageUrl = this@toggleStar.imageUrl
        link = this@toggleStar.link
    }
    if (config.starredActresses?.contains(a) == true) {
        config.starredActresses?.remove(a)
    } else {
        config.starredActresses?.add(0, a)
    }
}
