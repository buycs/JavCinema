package io.github.javcinema.network

import io.github.javcinema.JavCinema
import okhttp3.Interceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.security.MessageDigest
import java.util.UUID

interface BtSearch {

    companion object {
        const val BASE_URL = "https://www.btsearch.love"
        private const val SECRET_KEY = "long2ice"

        private val signingInterceptor = Interceptor { chain ->
            val original = chain.request()
            val url = original.url
            val timestamp = System.currentTimeMillis() / 1000
            val nonce = UUID.randomUUID().toString().replace("-", "").substring(0, 8)

            val params = mutableMapOf<String, String>()
            url.queryParameterNames.forEach { name ->
                url.queryParameter(name)?.let { params[name] = it }
            }
            params["timestamp"] = timestamp.toString()
            params["nonce"] = nonce

            val sorted = params.entries.sortedBy { it.key }
            val raw = sorted.joinToString("&") { "${it.key}=${it.value}" } + "&key=$SECRET_KEY"
            val sign = md5(raw).uppercase()

            android.util.Log.d("BtSearch", "url=$url params=$params raw=$raw sign=$sign")

            val request = original.newBuilder()
                .header("x-timestamp", timestamp.toString())
                .header("x-nonce", nonce)
                .header("x-sign", sign)
                .header("Accept", "application/json")
                .header("User-Agent", JavCinema.USER_AGENT)
                .header("Referer", "https://www.btsearch.love/search")
                .build()
            chain.proceed(request)
        }

        private fun md5(input: String): String {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray())
            return JavCinema.bytesToHex(digest)
        }

        private var _instance: BtSearch? = null

        val INSTANCE: BtSearch get() {
            if (_instance == null) {
                _instance = create(BASE_URL)
            }
            return _instance!!
        }

        fun recreate(baseUrl: String = BASE_URL) {
            _instance = create(baseUrl)
        }

        private fun create(baseUrl: String): BtSearch {
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(JavCinema.HTTP_CLIENT.newBuilder()
                    .addInterceptor(signingInterceptor)
                    .build())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(BtSearch::class.java)
        }
    }

    @GET("/api/search")
    suspend fun search(
        @Query("keyword") keyword: String,
        @Query("limit") limit: Int = 10,
        @Query("offset") offset: Int = 0,
        @Query("mode") mode: String = "",
        @Query("time") time: String = "",
        @Query("sort") sort: String = "",
        @Query("sort_type") sortType: String = "asc",
        @Query("size") size: String = ""
    ): BtSearchResponse

    @GET("/api/torrent/{id}")
    suspend fun getDetail(@Path("id") id: String, @Query("keyword") keyword: String): BtSearchDetailResponse
}

data class BtSearchResponse(
    val total: Int = 0,
    val data: List<BtSearchItem> = emptyList(),
    val time_ms: Double = 0.0
)

data class BtSearchItem(
    val id: Long = 0,
    val name: String = "",
    val size: String = "0",
    val created_at: String = "",
    val hash: String = "",
    val count: Int = 0,
    val hot: Int = 0
)

data class BtSearchDetailResponse(
    val torrentfile: List<BtSearchTorrentFile>? = null
)

data class BtSearchTorrentFile(
    val name: String = "",
    val size: String = "0"
)
