package dev.sunnyday.fusiontdd.fusiontddplugin.data

import dev.sunnyday.fusiontdd.fusiontddplugin.data.mapper.StarcoderRequestMapper
import dev.sunnyday.fusiontdd.fusiontddplugin.data.mapper.StarcoderResponseMapper
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.CodeBlock
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.GenerateCodeBlockResult
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.service.CodeGenerationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class CodeGenerationServiceImpl(
    private val starcoderApi: StarcoderApi,
    private val starcoderOptionsProvider: StarcoderOptionsProvider,
    private val requestMapper: StarcoderRequestMapper = StarcoderRequestMapper(),
    private val responseMapper: StarcoderResponseMapper = StarcoderResponseMapper(),
) : CodeGenerationService {

    override suspend fun generate(
        input: CodeBlock,
    ): GenerateCodeBlockResult = withContext(Dispatchers.Default) {
        val request = requestMapper.mapCodeBlockToRequest(
            code = input,
            options = starcoderOptionsProvider.getStarcoderOptions(),
        )

        val response = withContext(Dispatchers.IO) {
            starcoderApi.generate(request)
        }

        responseMapper.mapResultsToGeneratedCode(response)
    }
}