package io.github.javcinema.data.model

/**
 * Project: JAViewer
 */
class DataSource() : Linkable() {

    var name: String? = null
    var domain: String? = null
    var apiPath: String? = null
    var legacies: List<String>? = null

    constructor(name: String, baseUrl: String) : this() {
        this.name = name
        this.link = baseUrl
    }

    override fun toString(): String {
        return name ?: ""
    }

    companion object {
        @JvmField
        val AVMO = DataSource("AVMOO 日本", "https://avos.pw")
        @JvmField
        val AVSO = DataSource("AVSOX 日本无码", "https://avso.club")
        @JvmField
        val AVXO = DataSource("AVMEMO 欧美", "https://avxo.pw")
    }
}
