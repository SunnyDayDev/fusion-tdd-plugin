package dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.steps

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.FunctionTestDependencies
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings.FusionTDDSettings
import dev.sunnyday.fusiontdd.fusiontddplugin.test.executeAndWait
import dev.sunnyday.fusiontdd.fusiontddplugin.test.getClass
import dev.sunnyday.fusiontdd.fusiontddplugin.test.getNamedFunction
import io.mockk.every
import io.mockk.mockk
import org.jetbrains.kotlin.psi.KtClass
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.math.max
import kotlin.math.min

internal class PrepareGenerationSourceCodePipelineStepTest : LightJavaCodeInsightFixtureTestCase5() {

    override fun getTestDataPath(): String = "testdata"

    private val settings = mockk<FusionTDDSettings>()

    private val step = PrepareGenerationSourceCodePipelineStep(settings)

    @BeforeEach
    fun setUp() {
        settings.apply {
            every { projectPackage } returns "project"
            every { isAddTestCommentsBeforeGeneration } returns false
        }
    }

    @Test
    fun `all imports are placed at start`() {
        val deps = getPrintFunctionTestDependencies {
            setUsedReferences(
                fixture.getClass("other.ref.UsedDep1"),
                fixture.getClass("other.ref.UsedDep2"),
            )
        }

        val result = runReadAction { step.executeAndWait(deps) }

        var importLastLineIndex = 0
        var anyOtherNonEmptyLineIndex = Int.MAX_VALUE

        result.getOrNull()?.rawText.orEmpty().lineSequence().forEachIndexed { index, line ->
            if (line.isBlank()) return@forEachIndexed

            if (line.startsWith("import")) {
                importLastLineIndex = max(importLastLineIndex, index)
            } else {
                anyOtherNonEmptyLineIndex = min(anyOtherNonEmptyLineIndex, index)
            }
        }

        assertThat(importLastLineIndex).isLessThan(anyOtherNonEmptyLineIndex)
    }

    @Test
    fun `print only used imports`() {
        val deps = getPrintFunctionTestDependencies {
            setUsedReferences(
                fixture.getClass("other.ref.UsedDep1"),
                fixture.getClass("other.ref.UsedDep2"),
            )
        }

        val result = runReadAction { step.executeAndWait(deps) }

        val imports = result.getOrNull()?.rawText.orEmpty()
            .lines()
            .filter { it.startsWith("import") }
            .joinToString(separator = "\n")

        assertThat(imports).isEqualTo(
            """
                import other.ref.UsedDep1
                import other.ref.UsedDep2
            """.trimIndent()
        )
    }

    @Test
    fun `print only used functions of used classes`() {
        val deps = getPrintFunctionTestDependencies {
            val testClass = fixture.getClass("project.TestClass")
            setUsedClasses(testClass)
            setUsedReferences(testClass.getNamedFunction("test target fun"))
        }

        val result = runReadAction { step.executeAndWait(deps) }

        assertThat(result.getOrNull()?.rawText.orEmpty()).isEqualTo(
            """
                class TestClass {
                
                    @Test
                    fun `test target fun`() {
                        TargetClass().targetFun()
                    }
                }
            """.trimIndent()
        )
    }

    @Test
    fun `print target function with generate here tag`() {
        val deps = getPrintFunctionTestDependencies {
            val targetClass = fixture.getClass("project.TargetClass")
            setUsedClasses(targetClass)
            setUsedReferences(targetClass.getNamedFunction("targetFunction"))
        }

        val result = runReadAction { step.executeAndWait(deps) }

        assertThat(result.getOrNull()?.rawText.orEmpty()).isEqualTo(
            """
                class TargetClass {
                
                    fun targetFunction() {
                        -GENERATE_HERE-
                    }
                }
            """.trimIndent()
        )
    }

    @Test
    fun `if isAddTestCommentsBeforeGeneration enabled, print target function comment extracted from test titles`() {
        every { settings.isAddTestCommentsBeforeGeneration } returns true

        val deps = getPrintFunctionTestDependencies {
            val targetClass = fixture.getClass("project.TargetClass")
            val testClass = fixture.getClass("project.TestClass")

            setUsedClasses(targetClass)
            setUsedReferences(
                targetClass.getNamedFunction("targetFunction"),
                testClass.getNamedFunction("test target fun"),
            )
        }

        val result = runReadAction { step.executeAndWait(deps) }

        assertThat(result.getOrNull()?.rawText.orEmpty()).isEqualTo(
            """
                class TargetClass {
                
                    /**
                     * test target fun
                     */
                    fun targetFunction() {
                        -GENERATE_HERE-
                    }
                }
            """.trimIndent()
        )
    }

    @Test
    fun `print local classes`() {
        fixture.copyFileToProject("collect/local_class/Owner.kt")
        fixture.copyFileToProject("collect/local_class/TargetClass.kt")
        fixture.copyFileToProject("collect/local_class/TargetClassTest.kt")

        val dependencies = runReadAction {
            val targetClass = fixture.getClass("project.TargetClass")
            val targetFunction = targetClass.getNamedFunction("targetFun")
            val testClass = fixture.getClass("project.TargetClassTest")

            FunctionTestDependencies(
                function = targetFunction,
                testClass = testClass,
                usedClasses = listOf(
                    fixture.getClass("project.Owner"),
                ),
                usedReferences = listOf(
                    fixture.getClass("project.Owner.Local"),
                )
            )
        }

        val result = runReadAction { step.executeAndWait(dependencies) }

        assertThat(result.getOrNull()?.rawText.orEmpty()).isEqualTo(
            """
                class Owner(val local: Local) {
                
                    class Local(val value: Int) {}
                }
            """.trimIndent()
        )
    }

    private fun getPrintFunctionTestDependencies(
        build: FunctionTestDependenciesBuilder.() -> Unit = {}
    ): FunctionTestDependencies {
        fixture.copyFileToProject("print/References.kt")
        fixture.copyFileToProject("print/TargetClass.kt")
        fixture.copyFileToProject("print/Class1.kt")
        fixture.copyFileToProject("print/Class2.kt")
        fixture.copyFileToProject("print/TestClass.kt")

        return runReadAction {
            val targetClass = fixture.getClass("project.TargetClass")
            val targetFun = targetClass.getNamedFunction("targetFunction")

            val klass1 = fixture.getClass("project.Class1")
            val klass2 = fixture.getClass("project.Class2")

            val testClass = fixture.getClass("project.TestClass")

            var usedClasses = listOf(testClass, klass1, klass2, targetClass)
            var usedReferences = emptyList<PsiElement>()

            val builder = object : FunctionTestDependenciesBuilder {

                override fun setUsedClasses(vararg classes: KtClass) {
                    usedClasses = classes.toList()
                }

                override fun setUsedReferences(vararg references: PsiElement) {
                    usedReferences = references.toList()
                }
            }

            builder.build()

            FunctionTestDependencies(
                testClass = testClass,
                function = targetFun,
                usedClasses = usedClasses,
                usedReferences = usedReferences,
            )
        }
    }

    private interface FunctionTestDependenciesBuilder {

        fun setUsedClasses(vararg classes: KtClass)

        fun setUsedReferences(vararg references: PsiElement)
    }
}