package io.github.javcinema.data.model

import com.google.gson.annotations.SerializedName

/**
 * Project: JavCinema
 */
class AvgleSearchResult {

    var success: Boolean = false
    var response: Response? = null

    class Response {

        var has_more: Boolean = false
        var total_videos: Int = 0
        var current_offset: Int = 0
        var limit: Int = 0
        var videos: List<Video>? = null

        class Video {
            var title: String? = null
            var keyword: String? = null
            var channel: String? = null
            var duration: Double = 0.0
            var framerate: Double = 0.0
            var hd: Boolean = false
            var addtime: Int = 0
            var viewnumber: Int = 0
            var likes: Int = 0
            var dislikes: Int = 0
            var video_url: String? = null
            var embedded_url: String? = null
            var preview_url: String? = null
            var preview_video_url: String? = null
            @SerializedName("public")
            var isPublic: Boolean = false
            var vid: String? = null
            var uid: String? = null
        }
    }
}
