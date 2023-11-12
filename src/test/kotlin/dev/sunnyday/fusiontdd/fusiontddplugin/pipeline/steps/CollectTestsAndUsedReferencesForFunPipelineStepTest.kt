package dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.steps

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.FunctionTestDependencies
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings.FusionTDDSettings
import dev.sunnyday.fusiontdd.fusiontddplugin.test.*
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CollectTestsAndUsedReferencesForFunPipelineStepTest : LightJavaCodeInsightFixtureTestCase5() {

    private val settings = mockk<FusionTDDSettings>()

    override fun getTestDataPath(): String = "testdata"

    @BeforeEach
    fun setUp() {
        settings.apply {
            every { projectPackage } returns "project"
        }
    }

    @Test
    fun `collect dependencies of call_scenario`() {
        fixture.copyFileToProject("collect/call_scenario/Lib.kt")
        fixture.copyFileToProject("collect/call_scenario/TargetClass.kt")
        fixture.copyFileToProject("collect/call_scenario/TestClass.kt")
        fixture.copyFileToProject("collect/call_scenario/UnusedClass.kt")
        fixture.copyFileToProject("collect/call_scenario/UsedClass.kt")

        runReadAction {
            val targetClass = fixture.getClass("project.TargetClass")
            val targetFunction = targetClass.getNamedFunction("targetFunction")
            val testClass = fixture.getClass("project.TestClass")

            val step = CollectTestsAndUsedReferencesForFunPipelineStep(
                targetClass = targetClass,
                targetFunction = targetFunction,
                testClass = testClass,
                settings = settings,
            )

            val dependencies = step.executeAndWait()

            val usedClass = fixture.getClass("project.UsedClass")

            assertThat(dependencies.getOrNull()).isEqualTo(
                FunctionTestDependencies(
                    function = targetFunction,
                    testClass = testClass,
                    usedClasses = listOf(
                        testClass,
                        fixture.getClass("project.UsedClass"),
                        targetClass,
                    ),
                    usedReferences = listOf(
                        targetFunction,

                        testClass.getNamedFunction("testTarget"),
                        fixture.getHighLevelFun("lib.LibKt", "usedFromTest"),
                        testClass.getProperty("usedClass"),
                        usedClass.getNamedFunction("call"), // enqueue usedFunction
                        testClass.getProperty("target"),

                        testClass.getNamedFunction("testTarget2"),

                        targetClass.getNamedFunction("usedFunction"),
                        fixture.getHighLevelFun("lib.LibKt", "usedFromTarget"),
                    )
                )
            )
        }
    }

    @Test
    fun `collect dependencies of local_class`() {
        fixture.copyFileToProject("collect/local_class/Owner.kt")
        fixture.copyFileToProject("collect/local_class/TargetClass.kt")
        fixture.copyFileToProject("collect/local_class/TargetClassTest.kt")

        runReadAction {
            val targetClass = fixture.getClass("project.TargetClass")
            val targetFunction = targetClass.getNamedFunction("targetFun")
            val testClass = fixture.getClass("project.TargetClassTest")

            val step = CollectTestsAndUsedReferencesForFunPipelineStep(
                targetClass = targetClass,
                targetFunction = targetFunction,
                testClass = testClass,
                settings = settings,
            )

            val dependencies = step.executeAndWait()

            assertThat(dependencies.getOrNull()).isEqualTo(
                FunctionTestDependencies(
                    function = targetFunction,
                    testClass = testClass,
                    usedClasses = listOf(
                        testClass,
                        fixture.getClass("project.Owner"),
                        targetClass,
                    ),
                    usedReferences = listOf(
                        targetFunction,
                        testClass.getNamedFunction("test targetFun"),
                        fixture.getClass("project.Owner").primaryConstructor!!,
                        fixture.getClass("project.Owner.Local").primaryConstructor!!,
                        fixture.getClass("project.Owner.Local"),
                    )
                )
            )
        }
    }

    @Test
    fun `collect external (or lib) classes as used references, instead used classes`() {
        fixture.copyFileToProject("collect/lib_class/LibClass.kt")
        fixture.copyFileToProject("collect/lib_class/TargetClass.kt")
        fixture.copyFileToProject("collect/lib_class/TargetClassTest.kt")

        runReadAction {
            val targetClass = fixture.getClass("project.TargetClass")
            val targetFunction = targetClass.getNamedFunction("targetFun")
            val testClass = fixture.getClass("project.TargetClassTest")

            val step = CollectTestsAndUsedReferencesForFunPipelineStep(
                targetClass = targetClass,
                targetFunction = targetFunction,
                testClass = testClass,
                settings = settings,
            )

            val externalLibClass = fixture.getClass("lib.LibClass")

            val dependencies = step.executeAndWait().getOrNull()

            assertThat(dependencies?.usedClasses.orEmpty())
                .doesNotContain(externalLibClass)

            assertThat(dependencies?.usedReferences.orEmpty())
                .contains(externalLibClass)
        }
    }

    @Test
    fun `collect lib annotations as used references`() {
        fixture.copyFileToProject("collect/lib_class/LibClass.kt")
        fixture.copyFileToProject("collect/lib_class/TargetClass.kt")
        fixture.copyFileToProject("collect/lib_class/TargetClassTest.kt")
        fixture.copyFileToProject("collect/lib_class/Test.kt")

        runReadAction {
            val targetClass = fixture.getClass("project.TargetClass")
            val targetFunction = targetClass.getNamedFunction("targetFun")
            val testClass = fixture.getClass("project.TargetClassTest")

            val step = CollectTestsAndUsedReferencesForFunPipelineStep(
                targetClass = targetClass,
                targetFunction = targetFunction,
                testClass = testClass,
                settings = settings,
            )

            val testAnnotationClass = fixture.getClass("org.junit.jupiter.api.Test")

            val dependencies = step.executeAndWait().getOrNull()

            assertThat(dependencies?.usedClasses.orEmpty())
                .doesNotContain(testAnnotationClass)

            assertThat(dependencies?.usedReferences.orEmpty())
                .contains(testAnnotationClass)
        }
    }

    @Test
    fun `collect target fun chain`() {
        fixture.copyFileToProject("collect/chain/simple/Target.kt")
        fixture.copyFileToProject("collect/chain/simple/TargetTest.kt")

        runReadAction {
            val targetClass = fixture.getClass("project.Target")
            val targetFunction = targetClass.getNamedFunction("chainedFun")
            val testClass = fixture.getClass("project.TargetTest")

            val step = CollectTestsAndUsedReferencesForFunPipelineStep(
                targetClass = targetClass,
                targetFunction = targetFunction,
                testClass = testClass,
                settings = settings,
            )

            val dependencies = step.executeAndWait().getOrNull()

            assertThat(dependencies?.usedReferences.orEmpty())
                .containsAtLeast(
                    testClass.getNamedFunction("test callerFun"),
                    targetClass.getNamedFunction("callerFun"),
                    targetClass.getNamedFunction("chainedFun"),
                )
        }
    }

    @Test
    fun `don't collect fun chains that called from target fun`() {
        fixture.copyFileToProject("collect/chain/simple/Target.kt")
        fixture.copyFileToProject("collect/chain/simple/TargetTest.kt")

        runReadAction {
            val targetClass = fixture.getClass("project.Target")
            val targetFunction = targetClass.getNamedFunction("callerFun")
            val testClass = fixture.getClass("project.TargetTest")

            val step = CollectTestsAndUsedReferencesForFunPipelineStep(
                targetClass = targetClass,
                targetFunction = targetFunction,
                testClass = testClass,
                settings = settings,
            )

            val dependencies = step.executeAndWait().getOrNull()

            assertThat(dependencies?.usedReferences.orEmpty())
                .doesNotContain(targetClass.getNamedFunction("chainedFun"))
        }
    }
}