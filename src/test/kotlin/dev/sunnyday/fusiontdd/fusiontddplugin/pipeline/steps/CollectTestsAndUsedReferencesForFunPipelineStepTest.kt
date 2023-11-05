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
                        fixture.getClass("project.Owner.Local"),
                    )
                )
            )
        }
    }
}