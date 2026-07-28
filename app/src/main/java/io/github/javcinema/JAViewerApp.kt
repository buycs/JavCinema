package io.github.javcinema

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonReader
import io.github.javcinema.data.model.Configurations
import io.github.javcinema.data.model.DataSource
import io.github.javcinema.network.AvmooApiService
import io.github.javcinema.network.BasicService
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.HashMap

class JAViewer : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        Configurations.loadPrefs(this)
    }

    companion object {
        lateinit var instance: JAViewer
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 5.1.1; Nexus 5 Build/LMY48B; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/43.0.2357.65 Mobile Safari/537.36"

        val DATA_SOURCES: MutableList<DataSource> = mutableListOf()

        var CONFIGURATIONS: Configurations? = null

        var SERVICE: BasicService? = null

        var AVMOO_API_SERVICE: AvmooApiService? = null

        var hostReplacements: MutableMap<String, String> = HashMap()

        val dataSourceVersionFlow = MutableStateFlow(0)

        val HTTP_CLIENT: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .url(replaceUrl(original.url))
                    .header("User-Agent", USER_AGENT)
                    .build()
                chain.proceed(request)
            }
            .cookieJar(object : CookieJar {
                private val cookieStore: MutableMap<HttpUrl, MutableList<Cookie>> = HashMap()

                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    cookieStore[url] = cookies.toMutableList()
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    return cookieStore[url] ?: mutableListOf()
                }
            })
            .build()

        fun getDataSource(): DataSource {
            val saved = CONFIGURATIONS?.dataSource
            if (saved != null && DATA_SOURCES.isNotEmpty()) {
                return DATA_SOURCES.find { it.name == saved.name } ?: saved
            }
            return if (DATA_SOURCES.isNotEmpty()) DATA_SOURCES[0] else saved!!
        }

        fun recreateService() {
            val ds = getDataSource()
            val retrofit = Retrofit.Builder()
                .baseUrl(ds.link!!)
                .client(HTTP_CLIENT)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            SERVICE = retrofit.create(BasicService::class.java)
            AVMOO_API_SERVICE = retrofit.create(AvmooApiService::class.java)
            dataSourceVersionFlow.value++
        }

        fun getStorageDir(): File {
            val dir = instance.getExternalFilesDir(null)!!
            dir.mkdirs()
            return dir
        }

        fun replaceUrl(url: HttpUrl): HttpUrl {
            val builder = url.newBuilder()
            val host = url.toUrl().host
            if (hostReplacements.containsKey(host)) {
                builder.host(hostReplacements[host]!!)
                return builder.build()
            }
            return url
        }

        fun <T> parseJson(beanClass: Class<T>, reader: JsonReader): T {
            return GsonBuilder().create().fromJson(reader, beanClass)
        }

        fun <T> parseJson(beanClass: Class<T>, json: String): T {
            return GsonBuilder().create().fromJson(json, beanClass)
        }

        fun bytesToHex(bytes: ByteArray): String {
            val hexArray = "0123456789abcdef".toCharArray()
            val hexChars = CharArray(bytes.size * 2)
            for (j in bytes.indices) {
                val v = bytes[j].toInt() and 0xFF
                hexChars[j * 2] = hexArray[v ushr 4]
                hexChars[j * 2 + 1] = hexArray[v and 0x0F]
            }
            return String(hexChars)
        }

        fun Objects_equals(a: Any?, b: Any?): Boolean {
            return a === b || a != null && a == b
        }

        fun a(context: Context) {
            val url = "https://qr.alipay.com/a6x05027ymf6n8kl0qkoa54"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
            context.startActivity(intent)
        }

        fun b(s1: String, s2: String): String? {
            return try {
                val md = MessageDigest.getInstance("MD5")
                val bytes = md.digest(String.format("%s%sBrynhildr", s1, s2).toByteArray())
                bytesToHex(bytes)
            } catch (e: NoSuchAlgorithmException) {
                null
            }
        }
    }
}
