package dev.sunnyday.fusiontdd.fusiontddplugin.network.auth

import com.google.common.truth.Truth.assertThat
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Request
import org.junit.jupiter.api.Test

class BearerAuthInterceptorTest {

    @Test
    fun `on intercept, add auth header to request`() {
        val chain = mockk<Interceptor.Chain>(relaxed = true) {
            every { request() } returns Request.Builder().url("https://some.url").build()
        }
        val authTokenProvider = mockk<AuthTokenProvider> {
            every { getAuthToken() } returns "x-auth-token"
        }

        val interceptor = BearerAuthInterceptor(authTokenProvider)
        interceptor.intercept(chain)

        val requestSlot = CapturingSlot<Request>()
        verify { chain.proceed(capture(requestSlot)) }

        assertThat(requestSlot.captured.headers("Authorization"))
            .containsExactly("Bearer x-auth-token")
    }
}