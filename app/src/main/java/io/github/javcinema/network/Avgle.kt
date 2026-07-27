package io.github.javcinema.network

import io.github.javcinema.JAViewer
import io.github.javcinema.data.model.AvgleSearchResult
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path

interface Avgle {

    companion object {
        const val BASE_URL = "https://api.avgle.com"

        val INSTANCE: Avgle = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(JAViewer.HTTP_CLIENT)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(Avgle::class.java)
    }

    @GET("/v1/search/{keyword}/0?limit=1")
    @Headers("Accept-Language: zh-CN,zh;q=0.8,en;q=0.6")
    suspend fun search(@Path("keyword") keyword: String): AvgleSearchResult

    @GET("/{path}")
    suspend fun get(@Path("path") path: String): ResponseBody
}
