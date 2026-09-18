package io.github.javcinema.ui.screen

internal fun actressStarId(link: String?): String {
    val raw = link?.trim().orEmpty()
    if (raw.isEmpty()) return ""
    return raw.substringAfterLast('/').ifBlank { raw }
}

internal fun actressMoviesUrl(starId: String, link: String?): String {
    val raw = link.orEmpty()
    return if (raw.contains("/")) raw else "star/$starId"
}
