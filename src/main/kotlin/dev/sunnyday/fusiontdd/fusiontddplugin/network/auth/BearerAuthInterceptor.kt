package dev.sunnyday.fusiontdd.fusiontddplugin.network.auth

import io.ktor.http.*
import okhttp3.Interceptor
import okhttp3.Response

internal class BearerAuthInterceptor(private val tokenProvider: AuthTokenProvider) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header(HttpHeaders.Authorization, "Bearer ${tokenProvider.getAuthToken()}")
            .build()

        return chain.proceed(request)
    }
}