package io.github.javcinema.data.model

import java.util.ArrayList

/**
 * Project: JAViewer
 */
class MovieDetail {

    val screenshots: MutableList<Screenshot> = ArrayList()
    var title: String? = null
    var coverUrl: String? = null
    var code: String? = null
    var btsSearchUrl: String? = null
    val headers: MutableList<Header> = ArrayList()
    val genres: MutableList<Genre> = ArrayList()
    val actresses: MutableList<Actress> = ArrayList()

    class Header : Linkable() {
        var name: String? = null
        var value: String? = null

        companion object {
            fun create(name: String, value: String, link: String?): Header {
                return Header().apply {
                    this.name = name
                    this.value = value
                    this.link = link
                }
            }
        }

        override fun toString(): String {
            return "Header{" +
                    "name='" + name + '\'' +
                    ", value='" + value + '\'' +
                    '}'
        }
    }
}
