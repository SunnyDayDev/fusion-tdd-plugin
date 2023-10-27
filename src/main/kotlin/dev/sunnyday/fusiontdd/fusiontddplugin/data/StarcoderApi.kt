package dev.sunnyday.fusiontdd.fusiontddplugin.data

import dev.sunnyday.fusiontdd.fusiontddplugin.data.request.StarcoderRequestDto
import dev.sunnyday.fusiontdd.fusiontddplugin.data.response.StarcoderResultDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

internal class StarcoderApi(
    private val client: HttpClient,
    private val starcoderModelProvider: () -> String,
) {

    suspend fun generate(request: StarcoderRequestDto): List<StarcoderResultDto> = client.run {
        val response = client.post(getStarcoderUrl()) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        return response.body()
    }

    private fun getStarcoderUrl(): String {
        return "$BASE_STARCODER_URL${starcoderModelProvider.invoke()}"
    }

    private companion object {

        const val BASE_STARCODER_URL = "https://api-inference.huggingface.co/models/bigcode/"
    }
}