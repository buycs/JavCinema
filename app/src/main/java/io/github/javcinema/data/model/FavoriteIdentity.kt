package io.github.javcinema.data.model

internal fun movieFavoriteKey(code: String?, sourceName: String?): String {
    return "${sourceName.orEmpty()}|${code.orEmpty()}"
}

internal fun actressFavoriteKey(name: String?, sourceName: String?): String {
    return "${sourceName.orEmpty()}|${name.orEmpty()}"
}

private fun sourcesCompatible(left: String?, right: String?): Boolean {
    return left.isNullOrBlank() || right.isNullOrBlank() || left == right
}

internal fun sameFavoriteMovie(left: Movie, right: Movie): Boolean {
    if (!sourcesCompatible(left.dataSourceName, right.dataSourceName)) {
        return false
    }
    val leftCode = left.code
    val rightCode = right.code
    if (!leftCode.isNullOrBlank() && leftCode == rightCode) {
        return true
    }
    val leftLink = left.link
    val rightLink = right.link
    return !leftLink.isNullOrBlank() && leftLink == rightLink
}

internal fun sameFavoriteActress(left: Actress, right: Actress): Boolean {
    if (!sourcesCompatible(left.dataSourceName, right.dataSourceName)) {
        return false
    }
    val leftName = left.name
    val rightName = right.name
    if (!leftName.isNullOrBlank() && leftName == rightName) {
        return true
    }
    val leftLink = left.link
    val rightLink = right.link
    return !leftLink.isNullOrBlank() && leftLink == rightLink
}

internal fun matchesFavoriteQuery(title: String?, code: String?, query: String): Boolean {
    if (query.isBlank()) return true
    val needle = query.trim().lowercase()
    return title.orEmpty().lowercase().contains(needle) ||
        code.orEmpty().lowercase().contains(needle)
}
