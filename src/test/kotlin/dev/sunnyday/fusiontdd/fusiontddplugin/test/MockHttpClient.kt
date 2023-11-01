package dev.sunnyday.fusiontdd.fusiontddplugin.test

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

object MockHttpClient {

    fun buildClientMock(builder: HttpClientBuilder.() -> Unit): HttpClient {
        val engine = MockEngine.invoke { request ->
            val handlers =
                mutableMapOf<String, suspend MockRequestHandleScope.(request: HttpRequestData) -> HttpResponseData>()

            builder.invoke(
                object : HttpClientBuilder {
                    override fun intercept(
                        url: String,
                        intercept: suspend MockRequestHandleScope.(request: HttpRequestData) -> HttpResponseData
                    ) {
                        handlers[url] = intercept
                    }
                }
            )

            handlers[request.url.toString()]
                ?.let { intercept -> intercept(request) }
                ?: respondBadRequest()
        }

        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json {
                    encodeDefaults = true
                    ignoreUnknownKeys = true
                })
            }
        }
    }

    interface HttpClientBuilder {

        fun intercept(
            url: String,
            intercept: suspend MockRequestHandleScope.(request: HttpRequestData) -> HttpResponseData
        )
    }

    operator fun invoke(builder: HttpClientBuilder.() -> Unit) = buildClientMock(builder)
}

fun MockRequestHandleScope.respondJson(
    content: String,
): HttpResponseData {
    return respond(
        headers = Headers.build {
            set("Content-Type", "application/json")
        },
        content = content,
    )
}