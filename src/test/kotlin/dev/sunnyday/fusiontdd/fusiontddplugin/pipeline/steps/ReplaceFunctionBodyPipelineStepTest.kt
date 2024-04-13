package dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.steps

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.CodeBlock
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.GenerateCodeBlockResult
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.psi.findKotlinClass
import dev.sunnyday.fusiontdd.fusiontddplugin.test.executeAndWait
import dev.sunnyday.fusiontdd.fusiontddplugin.test.getClassFunction
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
                    |        val x = 2 * 2
                    |        println("replaced")
                    """.trimMargin()
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

    @Test
    fun `on result with multiple functions, correctly replace it`() {
        val file = fixture.addFileToProject(
            "Replace.kt", """
                class Replace {
                
                    fun targetFunction() = Unit
                
                }
            """.trimIndent()
        )
        val generationResult = GenerateCodeBlockResult(
            variants = listOf(
                CodeBlock(
                    """
                    |        subFunction()
                    |    }
                    |    
                    |    fun subFunction() {
                    |        println("replaced")
                    """.trimMargin()
                )
            )
        )

        val step = ReplaceFunctionBodyPipelineStep(
            targetFunction = runReadAction { fixture.getClassFunction("Replace.targetFunction") },
        )

        val result = step.executeAndWait(generationResult)

        assertThat(result.isSuccess).isTrue()

        val actualFileText = runReadAction { file.text }

        assertThat(actualFileText).isEqualTo(
            """
                class Replace {
                
                    fun targetFunction() {
                        subFunction()
                    }
                
                    fun subFunction() {
                        println("replaced")
                    }
                
                }
            """.trimIndent()
        )
    }

    @Test
    fun `on replace, keep spaces`() {
        val file = fixture.addFileToProject(
            "Replace.kt", """
                class Replace {
                    fun functionBefore() = Unit
                
                
                    fun targetFunction() = Unit
                
                
                    fun functionAfter() = Unit
                }
            """.trimIndent()
        )
        val generationResult = GenerateCodeBlockResult(
            variants = listOf(
                CodeBlock(
                    """
                    |        println("replaced")
                    """.trimMargin()
                )
            )
        )

        val step = ReplaceFunctionBodyPipelineStep(
            targetFunction = runReadAction {
                fixture.getClassFunction("Replace.targetFunction") },
        )

        val result = step.executeAndWait(generationResult)

        assertThat(result.isSuccess).isTrue()

        val actualFileText = runReadAction { file.text }

        assertThat(actualFileText).isEqualTo(
            """
                class Replace {
                    fun functionBefore() = Unit
                
                
                    fun targetFunction() {
                        println("replaced")
                    }
                
                
                    fun functionAfter() = Unit
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