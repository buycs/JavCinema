package io.github.javcinema.network.provider

import io.github.javcinema.ImageUrls
import io.github.javcinema.JavCinema
import io.github.javcinema.data.model.Actress
import io.github.javcinema.data.model.AvmooMovie
import io.github.javcinema.data.model.AvmooMovieDetail
import io.github.javcinema.data.model.Genre
import io.github.javcinema.data.model.Movie
import io.github.javcinema.data.model.MovieDetail
import io.github.javcinema.data.model.Screenshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object AVMOProvider {

    suspend fun parseMovies(html: String): List<Movie> = withContext(Dispatchers.IO) {
        val document = Jsoup.parse(html)

        document.select("a[class*=movie-box]").map { box ->
            val img = box.select("div.photo-frame > img").first()!!
            val span = box.select("div.photo-info > span").first()!!
            val date = span.select("date")

            Movie.create(
                img.attr("title"),
                date[0].text(),
                date[1].text(),
                img.attr("src"),
                box.attr("href"),
                span.getElementsByTag("i").size > 0
            )
        }
    }

    suspend fun parseActresses(html: String): List<Actress> = withContext(Dispatchers.IO) {
        val document = Jsoup.parse(html)

        document.select("a[class*=avatar-box]").map { box ->
            val img = box.select("div.photo-frame > img").first()!!
            val span = box.select("div.photo-info > span").first()!!

            Actress.create(
                span.text(),
                img.attr("src"),
                box.attr("href")
            )
        }
    }

    suspend fun parseMoviesDetail(html: String): MovieDetail = withContext(Dispatchers.IO) {
        val document = Jsoup.parse(html)
        val movie = MovieDetail()

        //General Parsing
        movie.title = document.select("div.container > h3").first()!!.text()
        movie.coverUrl = document.select("[class=bigImage]").first()!!.attr("href")

        //Parsing Screenshots
        for (box in document.select("[class*=sample-box]")) {
            movie.screenshots.add(
                Screenshot.create(
                    box.getElementsByTag("img").first()!!.attr("src"),
                    box.attr("href")
                )
            )
        }

        //Parsing Actresses
        for (box in document.select("[class*=avatar-box]")) {
            movie.actresses.add(
                Actress.create(
                    box.text(),
                    box.getElementsByTag("img").first()!!.attr("src"),
                    box.attr("href")
                )
            )
        }

        //Parsing Headers
        val info = document.select("div.info").first()
        if (info != null) {
            for (p in info.select("p:not([class*=header]):has(span:not([class=genre]))")) {
                val strings = p.text().split(":")
                movie.headers.add(
                    MovieDetail.Header.create(
                        strings[0].trim(),
                        if (strings.size > 1) strings[1].trim() else "",
                        null
                    )
                )
            }

            val headerNames = mutableListOf<String>()
            val headerAttr = mutableListOf<Array<String>>()

            for (p in info.select("p[class*=header]")) {
                headerNames.add(p.text().replace(":", ""))
            }

            for (a in info.select("p > a")) {
                headerAttr.add(arrayOf(a.text(), a.attr("href")))
            }

            for (i in 0 until minOf(headerNames.size, headerAttr.size)) {
                movie.headers.add(
                    MovieDetail.Header.create(
                        headerNames[i],
                        headerAttr[i][0].trim(),
                        headerAttr[i][1].trim()
                    )
                )
            }

            for (a in info.select("* > [class=genre] > a")) {
                movie.genres.add(
                    Genre.create(
                        a.text(),
                        a.attr("href")
                    )
                )
            }
        }
        movie
    }

    suspend fun parseGenres(html: String): Map<String, List<Genre>> = withContext(Dispatchers.IO) {
        val container = Jsoup.parse(html).getElementsByClass("pt-10").first()!!
        val keys = container.getElementsByTag("h4").map { it.text() }
        val genres = container.getElementsByClass("genre-box").map { element ->
            element.getElementsByTag("a").map { e ->
                Genre.create(e.text(), e.attr("href"))
            }
        }
        val map = linkedMapOf<String, List<Genre>>()
        for (i in keys.indices) {
            map[keys[i]] = genres[i]
        }
        map
    }

    fun fromApiList(apiMovies: List<AvmooMovie>): List<Movie> {
        return apiMovies.map { api ->
            api.movieId?.let { id ->
                JavCinema.imageUrlsRegistry[id] = ImageUrls(
                    posterSmall = api.posterSmall,
                    posterLarge = api.posterLarge,
                    sampleSmall = api.sampleSmall,
                    sampleLarge = api.sampleLarge
                )
            }
            Movie().apply {
                id = api.movieId
                code = api.movieFanHao
                title = api.title ?: api.title_ja ?: api.title_cn ?: api.movieFanHao
                coverUrl = api.posterSmall
                date = api.releaseDate
                link = api.movieId
            }
        }
    }

    fun fromApiDetail(api: AvmooMovieDetail): MovieDetail {
        val detail = MovieDetail()
        detail.title = api.title ?: api.title_ja ?: api.title_cn ?: api.movieFanHao
        detail.coverUrl = api.posterLarge ?: api.posterSmall
        detail.code = api.movieFanHao
        detail.id = api.movieId
        detail.btsSearchUrl = api.btsSearchUrl

        api.sampleSmall?.forEachIndexed { index, smallUrl ->
            val largeUrl = api.sampleLarge?.getOrNull(index) ?: smallUrl
            detail.screenshots.add(Screenshot.create(smallUrl, largeUrl))
        }

        api.star?.forEach { star ->
            val name = star.starName_ja ?: star.starName_en ?: star.starName_cn ?: ""
            val avatar = star.avatarUrl ?: star.avatar ?: ""
            detail.actresses.add(Actress.create(name, avatar, star.starId ?: ""))
        }

        api.genre?.forEach { g ->
            val name = g.genreName ?: g.genreName_ja ?: g.genreName_cn ?: ""
            detail.genres.add(Genre.create(name, g.genreId ?: ""))
        }

        api.releaseDate?.let { date ->
            detail.headers.add(MovieDetail.Header.create("发行日期", date, null))
        }
        api.length?.let { len ->
            detail.headers.add(MovieDetail.Header.create("影片时长", "${len}分钟", null))
        }
        api.director?.let { director ->
            val name = director.directorName ?: director.directorName_ja ?: ""
            detail.headers.add(MovieDetail.Header.create("导演", name, director.directorId))
        }
        api.studio?.let { studio ->
            val name = studio.studioName ?: studio.studioName_ja ?: studio.studioName_en ?: ""
            detail.headers.add(MovieDetail.Header.create("制作商", name, studio.studioId))
        }
        api.label?.let { label ->
            val name = label.labelName ?: label.labelName_ja ?: ""
            detail.headers.add(MovieDetail.Header.create("发行商", name, label.labelId))
        }
        api.series?.let { series ->
            val name = series.seriesName ?: series.seriesName_ja ?: ""
            detail.headers.add(MovieDetail.Header.create("系列", name, series.seriesId))
        }

        return detail
    }
}
