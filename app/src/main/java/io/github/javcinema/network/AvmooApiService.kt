package io.github.javcinema.network

import io.github.javcinema.data.model.AvmooGenreListResponse
import io.github.javcinema.data.model.AvmooMovieDetailResponse
import io.github.javcinema.data.model.AvmooMovieListResponse
import io.github.javcinema.data.model.AvmooStarListResponse
import io.github.javcinema.data.model.AvmooStarResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface AvmooApiService {

    @POST("jav/data/api/getMovies")
    suspend fun getMovies(@Body params: List<@kotlin.jvm.JvmSuppressWildcards Any>): AvmooMovieListResponse

    @POST("jav/data/api/getMovie")
    suspend fun getMovie(@Body params: List<@kotlin.jvm.JvmSuppressWildcards Any>): AvmooMovieDetailResponse

    @POST("jav/data/api/getRelatedMovies")
    suspend fun getRelatedMovies(@Body params: List<@kotlin.jvm.JvmSuppressWildcards Any>): AvmooMovieListResponse

    @POST("jav/data/api/getFilterMovies")
    suspend fun getFilterMovies(@Body params: List<@kotlin.jvm.JvmSuppressWildcards Any>): AvmooMovieListResponse

    @POST("jav/data/api/getStars")
    suspend fun getStars(@Body params: List<@kotlin.jvm.JvmSuppressWildcards Any>): AvmooStarListResponse

    @POST("jav/data/api/getStar")
    suspend fun getStar(@Body params: List<@kotlin.jvm.JvmSuppressWildcards Any>): AvmooStarResponse

    @POST("jav/data/api/getGenres")
    suspend fun getGenres(@Body params: List<@kotlin.jvm.JvmSuppressWildcards Any>): AvmooGenreListResponse
}
