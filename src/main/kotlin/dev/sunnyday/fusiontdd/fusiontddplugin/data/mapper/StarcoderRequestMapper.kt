package dev.sunnyday.fusiontdd.fusiontddplugin.data.mapper

import dev.sunnyday.fusiontdd.fusiontddplugin.data.request.StarcoderRequestDto
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.CodeBlock
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.StarcoderOptions

internal class StarcoderRequestMapper {

    fun mapCodeBlockToRequest(code: CodeBlock, options: StarcoderOptions): StarcoderRequestDto {
        val inputs = buildInputsString(code)
        val requestParameters = mapParameters(options)
        val requestOptions = mapOptions(options)

        return StarcoderRequestDto(
            inputs = inputs,
            parameters = requestParameters,
            options = requestOptions,
        )
    }

    private fun buildInputsString(input: CodeBlock): String {
        val inputWithSuffixTag = input.rawText.replace(
            Regex(" *${CodeBlock.GENERATE_HERE_TAG} *"),
            SUFFIX_TAG,
        )

        return buildString {
            appendLine(PREFIX_TAG)
            append(inputWithSuffixTag)

            if (!endsWith('\n')) {
                appendLine()
            }

            if (inputWithSuffixTag.length == input.rawText.length) {
                appendLine(SUFFIX_TAG)
            }

            append(MIDDLE_TAG)
        }
    }

    private fun mapParameters(options: StarcoderOptions): StarcoderRequestDto.Parameters {
        return StarcoderRequestDto.Parameters(
            maxNewTokens = options.maxNewTokens,
            temperature = options.temperature,
            doSample = options.doSample,
        )
    }

    private fun mapOptions(options: StarcoderOptions): StarcoderRequestDto.Options {
        return StarcoderRequestDto.Options(
            useCache = options.useCache,
            waitForModel = options.waitForModel,
        )
    }

    private companion object {

        const val PREFIX_TAG = "<fim_prefix>"
        const val SUFFIX_TAG = "<fim_suffix>"
        const val MIDDLE_TAG = "<fim_middle>"
    }
}
