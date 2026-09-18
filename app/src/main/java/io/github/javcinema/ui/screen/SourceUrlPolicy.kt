package io.github.javcinema.ui.screen

internal data class SourceUrlCheck(
    val normalized: String? = null,
    val error: String? = null,
    val needsHttpConfirm: Boolean = false
)

internal fun validateSourceUrl(raw: String?, allowHttp: Boolean = false): SourceUrlCheck {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return SourceUrlCheck(normalized = null)
    val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
    val lower = withScheme.lowercase()
    if (lower.startsWith("https://")) {
        return if (looksLikeHost(withScheme)) {
            SourceUrlCheck(normalized = withScheme)
        } else {
            SourceUrlCheck(error = "地址无效")
        }
    }
    if (lower.startsWith("http://")) {
        return if (!looksLikeHost(withScheme)) {
            SourceUrlCheck(error = "地址无效")
        } else if (allowHttp) {
            SourceUrlCheck(normalized = withScheme)
        } else {
            SourceUrlCheck(normalized = withScheme, needsHttpConfirm = true, error = "使用 HTTP 需确认")
        }
    }
    return SourceUrlCheck(error = "只支持 http 或 https")
}

private fun looksLikeHost(url: String): Boolean {
    val rest = url.substringAfter("://")
    val host = rest.substringBefore('/').substringBefore('?').substringBefore('#')
    return host.contains('.') || host.equals("localhost", ignoreCase = true)
}
