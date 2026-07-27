package io.github.javcinema.data.model

import com.google.gson.JsonElement

data class AvmooMovieListResponse(
    val code: Int,
    val data: List<AvmooMovie>?
)

data class AvmooMovieDetailResponse(
    val code: Int,
    val data: AvmooMovieDetail?
)

data class AvmooStarListResponse(
    val code: Int,
    val data: List<AvmooStar>?
)

data class AvmooStarResponse(
    val code: Int,
    val data: AvmooStar?
)

data class AvmooGenreListResponse(
    val code: Int,
    val data: Map<String, List<AvmooGenre>>?
)

data class AvmooMovie(
    val movieId: String,
    val movieFanHao: String,
    val title_ja: String?,
    val title_cn: String?,
    val title_en: String?,
    val title_tw: String?,
    val title: String?,
    val releaseDate: String?,
    val posterSmall: String?,
    val posterLarge: String?,
    val sampleSmall: List<String>?,
    val sampleLarge: List<String>?,
    val length: Int?,
    val movieDmmUrl: String?,
    val starId: String?,
    val genreId: String?,
    val studioId: String?,
    val directorId: String?,
    val labelId: String?,
    val seriesId: String?
)

data class AvmooMovieDetail(
    val movieId: String,
    val movieFanHao: String,
    val title_ja: String?,
    val title_cn: String?,
    val title_en: String?,
    val title_tw: String?,
    val title: String?,
    val releaseDate: String?,
    val length: Int?,
    val posterSmall: String?,
    val posterLarge: String?,
    val sampleSmall: List<String>?,
    val sampleLarge: List<String>?,
    val movieDmmUrl: String?,
    val studio: AvmooStudio?,
    val director: AvmooDirector?,
    val label: AvmooLabel?,
    val series: AvmooSeries?,
    val genre: List<AvmooGenre>?,
    val star: List<AvmooStar>?,
    val btsSearchUrl: String?
)

data class AvmooStar(
    val starId: String?,
    val starDmmId: Int?,
    val starName_ja: String?,
    val starName_en: String?,
    val starName_cn: String?,
    val starName_tw: String?,
    val avatar: String?,
    val avatarUrl: String?,
    val movieCount: Int?,
    val weight: Int?,
    val birthday: String?,
    val size: JsonElement?
)

data class AvmooGenre(
    val genreId: String?,
    val genreDmmId: Int?,
    val genreName_ja: String?,
    val genreName_cn: String?,
    val genreName_en: String?,
    val genreName_tw: String?,
    val genreName: String?,
    val type: Int?
)

data class AvmooDirector(
    val directorId: String?,
    val directorDmmId: Int?,
    val directorName_ja: String?,
    val directorName_en: String?,
    val directorName_cn: String?,
    val directorName_tw: String?,
    val directorName: String?
)

data class AvmooStudio(
    val studioId: String?,
    val studioDmmId: Int?,
    val studioName_ja: String?,
    val studioName_en: String?,
    val studioName_cn: String?,
    val studioName_tw: String?,
    val studioName: String?
)

data class AvmooLabel(
    val labelId: String?,
    val labelDmmId: Int?,
    val labelName_ja: String?,
    val labelName_en: String?,
    val labelName_cn: String?,
    val labelName_tw: String?,
    val labelName: String?
)

data class AvmooSeries(
    val seriesId: String?,
    val seriesDmmId: Int?,
    val seriesName_ja: String?,
    val seriesName_en: String?,
    val seriesName_cn: String?,
    val seriesName_tw: String?,
    val seriesName: String?
)

data class FilterMoviesRequest(
    val filter: String? = null,
    val filterType: String? = null,
    val filterId: String? = null,
    val lang: String = "cn",
    val page: Int = 1
)
