package dev.sunnyday.fusiontdd.fusiontddplugin.data.mapper

import dev.sunnyday.fusiontdd.fusiontddplugin.data.response.StarcoderResultDto
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.CodeBlock
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.GenerateCodeBlockResult

internal class StarcoderResponseMapper {

    fun mapResultsToGeneratedCode(results: List<StarcoderResultDto>): GenerateCodeBlockResult {
        return GenerateCodeBlockResult(
            variants = results.map(::mapStarcoderResultToCodeBlock),
        )
    }

    private fun mapStarcoderResultToCodeBlock(result: StarcoderResultDto): CodeBlock {
        if (result.generatedText == null) {
            throw Error(result.error.orEmpty())
        }

        return CodeBlock(result.generatedText)
    }
}
