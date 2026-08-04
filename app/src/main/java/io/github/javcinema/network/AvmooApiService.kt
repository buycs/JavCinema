package io.github.javcinema.network

import io.github.javcinema.data.model.AvmooGenreListResponse
import io.github.javcinema.data.model.AvmooMovieDetailResponse
import io.github.javcinema.data.model.AvmooMovieListResponse
import io.github.javcinema.data.model.AvmooStarListResponse
import io.github.javcinema.data.model.AvmooStarResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface AvmooApiService {

    @POST("getMovies")
    suspend fun getMovies(@Body params: List<@kotlin.jvm.JvmSuppressWildcards Any>): AvmooMovieListResponse

    @POST("getMovie")
    suspend fun getMovie(@Body params: List<@kotlin.jvm.JvmSuppressWildcards Any>): AvmooMovieDetailResponse

    @POST("getRelatedMovies")
    suspend fun getRelatedMovies(@Body params: List<@kotlin.jvm.JvmSuppressWildcards Any>): AvmooMovieListResponse

    @POST("getFilterMovies")
    suspend fun getFilterMovies(@Body params: List<@kotlin.jvm.JvmSuppressWildcards Any>): AvmooMovieListResponse

    @POST("getStars")
    suspend fun getStars(@Body params: List<@kotlin.jvm.JvmSuppressWildcards Any>): AvmooStarListResponse

    @POST("getStar")
    suspend fun getStar(@Body params: List<@kotlin.jvm.JvmSuppressWildcards Any>): AvmooStarResponse

    @POST("getGenres")
    suspend fun getGenres(@Body params: List<@kotlin.jvm.JvmSuppressWildcards Any>): AvmooGenreListResponse

    @POST("search")
    suspend fun search(@Body params: List<@kotlin.jvm.JvmSuppressWildcards Any>): AvmooMovieListResponse
}
