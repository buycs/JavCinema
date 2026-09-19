package io.github.javcinema.util

/** 匹配 HTML 标签，用于剥离标题里的高亮标记（如磁力站返回的 `<em>`）。 */
private val HTML_TAG_RE = Regex("<[^>]+>")

/** 去掉字符串中的 HTML 标签。 */
fun stripHtmlTags(text: String): String = text.replace(HTML_TAG_RE, "")
