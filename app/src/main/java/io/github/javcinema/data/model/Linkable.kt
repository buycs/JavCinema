package io.github.javcinema.data.model

import java.io.Serializable

/**
 * Project: JAViewer
 */
open class Linkable : Serializable {

    var link: String? = null

    override fun equals(other: Any?): Boolean {
        if (other !is Linkable) return false
        return link == other.link
    }

    override fun hashCode(): Int {
        return link?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "Linkable{" +
                "link='" + link + '\'' +
                '}'
    }
}
