package io.github.javcinema.network

import io.github.javcinema.JavCinema
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Url

interface TorrentKitty {

    companion object {
        const val BASE_URL = "https://www.torrentkitty.tv"

        val INSTANCE: TorrentKitty = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(JavCinema.HTTP_CLIENT)
            .build()
            .create(TorrentKitty::class.java)
    }

    @GET("/search/{keyword}")
    @Headers("Accept-Language: zh-CN,zh;q=0.8,en;q=0.6", "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", "Referer: https://www.torrentkitty.tv/")
    suspend fun search(@Path("keyword") keyword: String): ResponseBody

    @GET("/search/{keyword}/{page}")
    @Headers("Accept-Language: zh-CN,zh;q=0.8,en;q=0.6", "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", "Referer: https://www.torrentkitty.tv/")
    suspend fun searchPage(@Path("keyword") keyword: String, @Path("page") page: Int): ResponseBody

    @GET
    @Headers("Accept-Language: zh-CN,zh;q=0.8,en;q=0.6", "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", "Referer: https://www.torrentkitty.tv/")
    suspend fun get(@Url url: String): ResponseBody
}
