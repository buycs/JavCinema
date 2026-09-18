package io.github.javcinema.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryPolicyTest {

    @Test
    fun getRetryableStatus_retries() {
        listOf(429, 502, 503, 504).forEach { code ->
            assertTrue(RetryInterceptor.shouldRetry("GET", code, false, false, 0))
        }
    }

    @Test
    fun thirdAttempt_doesNotRetry() {
        assertFalse(RetryInterceptor.shouldRetry("GET", 503, false, false, 2))
    }

    @Test
    fun getIoFailure_retries() {
        assertTrue(RetryInterceptor.shouldRetry("GET", null, true, false, 0))
    }

    @Test
    fun postIoFailure_doesNotRetry() {
        assertFalse(RetryInterceptor.shouldRetry("POST", null, true, false, 0))
    }

    @Test
    fun getHttp500_doesNotRetry() {
        assertFalse(RetryInterceptor.shouldRetry("GET", 500, false, false, 0))
    }

    @Test
    fun canceled_doesNotRetry() {
        assertFalse(RetryInterceptor.shouldRetry("GET", 503, true, true, 0))
    }
}
