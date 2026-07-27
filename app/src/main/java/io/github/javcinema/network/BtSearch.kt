package io.github.javcinema.network

import io.github.javcinema.JAViewer
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.security.MessageDigest

interface BtSearch {

    companion object {
        const val BASE_URL = "https://www.btsearch.love"
        private const val SECRET_KEY = "long2ice"
        private var nonceCounter = 0L

        private val signingInterceptor = Interceptor { chain ->
            val original = chain.request()
            val timestamp = System.currentTimeMillis() / 1000
            val nonce = nextNonce()
            val sign = generateSign(timestamp.toInt(), nonce)

            val request = original.newBuilder()
                .header("x-timestamp", timestamp.toString())
                .header("x-nonce", nonce.toString())
                .header("x-sign", sign)
                .build()
            chain.proceed(request)
        }

        private fun nextNonce(): Long {
            nonceCounter++
            return (System.currentTimeMillis() / 1000) + nonceCounter
        }

        private fun generateSign(timestamp: Int, nonce: Long): String {
            val input = "nonce=${nonce}timestamp=${timestamp}key=${SECRET_KEY}"
            return md5(input)
        }

        private fun md5(input: String): String {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray())
            return JAViewer.bytesToHex(digest)
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
                .client(OkHttpClient.Builder()
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
        @Query("mode") mode: String = "and",
        @Query("time") time: String = "",
        @Query("sort") sort: String = "created_at",
        @Query("sort_type") sortType: String = "desc",
        @Query("size") size: String = "all"
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
    val id: Int = 0,
    val name: String = "",
    val size: Long = 0,
    val created_at: String = "",
    val hash: String = "",
    val count: Int = 0,
    val hot: Int = 0
)

data class BtSearchDetailResponse(
    val data: BtSearchTorrentDetail? = null
)

data class BtSearchTorrentDetail(
    val torrentfile: List<BtSearchTorrentFile>? = null
)

data class BtSearchTorrentFile(
    val path: String = "",
    val size: Long = 0
)
