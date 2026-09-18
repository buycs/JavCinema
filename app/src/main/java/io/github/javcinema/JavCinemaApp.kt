package io.github.javcinema

import android.app.Application
import android.content.Context
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.imageLoader
import coil.memory.MemoryCache
import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonReader
import io.github.javcinema.BuildConfig
import io.github.javcinema.data.model.Configurations
import io.github.javcinema.data.model.DataSource
import io.github.javcinema.network.AvmooApiService
import io.github.javcinema.network.BasicService
import io.github.javcinema.network.HostCookieJar
import io.github.javcinema.network.RetryInterceptor
import io.github.javcinema.util.BoundedLruMap
import io.github.javcinema.util.BoundedLruSet
import retrofit2.converter.gson.GsonConverterFactory
import android.util.Log
import coil.request.ImageRequest
import okhttp3.ConnectionPool
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.util.HashMap
import java.util.concurrent.TimeUnit

data class ImageUrls(
    val posterSmall: String? = null,
    val posterLarge: String? = null,
    val sampleSmall: List<String>? = null,
    val sampleLarge: List<String>? = null
)

internal fun selectDataSource(
    sources: List<DataSource>,
    saved: DataSource?
): DataSource {
    if (sources.isNotEmpty()) {
        return sources.find { it.name == saved?.name } ?: sources[0]
    }
    if (saved != null) {
        return saved
    }
    error("No data source available")
}

class JavCinema : Application() {

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
        lateinit var instance: JavCinema
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 5.1.1; Nexus 5 Build/LMY48B; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/43.0.2357.65 Mobile Safari/537.36"

        val SHARED_DISK_CACHE: DiskCache by lazy {
            DiskCache.Builder()
                .directory(instance.cacheDir.resolve("image_cache"))
                .maxSizeBytes(256L * 1024 * 1024)
                .build()
        }

        val DATA_SOURCES: MutableList<DataSource> = mutableListOf()
        val MAGNET_SOURCES: MutableList<DataSource> = mutableListOf()

        var CONFIGURATIONS: Configurations? = null

        var SERVICE: BasicService? = null

        var AVMOO_API_SERVICE: AvmooApiService? = null

        var hostReplacements: MutableMap<String, String> = HashMap()

        val dataSourceVersionFlow = MutableStateFlow(0)
        val favoritesVersionFlow = MutableStateFlow(0)
        val uiPrefsVersionFlow = MutableStateFlow(0)

        val HTTP_CLIENT: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(5, 30, TimeUnit.SECONDS))
            .addInterceptor(RetryInterceptor())
            .addInterceptor(HttpLoggingInterceptor { msg -> Log.i("HTTP", msg) }.apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.HEADERS
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            })
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .url(replaceUrl(original.url))
                    .header("User-Agent", USER_AGENT)
                    .build()
                chain.proceed(request)
            }
            .cookieJar(HostCookieJar())
            .build()

        val imageUrlsRegistry = BoundedLruMap<String, ImageUrls>(80)

        val prefetchedLargeCovers = BoundedLruSet<String>(80)

        val prefetchSemaphore: java.util.concurrent.Semaphore = java.util.concurrent.Semaphore(3)

        fun enqueueCoverWithRetry(
            url: String,
            context: Context,
            onSuccess: (() -> Unit)? = null,
            onFailed: (() -> Unit)? = null
        ) {
            val loader = runCatching { instance.imageLoader }.getOrNull() ?: run {
                onFailed?.invoke()
                return
            }
            val disposable = runCatching {
                loader.enqueue(
                    ImageRequest.Builder(context)
                        .data(url)
                        .memoryCacheKey(url)
                        .size(1080, 763)
                        .build()
                )
            }.getOrNull() ?: run {
                onFailed?.invoke()
                return
            }
            disposable.job.invokeOnCompletion { completion ->
                if (completion == null) onSuccess?.invoke() else onFailed?.invoke()
            }
        }

        fun getDataSource(): DataSource {
            return selectDataSource(DATA_SOURCES, CONFIGURATIONS?.dataSource)
        }

        fun recreateService() {
            val ds = getDataSource()
            hostReplacements.clear()
            imageUrlsRegistry.clear()
            prefetchedLargeCovers.clear()
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
    }
}
