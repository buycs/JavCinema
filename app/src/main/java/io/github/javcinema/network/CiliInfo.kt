package io.github.javcinema.network

import io.github.javcinema.JAViewer
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

interface CiliInfo {

    companion object {
        const val BASE_URL = "https://cili.info"

        private var _instance: CiliInfo? = null

        val INSTANCE: CiliInfo get() {
            if (_instance == null) {
                _instance = create(BASE_URL)
            }
            return _instance!!
        }

        fun recreate(baseUrl: String = BASE_URL) {
            _instance = create(baseUrl)
        }

        private fun create(baseUrl: String): CiliInfo {
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(JAViewer.HTTP_CLIENT)
                .build()
                .create(CiliInfo::class.java)
        }
    }

    @GET("/search")
    @Headers("Accept-Language: zh-CN,zh;q=0.8,en;q=0.6")
    suspend fun search(@Query("q") keyword: String): ResponseBody

    @GET("/{path}")
    @Headers("Accept-Language: zh-CN,zh;q=0.8,en;q=0.6")
    suspend fun get(@Path("path", encoded = true) path: String): ResponseBody
}
