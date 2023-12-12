package dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.steps

import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.CodeBlock
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.GenerateCodeBlockResult
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.dialog.ModifySourceCodeDialog
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.util.PipelineCancellationException
import dev.sunnyday.fusiontdd.fusiontddplugin.test.emptyObserver
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture

class FixGenerationResultPipelineStepTest : LightJavaCodeInsightFixtureTestCase5() {

    override fun getTestDataPath() = "testdata"

    @Test
    fun `on execute, show dialog with proper title and description`() {
        // arrange
        val modifyDialog = mockk<ModifySourceCodeDialog>(relaxed = true)
        val step = createStep(modifyDialog)

        // act
        step.execute(createResult(""), emptyObserver())

        // assert
        verify {
            modifyDialog.title = "Fix Generated Result"
            modifyDialog.setDescription("The generated result can't be applied to the function, fix it:")
        }
    }

    @Test
    fun `on execute, show fix dialog with code as input`() {
        // arrange
        val modifyDialog = mockk<ModifySourceCodeDialog>(relaxed = true)
        val step = createStep(modifyDialog)

        // act
        step.execute(createResult("broken code"), emptyObserver())

        // assert
        verify {
            // that shown
            modifyDialog.showAndGet()
            // with correct code block
            modifyDialog.setCodeBlock("broken code")
        }
    }

    @Test
    fun `on execute with empty variants, show fix dialog with empty input`() {
        // arrange
        val modifyDialog = mockk<ModifySourceCodeDialog>(relaxed = true)
        val step = createStep(modifyDialog)

        // act
        step.execute(GenerateCodeBlockResult(emptyList()), emptyObserver())

        // assert
        // that shown
        verify { modifyDialog.showAndGet() }
        // with correct input
        verify { modifyDialog.setCodeBlock("") }
    }

    @Test
    fun `on dialog ok, use current input as result`() {
        // arrange
        val modifyDialog = mockk<ModifySourceCodeDialog>(relaxed = true) {
            every { showAndGet() } returns true
            every { getCodeBlock() } returns "modified code"
        }
        val step = createStep(modifyDialog)
        val futureResult = CompletableFuture<Result<GenerateCodeBlockResult>>()

        // act
        step.execute(createResult(), futureResult::complete)
        val result = futureResult.get()

        // assert
        assertThat(result.getOrNull()).isEqualTo(createResult("modified code"))
    }

    @Test
    fun `on dialog cancel, use cancellation as result`() {
        // arrange
        val modifyDialog = mockk<ModifySourceCodeDialog>(relaxed = true) {
            every { showAndGet() } returns false
        }
        val step = createStep(modifyDialog)
        val futureResult = CompletableFuture<Result<GenerateCodeBlockResult>>()

        // act
        step.execute(createResult(), futureResult::complete)
        val result = futureResult.get()

        // assert
        assertThat(result.exceptionOrNull()).isInstanceOf(PipelineCancellationException::class.java)
    }

    private fun createStep(dialog: ModifySourceCodeDialog): FixGenerationResultPipelineStep {
        return FixGenerationResultPipelineStep { dialog }
    }

    private fun createResult(code: String = "some code"): GenerateCodeBlockResult {
        return GenerateCodeBlockResult(listOf(CodeBlock(code)))
    }
}