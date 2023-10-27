package dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.steps

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.CodeBlock
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.GenerateCodeBlockResult
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.psi.findKotlinClass
import dev.sunnyday.fusiontdd.fusiontddplugin.test.executeAndWait
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.findFunctionByName
import org.junit.jupiter.api.Test

class ReplaceFunctionBodyPipelineStepTest : LightJavaCodeInsightFixtureTestCase5() {

    override fun getTestDataPath(): String = "testdata"

    @Test
    fun `replace fun body with generated code`() {
        fixture.copyFileToProject("replace/TestClassWithFun.kt")

        val function = runReadAction(::getTestFunction)
        val generationResult = GenerateCodeBlockResult(
            variants = listOf(
                CodeBlock(
                    """
                        val x = 2 * 2
                        println("replaced")
                    """.trimIndent()
                )
            )
        )

        val step = ReplaceFunctionBodyPipelineStep(function)

        val actualFun = step.executeAndWait(generationResult).getOrNull()
        val functionText = runReadAction { actualFun?.text.orEmpty() }

        assertThat(functionText).isEqualTo(
            """
                fun doSomething() {
                        val x = 2 * 2
                        println("replaced")
                    }
            """.trimIndent()
        )
    }

    private fun getTestFunction(): KtNamedFunction {
        val klass = fixture.javaFacade.findKotlinClass("TestClassWithFun")
            ?: error("Can't find required class")

        return klass.findFunctionByName("doSomething") as? KtNamedFunction
            ?: error("Can't find required function")
    }
}