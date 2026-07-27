package io.github.javcinema.network

import io.github.javcinema.JAViewer
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query
import retrofit2.http.Url

interface BTSO {

    companion object {
        const val BASE_URL = "https://api.rekonquer.com"

        private var _instance: BTSO? = null

        val INSTANCE: BTSO get() {
            if (_instance == null) {
                _instance = create(BASE_URL)
            }
            return _instance!!
        }

        fun recreate(baseUrl: String = BASE_URL) {
            _instance = create(baseUrl)
        }

        private fun create(baseUrl: String): BTSO {
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(JAViewer.HTTP_CLIENT)
                .build()
                .create(BTSO::class.java)
        }
    }

    @GET("/btso.php")
    @Headers("Accept-Language: zh-CN,zh;q=0.8,en;q=0.6")
    suspend fun search(@Query("kw") keyword: String, @Query("page") page: Int): ResponseBody

    @GET
    @Headers("Accept-Language: zh-CN,zh;q=0.8,en;q=0.6")
    suspend fun get(@Url url: String): ResponseBody
}
