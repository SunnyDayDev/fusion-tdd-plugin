package dev.sunnyday.fusiontdd.fusiontddplugin.data

import com.google.common.truth.Truth.assertThat
import dev.sunnyday.fusiontdd.fusiontddplugin.data.mapper.StarcoderRequestMapper
import dev.sunnyday.fusiontdd.fusiontddplugin.data.mapper.StarcoderResponseMapper
import dev.sunnyday.fusiontdd.fusiontddplugin.data.request.StarcoderRequestDto
import dev.sunnyday.fusiontdd.fusiontddplugin.data.response.StarcoderResultDto
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.CodeBlock
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.GenerateCodeBlockResult
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.StarcoderOptions
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CodeGenerationServiceImplTest {

    // region Setup + Fixtures

    private val requestMapper = mockk<StarcoderRequestMapper>()
    private val responseMapper = mockk<StarcoderResponseMapper>()
    private val starcoderOptionsProvider = mockk<StarcoderOptionsProvider>()
    private val starcoderApi = mockk<StarcoderApi>()

    private val generationService = CodeGenerationServiceImpl(
        starcoderApi = starcoderApi,
        starcoderOptionsProvider = starcoderOptionsProvider,
        requestMapper = requestMapper,
        responseMapper = responseMapper,
    )

    private val source = mockk<CodeBlock>(relaxed = true)
    private val mappedRequest = mockk<StarcoderRequestDto>()
    private val receivedResponse = mockk<List<StarcoderResultDto>>()
    private val mappedReceivedResult = mockk<GenerateCodeBlockResult>()

    @BeforeEach
    fun setUp() {
        every { starcoderOptionsProvider.getStarcoderOptions() } returns mockk()
        every { requestMapper.mapCodeBlockToRequest(any(), any()) } returns mappedRequest
        coEvery { starcoderApi.generate(any()) } returns receivedResponse
        every { responseMapper.mapResultsToGeneratedCode(any()) } returns mappedReceivedResult
    }

    // endregion

    // region Specification

    @Test
    fun `on generate, map source to request by mapper`() = runTest {
        generationService.generate(source)

        verify { requestMapper.mapCodeBlockToRequest(source, any()) }
    }

    @Test
    fun `on generate, map provided options to request`() = runTest {
        val options = mockk<StarcoderOptions>()
        every { starcoderOptionsProvider.getStarcoderOptions() } returns options

        generationService.generate(source)

        verify { requestMapper.mapCodeBlockToRequest(any(), refEq(options)) }
    }

    @Test
    fun `on generate, request generation from Starcoder api`() = runTest {
        generationService.generate(source)

        coVerify { starcoderApi.generate(mappedRequest) }
    }

    @Test
    fun `on generate, map response dto to generated code model`() = runTest {
        generationService.generate(source)

        verify { responseMapper.mapResultsToGeneratedCode(receivedResponse) }
    }

    @Test
    fun `on success generate, return received generated code`() = runTest {
        val result = generationService.generate(source)

        assertThat(result).isSameInstanceAs(mappedReceivedResult)
    }

    // endregion
}