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
    val data: JsonElement?
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

/**
 * 女优资料。字段按接口 `getStar` / `getStars` 的实际返回对齐。
 *
 * ⚠️ 接口返回的字段比这里多，但**很多是不可用的**，不要见字段就往界面上加：
 * - `starName_cn` / `starName_tw`：实测 60/60 全为空串（中/繁名根本没数据）
 * - `blog`：实测 60/60 全为 null
 * - `constellation`（星座）：实测 59/60 是 `0`，只有个别记录有值 → 基本是废数据
 * - `weight`（体重）：虽然有值，但**同一批数据里单位不统一** ——
 *   163cm/108 像「斤」，158cm/58、164cm/50 又像 kg。带单位显示必然有一半是错的。
 * 新增字段前先跑一遍真实接口统计非空率，别照着字段名猜。
 */
data class AvmooStar(
    val starId: String?,
    val starDmmId: Int?,
    val starName: String?,
    val starName_ja: String?,
    val starName_en: String?,
    val starName_cn: String?,
    val starName_tw: String?,
    val avatar: String?,
    val avatarUrl: String?,
    val movieCount: Int?,
    val weight: Int?,
    val birthday: String?,
    val size: JsonElement?,
    // 以下几项是后补的（有默认值，避免影响既有构造调用）
    val bloodType: String? = null,
    val hometown: String? = null,
    val hobby: String? = null,
    val lastReleaseDate: String? = null,
    /** 其中可下载（有磁力源）的部数，恒 <= [movieCount]；三个源实测均 60/60 有值。 */
    val downloadMovieCount: Int? = null
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
