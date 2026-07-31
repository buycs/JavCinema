package io.github.javcinema

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonReader
import io.github.javcinema.data.model.Configurations
import io.github.javcinema.data.model.DataSource
import io.github.javcinema.network.AvmooApiService
import io.github.javcinema.network.BasicService
import retrofit2.converter.gson.GsonConverterFactory
import android.util.Log
import coil.request.ImageRequest
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.ConnectionPool
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.HashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class JAViewer : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        Configurations.loadPrefs(this)
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .okHttpClient(HTTP_CLIENT)
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.25)
                        .build()
                }
                .diskCache(SHARED_DISK_CACHE)
                .build()
        )
    }

    companion object {
        lateinit var instance: JAViewer
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 5.1.1; Nexus 5 Build/LMY48B; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/43.0.2357.65 Mobile Safari/537.36"

        val SHARED_DISK_CACHE: DiskCache by lazy {
            DiskCache.Builder()
                .directory(instance.cacheDir.resolve("image_cache"))
                .maxSizeBytes(512L * 1024 * 1024)
                .build()
        }

        val DATA_SOURCES: MutableList<DataSource> = mutableListOf()
        val MAGNET_SOURCES: MutableList<DataSource> = mutableListOf()

        var CONFIGURATIONS: Configurations? = null

        var SERVICE: BasicService? = null

        var AVMOO_API_SERVICE: AvmooApiService? = null

        var hostReplacements: MutableMap<String, String> = HashMap()

        val dataSourceVersionFlow = MutableStateFlow(0)

        val HTTP_CLIENT: OkHttpClient = OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 30, TimeUnit.SECONDS))
            .addInterceptor(HttpLoggingInterceptor { msg -> Log.i("HTTP", msg) }.apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            })
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

        val SCREENSHOT_HTTP_CLIENT: OkHttpClient = HTTP_CLIENT.newBuilder()
            .dispatcher(okhttp3.Dispatcher().apply { maxRequestsPerHost = 2 })
            .build()

        val screenshotImageLoader: ImageLoader by lazy {
            ImageLoader.Builder(instance)
                .okHttpClient(SCREENSHOT_HTTP_CLIENT)
                .memoryCache {
                    MemoryCache.Builder(instance)
                        .maxSizePercent(0.25)
                        .build()
                }
                .diskCache(SHARED_DISK_CACHE)
                .build()
        }

        val PREFETCH_HTTP_CLIENT: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(2, 30, TimeUnit.SECONDS))
            .addInterceptor(HttpLoggingInterceptor { msg -> Log.i("HTTP", msg) }.apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            })
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .url(replaceUrl(original.url))
                    .header("User-Agent", USER_AGENT)
                    .build()
                chain.proceed(request)
            }
            .dispatcher(okhttp3.Dispatcher().apply { maxRequestsPerHost = 1 })
            .build()

        val prefetchImageLoader: ImageLoader by lazy {
            ImageLoader.Builder(instance)
                .okHttpClient(PREFETCH_HTTP_CLIENT)
                .memoryCache {
                    MemoryCache.Builder(instance)
                        .maxSizePercent(0.05)
                        .build()
                }
                .diskCache(SHARED_DISK_CACHE)
                .build()
        }

        val prefetchedLargeCovers: MutableSet<String> = ConcurrentHashMap.newKeySet()

        fun enqueueCoverWithRetry(
            url: String,
            context: Context,
            onSuccess: (() -> Unit)? = null,
            onFailed: (() -> Unit)? = null
        ) {
            enqueueCoverInternal(url, context, 0, onSuccess, onFailed)
        }

        private fun enqueueCoverInternal(
            url: String,
            context: Context,
            attempt: Int,
            onSuccess: (() -> Unit)?,
            onFailed: (() -> Unit)?
        ) {
            val disposable = runCatching {
                prefetchImageLoader.enqueue(
                    ImageRequest.Builder(context).data(url).build()
                )
            }.getOrNull() ?: run {
                onFailed?.invoke()
                return
            }
            disposable.job.invokeOnCompletion { completion ->
                when {
                    completion == null -> onSuccess?.invoke()
                    attempt < 1 -> enqueueCoverInternal(url, context, attempt + 1, onSuccess, onFailed)
                    else -> onFailed?.invoke()
                }
            }
        }

        fun getDataSource(): DataSource {
            val saved = CONFIGURATIONS?.dataSource
            if (saved != null && DATA_SOURCES.isNotEmpty()) {
                return DATA_SOURCES.find { it.name == saved.name } ?: saved
            }
            return if (DATA_SOURCES.isNotEmpty()) DATA_SOURCES[0] else saved!!
        }

        fun recreateService() {
            val ds = getDataSource()
            hostReplacements.clear()
            val host = try { java.net.URI(ds.link!!).host } catch (_: Exception) { null }
            if (host != null) {
                ds.legacies?.forEach { h -> hostReplacements[h] = host }
            }
            val domain = ds.link?.trimEnd('/') ?: ""
            val apiPath = ds.apiPath?.trim('/')?.let { "/$it/" } ?: "/jav/data/api/"
            val baseUrl = "$domain$apiPath"
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
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
