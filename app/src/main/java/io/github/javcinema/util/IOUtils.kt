package io.github.javcinema.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import io.github.javcinema.JavCinema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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

suspend fun saveImageToGallery(context: Context, url: String, subDir: String): File {
    return withContext(Dispatchers.IO) {
        val fileName = url.substringAfterLast("/").substringBefore("?").ifBlank {
            "IMG_${System.currentTimeMillis()}.jpg"
        }
        val safeSubDir = subDir.replace(Regex("[:\\\\/*?\"<>|]"), "-")
        val request = okhttp3.Request.Builder().url(url).build()
        val bytes = JavCinema.HTTP_CLIENT.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            response.body?.bytes() ?: error("empty body")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/JavCinema/$safeSubDir")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert failed")
            try {
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("openOutputStream failed")
            } finally {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            File(safeSubDir, fileName)
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "JavCinema/$safeSubDir"
            )
            dir.mkdirs()
            val file = File(dir, fileName)
            file.writeBytes(bytes)
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            file
        }
    }
}
