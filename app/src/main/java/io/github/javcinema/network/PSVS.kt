package io.github.javcinema.network

import io.github.javcinema.JAViewer
import io.github.javcinema.data.model.AvgleSearchResult
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface PSVS {

    companion object {
        const val BASE_URL = "http://api.rekonquer.com"

        val INSTANCE: PSVS = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(JAViewer.HTTP_CLIENT)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PSVS::class.java)
    }

    @GET("/psvs/search.php")
    suspend fun search(@Query("kw") keyword: String): AvgleSearchResult
}
