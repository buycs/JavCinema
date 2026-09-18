package io.github.javcinema.ui.screen

internal fun genreIdFromLink(link: String?): String {
    val raw = link?.trim().orEmpty()
    if (raw.isEmpty()) return ""
    return raw.substringAfterLast('/').ifBlank { raw }
}

internal fun combinedGenreFilterUrl(ids: List<String>): String {
    val unique = ids.map { genreIdFromLink(it) }.filter { it.isNotBlank() }.distinct()
    return "genre/" + unique.joinToString(",")
}

internal fun combinedGenreTitle(names: List<String>): String {
    val unique = names.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    return unique.joinToString("、").ifBlank { "类型" }
}
