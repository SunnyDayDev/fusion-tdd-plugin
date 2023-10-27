package dev.sunnyday.fusiontdd.fusiontddplugin.data.mapper

import com.google.common.truth.Truth.assertThat
import dev.sunnyday.fusiontdd.fusiontddplugin.data.response.StarcoderResultDto
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.CodeBlock
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.GenerateCodeBlockResult
import org.junit.jupiter.api.Test

class StarcoderResponseMapperTest {

    private val mapper = StarcoderResponseMapper()

    @Test
    fun `map starcoder results to generated code results`() {
        val generatedText = "class Example()"
        val resultDto = StarcoderResultDto(generatedText = generatedText)

        val generatedResult = mapper.mapResultsToGeneratedCode(listOf(resultDto))

        assertThat(generatedResult).isEqualTo(
            GenerateCodeBlockResult(
                variants = listOf(
                    CodeBlock(generatedText)
                )
            )
        )
    }
}