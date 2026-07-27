package io.github.javcinema.util

import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset

val UTF_8: Charset = Charset.forName("utf-8")

@Throws(IOException::class)
fun readText(stream: InputStream, charset: Charset): String {
    return stream.bufferedReader(charset).use { reader ->
        reader.readLines().joinToString("")
    }
}
