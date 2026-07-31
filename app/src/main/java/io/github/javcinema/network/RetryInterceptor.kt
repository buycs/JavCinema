package io.github.javcinema.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class RetryInterceptor(
    private val maxRetries: Int = 2,
    private val baseDelayMs: Long = 500
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var attempt = 0
        while (true) {
            val request = chain.request()
            val response = try {
                chain.proceed(request)
            } catch (e: IOException) {
                if (attempt >= maxRetries) throw e
                attempt++
                Log.w("RetryInterceptor", "IOException retry ${attempt}/${maxRetries}: ${request.url} - ${e.message}")
                Thread.sleep(baseDelayMs * attempt)
                continue
            }

            if (response.code in RETRYABLE_STATUS && attempt < maxRetries) {
                response.close()
                attempt++
                Log.w("RetryInterceptor", "HTTP ${response.code} retry ${attempt}/${maxRetries}: ${request.url}")
                Thread.sleep(baseDelayMs * attempt)
                continue
            }
            return response
        }
    }

    companion object {
        private val RETRYABLE_STATUS = setOf(502, 503, 504, 429)
    }
}
