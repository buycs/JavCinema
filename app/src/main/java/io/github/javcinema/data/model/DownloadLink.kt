package io.github.javcinema.data.model

/**
 * Project: JavCinema
 */
class DownloadLink : Linkable() {

    var title: String? = null
    var size: String? = null
    var date: String? = null
    var magnetLink: MagnetLink? = null
    var files: List<MagnetFile>? = null
    var filesExpanded: Boolean = false
    var filesError: String? = null

    companion object {
        fun create(title: String, size: String, date: String, link: String?, magnetLinkStr: String?): DownloadLink {
            return DownloadLink().apply {
                this.title = title
                this.size = size
                this.date = date
                this.link = link
                this.magnetLink = MagnetLink.create(magnetLinkStr)
            }
        }
    }

    fun hasMagnetLink(): Boolean {
        return magnetLink?.magnetLink != null
    }

    fun setMagnetLink(link: String) {
        magnetLink = MagnetLink.create(link)
    }

    fun copy(
        files: List<MagnetFile>? = this.files,
        filesError: String? = this.filesError,
        magnetLink: MagnetLink? = this.magnetLink
    ): DownloadLink {
        return DownloadLink().also { copy ->
            copy.title = title
            copy.size = size
            copy.date = date
            copy.link = link
            copy.magnetLink = magnetLink
            copy.files = files
            copy.filesExpanded = filesExpanded
            copy.filesError = filesError
        }
    }
}
