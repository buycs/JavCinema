package io.github.javcinema.network

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Url

interface BasicService {

    companion object {
        const val LANGUAGE_NODE = "/cn"
    }

    @GET("$LANGUAGE_NODE/page/{page}")
    suspend fun getHomePage(@Path("page") page: Int): ResponseBody

    @GET("$LANGUAGE_NODE/released/page/{page}")
    suspend fun getReleased(@Path("page") page: Int): ResponseBody

    @GET("$LANGUAGE_NODE/popular/page/{page}")
    suspend fun getPopular(@Path("page") page: Int): ResponseBody

    @GET("$LANGUAGE_NODE/actresses/page/{page}")
    suspend fun getActresses(@Path("page") page: Int): ResponseBody

    @GET("$LANGUAGE_NODE/genre")
    suspend fun getGenre(): ResponseBody

    @GET
    suspend fun get(@Url url: String): ResponseBody
}
