package dev.sunnyday.fusiontdd.fusiontddplugin.data

import com.google.common.truth.Truth.assertThat
import dev.sunnyday.fusiontdd.fusiontddplugin.data.request.StarcoderRequestDto
import dev.sunnyday.fusiontdd.fusiontddplugin.data.response.StarcoderResultDto
import dev.sunnyday.fusiontdd.fusiontddplugin.test.MockHttpClient
import dev.sunnyday.fusiontdd.fusiontddplugin.test.respondJson
import io.ktor.client.engine.mock.*
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class StarcoderApiTest {

    @Test
    fun `generate suggestion`() = runTest(UnconfinedTestDispatcher()) {
        var requestBodyString: String? = null
        val client = MockHttpClient {
            intercept("https://api-inference.huggingface.co/models/bigcode/superstarcoder") { request ->
                requestBodyString = String(request.body.toByteArray())

                respondJson(
                    content = """
                       [{"generated_text": "return 2 + 2"}]
                    """.trimIndent()
                )
            }
        }
        val api = StarcoderApi(client, starcoderModelProvider = { "superstarcoder" })

        val actualResult = api.generate(
            StarcoderRequestDto(
                inputs = "class Class {}",
            )
        )

        assertThat(requestBodyString)
            .isEqualTo(
                """
                    {"parameters":{
                    "max_new_tokens":500,
                    "temperature":0.5,
                    "return_full_text":false,
                    "num_return_sequences":3,
                    "do_sample":true},
                    "options":{
                    "use_cache":false,
                    "wait_for_model":false},
                    "inputs":"class Class {}"}
                """.trimIndent().replace("\n", "")
            )

        assertThat(actualResult).containsExactly(
            StarcoderResultDto(
                generatedText = "return 2 + 2",
            )
        )
    }
}