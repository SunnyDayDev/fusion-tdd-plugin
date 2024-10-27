package dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.steps

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.CodeBlock
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.GenerateCodeBlockResult
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.psi.findKotlinClass
import dev.sunnyday.fusiontdd.fusiontddplugin.test.*
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.findFunctionByName
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@Suppress("ClassName")
@ExtendWith(TestSteps::class)
class ReplaceFunctionBodyPipelineStepTest : LightJavaCodeInsightFixtureTestCase5() {

    // region Definitions

    override fun getTestDataPath(): String = "testdata"

    // endregion

    @Nested
    inner class `on execute for function target element` {

        @Test
        fun `replace fun body with generated code`() {
            fixture.copyFileToProject("replace/TestClassWithFun.kt")

            val function = runReadAction(::getTestFunction)
            val generationResult = createGenerateCodeBlockResult(
                """
                |        val x = 2 * 2
                |        println("replaced")
                """.trimMargin()
            )

            val step = createReplacePipelineStep(function)

            val actualFun = step.executeAndWait(generationResult).getOrNull()
            val functionText = runReadAction { actualFun?.text.orEmpty() }

            assertThat(functionText).isEqualTo(
                """
                |fun doSomething() {
                |        val x = 2 * 2
                |        println("replaced")
                |    }
                """.trimMargin()
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
            val generationResult = createGenerateCodeBlockResult(
                """
                |        subFunction()
                |    }
                |    
                |    fun subFunction() {
                |        println("replaced")
                """.trimMargin()
            )

            val step = createReplacePipelineStep(
                targetElement = runReadAction { fixture.getClassFunction("Replace.targetFunction") },
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
            val generationResult = createGenerateCodeBlockResult(
                """
                |        println("replaced")
                """.trimMargin()
            )

            val step = createReplacePipelineStep(
                targetElement = runReadAction {
                    fixture.getClassFunction("Replace.targetFunction")
                },
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

    @Nested
    inner class `on execute for class target element` {

        private var targetClassName = "Target"
        private val targetClass: KtClass get() = fixture.getClass(targetClassName)

        private lateinit var replaceStep: ReplaceFunctionBodyPipelineStep
        private var replaceResult: Result<KtDeclaration> = Result.failure(NotImplementedError())

        @BeforeEach
        internal fun setup() {
            fixture.addFileToProject(
                "Target.kt", """
                class Target {
                
                }
            """.trimIndent()
            )
        }

        @TestStep
        fun runReplacePipelineStep() {
            replaceStep = createReplacePipelineStep(
                targetElement = runReadAction { targetClass },
            )
        }

        abstract inner class `do common part` internal constructor(
            private val receivedBody: String,
        ) {

            @TestStep
            internal fun runStep() {
                replaceResult = replaceStep.executeAndWait(
                    input = createGenerateCodeBlockResult(receivedBody)
                )
            }

            @Test
            fun `return new target element declaration`() {
                assertThat(replaceResult.isSuccess).isTrue()

                val returnedDeclaration = runReadAction { replaceResult.getOrNull()?.text }
                assertThat(returnedDeclaration).isEqualTo(runReadAction { targetClass.text })
            }
        }

        @Nested
        inner class `if simple body received` : `do common part`(
            receivedBody =
                """
                |    fun generatedFun() = Unit
                """.trimMargin()
        ) {

            @Test
            fun `just replace it`() {
                val text = runReadAction { targetClass.containingKtFile.text }
                assertThat(text).isEqualTo(
                    """
                    class Target {
                        fun generatedFun() = Unit
                    }
                     """.trimIndent()
                )
            }
        }

        @Nested
        inner class `if body with extra class received` : `do common part`(
            receivedBody =
                """
                |    fun generatedFun() = Unit
                |}
                |
                |class Extra {
                |    fun extraFun() = Unit
                """.trimMargin()
        ) {

            @Test
            fun `replace only target body`() {
                val text = runReadAction { targetClass.containingKtFile.text }
                assertThat(text).isEqualTo(
                    """
                    class Target {
                        fun generatedFun() = Unit
                    }
                    """.trimIndent()
                )
            }
        }

        @Nested
        inner class `if target placed in other class` : `do common part`(
            receivedBody =
                """
                |        fun generatedFun() = Unit
                """.trimMargin()
        ) {

            @BeforeEach
            internal fun setup() {
                targetClassName = "Parent.Target"

                fixture.addFileToProject(
                    "Parent.kt", """
                        class Parent {
                            fun someFun() = Unit
                        
                            class Target {
                                
                            }
                        }
                    """.trimIndent()
                )
            }

            @Test
            fun `replace only nested class body`() {
                val text = runReadAction { targetClass.containingKtFile.text }
                assertThat(text).isEqualTo(
                    """
                    class Parent {
                        fun someFun() = Unit
                    
                        class Target {
                            fun generatedFun() = Unit
                        }
                    }
                    """.trimIndent()
                )
            }
        }
    }

    // region Utils

    private fun createReplacePipelineStep(targetElement: KtDeclaration): ReplaceFunctionBodyPipelineStep {
        return ReplaceFunctionBodyPipelineStep(targetElement)
    }

    private fun createGenerateCodeBlockResult(generatedCode: String): GenerateCodeBlockResult {
        return GenerateCodeBlockResult(
            variants = listOf(
                CodeBlock(generatedCode),
            ),
        )
    }

    // endregion
}