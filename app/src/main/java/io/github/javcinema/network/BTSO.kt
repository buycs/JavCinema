package io.github.javcinema.network

import io.github.javcinema.JAViewer
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

interface BTSO {

    companion object {
        const val BASE_URL = "https://btsow.pics"

        private var _instance: BTSO? = null

        val INSTANCE: BTSO get() {
            if (_instance == null) {
                _instance = create(BASE_URL)
            }
            return _instance!!
        }

        fun recreate(baseUrl: String = BASE_URL) {
            _instance = create(baseUrl)
        }

        private fun create(baseUrl: String): BTSO {
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(JAViewer.HTTP_CLIENT)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(BTSO::class.java)
        }
    }

    @POST("/bts/data/api/search")
    suspend fun search(@Body body: RequestBody): BTSOSearchResponse

    @POST("/bts/data/api/magnet")
    suspend fun getMagnet(@Body body: RequestBody): BTSOMagnetResponse
}

data class BTSOSearchResponse(
    val code: Int = 0,
    val data: List<BTSOSearchItem> = emptyList()
)

data class BTSOSearchItem(
    val hash: String = "",
    val name: String = "",
    val size: Long = 0,
    val lastUpdateTime: Long = 0
)

data class BTSOMagnetResponse(
    val code: Int = 0,
    val data: BTSOMagnetData? = null
)

data class BTSOMagnetData(
    val files: List<BTSOFile> = emptyList()
)

data class BTSOFile(
    val filename: String = "",
    val size: Long = 0
)
