package dev.sunnyday.fusiontdd.fusiontddplugin.data.mapper

import com.google.common.truth.Truth.assertThat
import dev.sunnyday.fusiontdd.fusiontddplugin.data.request.StarcoderRequestDto
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.CodeBlock
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.StarcoderOptions
import io.mockk.mockk
import org.junit.jupiter.api.Test

class StarcoderRequestMapperTest {

    private val mapper = StarcoderRequestMapper()

    private val optionsStub = mockk<StarcoderOptions>(relaxed = true)

    @Test
    fun `map CodeBlock with 'generate here' placeholder`() {
        val codeBlock = CodeBlock(
            """
            class Example {
                fun execute() {
                    ${CodeBlock.GENERATE_HERE_TAG}
                }
            }
            """.trimIndent()
        )

        val request = mapper.mapCodeBlockToRequest(codeBlock, optionsStub)

        assertThat(request.inputs).isEqualTo(
            """
            <fim_prefix>
            class Example {
                fun execute() {
            <fim_suffix>
                }
            }
            <fim_middle>
            """.trimIndent()
        )
    }

    @Test
    fun `map CodeBlock with 'generate here' placed at the end`() {
        val codeBlock = CodeBlock(
            """
            class Example()
            ${CodeBlock.GENERATE_HERE_TAG}
            """.trimIndent()
        )

        val request = mapper.mapCodeBlockToRequest(codeBlock, optionsStub)

        assertThat(request.inputs).isEqualTo(
            """
            <fim_prefix>
            class Example()
            <fim_suffix>
            <fim_middle>
            """.trimIndent()
        )
    }

    @Test
    fun `map CodeBlock with 'generate here' placed at the start`() {
        val codeBlock = CodeBlock(
            """
            ${CodeBlock.GENERATE_HERE_TAG}
            class Example()
            """.trimIndent()
        )

        val request = mapper.mapCodeBlockToRequest(codeBlock, optionsStub)

        assertThat(request.inputs).isEqualTo(
            """
            <fim_prefix>
            <fim_suffix>
            class Example()
            <fim_middle>
            """.trimIndent()
        )
    }

    @Test
    fun `map CodeBlock without 'generate here' placeholder`() {
        val codeBlock = CodeBlock(
            """
            class Example()
            """.trimIndent()
        )

        val request = mapper.mapCodeBlockToRequest(codeBlock, optionsStub)

        assertThat(request.inputs).isEqualTo(
            """
            <fim_prefix>
            class Example()
            <fim_suffix>
            <fim_middle>
            """.trimIndent()
        )
    }

    @Test
    fun `map starcoder options`() {
        val options = StarcoderOptions(
            maxNewTokens = 377,
            temperature = 0.87f,
            doSample = false,
            useCache = true,
            waitForModel = false,
        )

        val request = mapper.mapCodeBlockToRequest(CodeBlock(""), options)

        assertThat(request.options).isEqualTo(
            StarcoderRequestDto.Options(
                useCache = true,
                waitForModel = false,
            )
        )

        assertThat(request.parameters).isEqualTo(
            StarcoderRequestDto.Parameters(
                maxNewTokens = 377,
                temperature = 0.87f,
                doSample = false,
            )
        )
    }
}