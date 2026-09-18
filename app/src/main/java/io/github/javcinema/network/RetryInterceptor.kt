package io.github.javcinema.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class RetryInterceptor(
    private val maxRetries: Int = 2
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var retryCount = 0
        while (true) {
            val request = chain.request()
            val response = try {
                chain.proceed(request)
            } catch (e: IOException) {
                val canceled = chain.call().isCanceled() ||
                    e.message?.contains("Canceled", ignoreCase = true) == true
                if (!shouldRetry(request.method, null, true, canceled, retryCount, maxRetries)) {
                    throw e
                }
                retryCount++
                Log.w("RetryInterceptor", "IOException retry $retryCount/$maxRetries: ${request.url} - ${e.message}")
                continue
            }

            if (shouldRetry(
                    request.method,
                    response.code,
                    false,
                    chain.call().isCanceled(),
                    retryCount,
                    maxRetries
                )
            ) {
                response.close()
                retryCount++
                Log.w("RetryInterceptor", "HTTP ${response.code} retry $retryCount/$maxRetries: ${request.url}")
                continue
            }
            return response
        }
    }

    companion object {
        private val RETRYABLE_STATUS = setOf(429, 502, 503, 504)

        fun shouldRetry(
            method: String,
            httpCode: Int?,
            ioFailed: Boolean,
            canceled: Boolean,
            retryCount: Int,
            maxRetries: Int = 2
        ): Boolean {
            if (canceled) return false
            if (retryCount >= maxRetries) return false
            if (!method.equals("GET", ignoreCase = true)) return false
            if (ioFailed) return true
            return httpCode != null && httpCode in RETRYABLE_STATUS
        }
    }
}
