package io.github.javcinema.ui.screen

import io.github.javcinema.data.model.Movie

object GalleryState {
    var imageUrls: List<String> = emptyList()
    var initialIndex: Int = 0
    var movie: Movie? = null
}
