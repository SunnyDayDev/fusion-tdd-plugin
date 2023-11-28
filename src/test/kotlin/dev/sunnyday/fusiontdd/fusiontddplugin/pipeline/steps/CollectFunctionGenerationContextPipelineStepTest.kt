package dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.steps

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import com.intellij.testFramework.runInEdtAndWait
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.FunctionGenerationContext
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.PsiElementContentFilter
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings.FusionTDDSettings
import dev.sunnyday.fusiontdd.fusiontddplugin.test.*
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CollectFunctionGenerationContextPipelineStepTest : LightJavaCodeInsightFixtureTestCase5() {

    private val settings = mockk<FusionTDDSettings>()

    override fun getTestDataPath(): String = "testdata"

    @BeforeEach
    fun setUp() {
        settings.apply {
            every { projectPackage } returns "project"
        }
    }

    @Test
    fun `collect dependencies of call_scenario`() = executeCollectContextTest(
        prepareProject = {
            copyFileToProject("collect/call_scenario/Lib.kt")
            copyFileToProject("collect/call_scenario/TargetClass.kt")
            copyFileToProject("collect/call_scenario/TestClass.kt")
            copyFileToProject("collect/call_scenario/UnusedClass.kt")
            copyFileToProject("collect/call_scenario/UsedClass.kt")
        },
        createStep = {
            CollectFunctionGenerationContextPipelineStep(
                targetFunction = getClassFunction("project.TargetClass.targetFunction"),
                targetClass = getClass("project.TargetClass"),
                settings = settings,
            )
        },
        assertStepResult = { context ->
            assertThat(context).isEqualTo(
                FunctionGenerationContext(
                    targetFunction = getClassFunction("project.TargetClass.targetFunction"),
                    usedClasses = listOf(
                        getClass("project.TestClass"),
                        getClass("project.UsedClass"),
                        getClass("project.TargetClass"),
                    ),
                    usedReferences = listOf(
                        getClassFunction("project.TargetClass.targetFunction"),

                        getClassFunction("project.TestClass.testTarget"),
                        getHighLevelFun("lib.LibKt", "usedFromTest"),
                        getClassProperty("project.TestClass.usedClass"),
                        getClassFunction("project.UsedClass.call"),
                        getClassProperty("project.TestClass.target"),
                        getClassFunction("project.TargetClass.usedFunction"),
                        getHighLevelFun("lib.LibKt", "usedFromTarget"),
                        getClass("project.UsedClass").primaryConstructor!!,

                        getClassFunction("project.TestClass.testTarget2"),
                    ),
                    tests = mutableMapOf(
                        getClass("project.TestClass") to listOf(
                            getClassFunction("project.TestClass.testTarget"),
                            getClassFunction("project.TestClass.testTarget2"),
                        )
                    ),
                    branchFilters = emptyMap(),
                )
            )
        }
    )

    @Test
    fun `on local class reference, put it in usedReferences instead of usedClasses`() = executeCollectContextTest(
        prepareProject = {
            copyFileToProject("collect/local_class/Owner.kt")
            copyFileToProject("collect/local_class/TargetClass.kt")
            copyFileToProject("collect/local_class/TargetClassTest.kt")
        },
        createStep = {
            CollectFunctionGenerationContextPipelineStep(
                targetFunction = getClassFunction("project.TargetClass.targetFun"),
                targetClass = getClass("project.TargetClass"),
                settings = settings,
            )
        },
        assertStepResult = { context ->
            assertThat(context.usedClasses)
                .doesNotContain(getClass("project.Owner.Local"))

            assertThat(context.usedReferences)
                .containsAtLeast(
                    getClass("project.Owner.Local").primaryConstructor!!,
                    getClass("project.Owner.Local"),
                )
        }
    )

    @Test
    fun `on external (or lib) classes references, collect it as references`() = executeCollectContextTest(
        prepareProject = {
            copyFileToProject("collect/lib_class/LibClass.kt")
            copyFileToProject("collect/lib_class/TargetClass.kt")
            copyFileToProject("collect/lib_class/TargetClassTest.kt")
        },
        createStep = {
            CollectFunctionGenerationContextPipelineStep(
                targetFunction = getClassFunction("project.TargetClass.targetFun"),
                targetClass = getClass("project.TargetClass"),
                settings = settings,
            )
        },
        assertStepResult = { context ->
            val externalLibClass = getClass("lib.LibClass")

            assertThat(context.usedClasses)
                .doesNotContain(externalLibClass)

            assertThat(context.usedReferences)
                .contains(externalLibClass)
        }
    )

    @Test
    fun `on annotation, collect it as lib class in used references`() = executeCollectContextTest(
        prepareProject = {
            copyFileToProject("collect/lib_class/LibClass.kt")
            copyFileToProject("collect/lib_class/TargetClass.kt")
            copyFileToProject("collect/lib_class/TargetClassTest.kt")
            copyFileToProject("collect/lib_class/Test.kt")
        },
        createStep = {
            CollectFunctionGenerationContextPipelineStep(
                targetFunction = fixture.getClassFunction("project.TargetClass.targetFun"),
                targetClass = fixture.getClass("project.TargetClass"),
                settings = settings,
            )
        },
        assertStepResult = { context ->
            val testAnnotationClass = getClass("org.junit.jupiter.api.Test")

            assertThat(context.usedClasses)
                .doesNotContain(testAnnotationClass)

            assertThat(context.usedReferences)
                .contains(testAnnotationClass)
        }
    )

    @Test
    fun `on chained target fun, collect calls chain`() = executeCollectContextTest(
        prepareProject = {
            copyFileToProject("collect/chain/simple/Target.kt")
            copyFileToProject("collect/chain/simple/TargetTest.kt")
        },
        createStep = {
            CollectFunctionGenerationContextPipelineStep(
                targetFunction = getClassFunction("project.Target.chainedFun"),
                targetClass = getClass("project.Target"),
                settings = settings,
            )
        },
        assertStepResult = { context ->
            assertThat(context.usedReferences)
                .containsAtLeast(
                    getClassFunction("project.TargetTest.test callerFun"),
                    getClassFunction("project.Target.callerFun"),
                    getClassFunction("project.Target.chainedFun"),
                )
        }
    )

    @Test
    fun `on chained target fun, don't collect funs that called from target fun`() = executeCollectContextTest(
        prepareProject = {
            copyFileToProject("collect/chain/simple/Target.kt")
            copyFileToProject("collect/chain/simple/TargetTest.kt")
        },
        createStep = {
            CollectFunctionGenerationContextPipelineStep(
                targetFunction = getClassFunction("project.Target.callerFun"),
                targetClass = getClass("project.Target"),
                settings = settings,
            )
        },
        assertStepResult = { context ->
            assertThat(context.usedReferences)
                .doesNotContain(getClassFunction("project.Target.chainedFun"))
        }
    )

    @Test
    fun `on 'if', collect branch filter`() = executeCollectContextTest(
        prepareProject = {
            copyFileToProject("collect/chain/if/Target.kt")
            copyFileToProject("collect/chain/if/TargetTest.kt")
        },
        createStep = {
            CollectFunctionGenerationContextPipelineStep(
                targetFunction = getClassFunction("project.Target.doElse2"),
                targetClass = getClass("project.Target"),
                settings = settings,
            )
        },
        assertStepResult = { context ->
            val firstIf = getClassFunction("project.Target.execute").getFirstIfExpression()
            val secondIf = getClassFunction("project.Target.doThen").getFirstIfExpression()

            assertThat(context.branchFilters)
                .containsExactly(
                    firstIf, PsiElementContentFilter.If(firstIf, true),
                    secondIf, PsiElementContentFilter.If(secondIf, false),
                )

            assertThat(context.usedReferences).containsNoneIn(
                listOf(
                    getClassFunction("project.Target.doElse"),
                    getClassFunction("project.Target.doThen2")
                )
            )
        }
    )

    @Test
    fun `on 'if' with both branches used, don't collect branch filter`() = executeCollectContextTest(
        prepareProject = {
            copyFileToProject("collect/chain/if/Target.kt")
            copyFileToProject("collect/chain/if/TargetTest.kt")
        },
        createStep = {
            CollectFunctionGenerationContextPipelineStep(
                targetFunction = getClassFunction("project.Target.doBoth"),
                targetClass = getClass("project.Target"),
                settings = settings,
            )
        },
        assertStepResult = { context ->
            assertThat(context.branchFilters).isEmpty()
        }
    )

    @Test
    fun `on 'when' with entry branch, collect branch filter`() = executeCollectContextTest(
        prepareProject = {
            copyFileToProject("collect/chain/when/When.kt")
            copyFileToProject("collect/chain/when/WhenTest.kt")
        },
        createStep = {
            CollectFunctionGenerationContextPipelineStep(
                targetFunction = getClassFunction("project.When.doSome"),
                targetClass = getClass("project.When"),
                settings = settings,
            )
        },
        assertStepResult = { context ->
            val whenExpression = getClass("project.When").getFirstWhenExpression()
            assertThat(context.branchFilters).containsExactly(
                whenExpression,
                PsiElementContentFilter.When(whenExpression, listOf(whenExpression.entries[0]))
            )

            assertThat(context.usedReferences).apply {
                doesNotContain(getClassFunction("project.When.doElse"))
                containsAtLeast(
                    getClassFunction("project.When.doSome"),
                    getClassFunction("project.When.doAll"),
                )
            }
        }
    )

    @Test
    fun `on 'when' with else branch, collect branch filter`() = executeCollectContextTest(
        prepareProject = {
            copyFileToProject("collect/chain/when/When.kt")
            copyFileToProject("collect/chain/when/WhenTest.kt")
        },
        createStep = {
            CollectFunctionGenerationContextPipelineStep(
                targetFunction = getClassFunction("project.When.doElse"),
                targetClass = getClass("project.When"),
                settings = settings,
            )
        },
        assertStepResult = { context ->
            val whenExpression = getClass("project.When").getFirstWhenExpression()
            assertThat(context.branchFilters).containsExactly(
                whenExpression,
                PsiElementContentFilter.When(whenExpression, listOf(whenExpression.entries[2]))
            )

            assertThat(context.usedReferences).apply {
                doesNotContain(getClassFunction("project.When.doSome"))
                containsAtLeast(
                    getClassFunction("project.When.doElse"),
                    getClassFunction("project.When.doAll"),
                )
            }
        }
    )

    @Test
    fun `on 'when' with all branches, don't collect branch filter`() = executeCollectContextTest(
        prepareProject = {
            copyFileToProject("collect/chain/when/When.kt")
            copyFileToProject("collect/chain/when/WhenTest.kt")
        },
        createStep = {
            CollectFunctionGenerationContextPipelineStep(
                targetFunction = getClassFunction("project.When.doAll"),
                targetClass = getClass("project.When"),
                settings = settings,
            )
        },
        assertStepResult = { context ->
            assertThat(context.branchFilters).isEmpty()

            assertThat(context.usedReferences).apply {
                containsAtLeast(
                    getClassFunction("project.When.doSome"),
                    getClassFunction("project.When.doAll"),
                    getClassFunction("project.When.doElse"),
                )
            }
        }
    )

    private fun executeCollectContextTest(
        prepareProject: JavaCodeInsightTestFixture.() -> Unit,
        createStep: JavaCodeInsightTestFixture.() -> CollectFunctionGenerationContextPipelineStep,
        assertStepResult: JavaCodeInsightTestFixture.(FunctionGenerationContext) -> Unit,
    ) {
        runInEdtAndWait { fixture.prepareProject() }

        val dependencies = runReadAction { fixture.createStep().executeAndWait().getOrNull() }

        assertThat(dependencies).isNotNull()
        runReadAction { fixture.assertStepResult(dependencies!!) }
    }
}