package dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.steps

import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import com.intellij.testFramework.runInEdtAndWait
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.CodeBlock
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.dialog.ModifySourceCodeDialog
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings.FusionTDDSettings
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.util.PipelineCancellationException
import dev.sunnyday.fusiontdd.fusiontddplugin.test.executeAndWait
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class ConfirmGenerationSourcePipelineStepTest : LightJavaCodeInsightFixtureTestCase5() {

    private val settings = mockk<FusionTDDSettings>()
    private val dialog = mockk<ModifySourceCodeDialog>(relaxed = true)

    override fun getTestDataPath(): String = "testdata"

    @Test
    fun `on execute, if modify is disabled in settings do nothing`() {
        every { settings.isConfirmSourceBeforeGeneration } returns false

        val source = CodeBlock("2 + 2")
        val step = ConfirmGenerationSourcePipelineStep(settings, ::dialog)

        val result = step.executeAndWait(source)

        assertThat(result.getOrNull()).isEqualTo(source)
    }

    @Test
    fun `on execute, show confirm dialog and get result from it`() = runInEdtAndWait {
        every { settings.isConfirmSourceBeforeGeneration } returns true
        every { dialog.showAndGet() } returns true
        every { dialog.getCodeBlock() } returns "3 * 3"

        val step = ConfirmGenerationSourcePipelineStep(settings, ::dialog)

        val result = step.executeAndWait(CodeBlock("2 + 2"))

        assertThat(result.getOrNull()).isEqualTo(CodeBlock("3 * 3"))
    }

    @Test
    fun `on execute, if confirm dialog cancelled, cancel pipeline`() = runInEdtAndWait {
        every { settings.isConfirmSourceBeforeGeneration } returns true
        every { dialog.showAndGet() } returns false

        val step = ConfirmGenerationSourcePipelineStep(settings, ::dialog)

        val result = step.executeAndWait(CodeBlock("2 + 2"))

        assertThat(result.exceptionOrNull()).isInstanceOf(PipelineCancellationException::class.java)
    }
}