package dev.sunnyday.fusiontdd.fusiontddplugin.network

import dev.sunnyday.fusiontdd.fusiontddplugin.network.auth.AuthTokenProvider
import dev.sunnyday.fusiontdd.fusiontddplugin.network.auth.BearerAuthInterceptor
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

internal object KtorClientFactory {

    fun createClient(authTokenProvider: AuthTokenProvider? = null): HttpClient {
        return HttpClient(OkHttp) {
            engine {
                config {
                    followRedirects(false)

                    if (authTokenProvider != null) {
                        addInterceptor(BearerAuthInterceptor(authTokenProvider))
                    }
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