package dev.sunnyday.fusiontdd.fusiontddplugin.network

import dev.sunnyday.fusiontdd.fusiontddplugin.network.auth.AuthTokenProvider
import dev.sunnyday.fusiontdd.fusiontddplugin.network.auth.BearerAuthInterceptor
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

internal object KtorClientFactory {

    fun createClient(authTokenProvider: AuthTokenProvider): HttpClient {
        return HttpClient(OkHttp) {
            engine {
                config {
                    followRedirects(false)

                    connectTimeout(1.minutes.toJavaDuration())
                    readTimeout(1.minutes.toJavaDuration())

                    addInterceptor(BearerAuthInterceptor(authTokenProvider))
                }
            }

            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.NONE
            }

            install(ContentNegotiation) {
                json(Json {
                    encodeDefaults = true
                    ignoreUnknownKeys = true
                })
            }
        }
    }
}