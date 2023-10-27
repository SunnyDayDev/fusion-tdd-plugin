package dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.steps

import com.google.common.truth.Truth.assertThat
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.CodeBlock
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.GenerateCodeBlockResult
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.service.CodeGenerationService
import dev.sunnyday.fusiontdd.fusiontddplugin.test.executeAndWait
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class GenerateCodeSuggestionsPipelineStepTest {

    private val codeGenerationService = mockk<CodeGenerationService>()
    private val testScope = TestScope(UnconfinedTestDispatcher())

    private val step = GenerateCodeSuggestionsPipelineStep(
        codeGenerationService = codeGenerationService,
        coroutineScope = testScope,
    )

    @Test
    fun `generate code by service`() = testScope.runTest {
        val expectedResult = mockk<GenerateCodeBlockResult>()
        coEvery { codeGenerationService.generate(any()) } returns expectedResult

        val source = mockk<CodeBlock>(relaxed = true)

        val outputResult = step.executeAndWait(source)

        assertThat(outputResult.getOrNull()).isSameInstanceAs(expectedResult)
        coVerify { codeGenerationService.generate(source) }
    }

    @Test
    fun `on error, fail result`() = testScope.runTest {
        val expectedError = Error("Simulated error")
        coEvery { codeGenerationService.generate(any()) } throws expectedError

        val outputResult = step.executeAndWait(mockk(relaxed = true))

        assertThat(outputResult.exceptionOrNull()).isSameInstanceAs(expectedError)
    }
}