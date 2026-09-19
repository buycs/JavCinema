package io.github.javcinema.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TextUtilsTest {

    @Test
    fun stripsHtmlTags() {
        assertEquals("SSIS-001", stripHtmlTags("<em>SSIS</em>-001"))
        assertEquals("三上悠亜", stripHtmlTags("<b>三上</b>悠亜"))
        assertEquals("plain", stripHtmlTags("plain"))
        assertEquals("", stripHtmlTags("<br/>"))
    }

    @Test
    fun sanitizePathSegment_replacesIllegalCharacters() {
        assertEquals("a-b", sanitizePathSegment("a/b"))
        assertEquals("a-b-c-d-e-f-g-h-i", sanitizePathSegment("""a\b:c*d?e"f<g>h|i"""))
        assertEquals("三上悠亜", sanitizePathSegment("三上悠亜"))
        assertEquals("", sanitizePathSegment(""))
    }
}
