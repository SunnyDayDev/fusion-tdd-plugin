package dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.steps

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import com.intellij.testFramework.runInEdtAndWait
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.FunctionGenerationContext
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.PsiElementContentFilter
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings.FusionTDDSettings
import dev.sunnyday.fusiontdd.fusiontddplugin.test.*
import io.mockk.every
import io.mockk.mockk
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class CollectFunctionGenerationContextPipelineStepTest : LightJavaCodeInsightFixtureTestCase5() {

    // region Definition + Setup

    private val settings = mockk<FusionTDDSettings>()

    override fun getTestDataPath(): String = "testdata"

    @BeforeEach
    fun setUp() {
        settings.apply {
            every { projectPackage } returns "project"
        }
    }

    // endregion

    // region Specification

    // region Unspecified / Other

    @Test
    fun `collect dependencies of call_scenario`() = executeCollectContextTest(
        prepareProject = {
            copyFileToProject("collect/call_scenario/Lib.kt")
            copyFileToProject("collect/call_scenario/TargetClass.kt")
            copyFileToProject("collect/call_scenario/TestClass.kt")
            copyFileToProject("collect/call_scenario/UnusedClass.kt")
            copyFileToProject("collect/call_scenario/UsedClass.kt")
        },
        createStep = { createPipelineStep(targetFunction = "project.TargetClass.targetFunction") },
        assertStepResult = { context ->
            assertThat(context).isEqualTo(
                FunctionGenerationContext(
                    targetElement = getClassFunction("project.TargetClass.targetFunction"),
                    usedClasses = listOf(
                        getClass("project.TestClass"),
                        getClass("project.UsedClass"),
                        getClass("project.TargetClass"),
                    ),
                    usedReferences = listOf(
                        getClassFunction("project.TargetClass.targetFunction"),

                        getClassFunction("project.TestClass.testTarget"),
                        getTopLevelFun("lib.LibKt", "usedFromTest"),
                        getClassProperty("project.TestClass.usedClass"),
                        getClassFunction("project.UsedClass.call"),
                        getClassProperty("project.TestClass.target"),
                        getClassFunction("project.TargetClass.usedFunction"),
                        getTopLevelFun("lib.LibKt", "usedFromTarget"),
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
        createStep = { createPipelineStep(targetFunction = "project.TargetClass.targetFun") },
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
        createStep = { createPipelineStep(targetFunction = "project.TargetClass.targetFun") },
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
        createStep = { createPipelineStep(targetFunction = "project.TargetClass.targetFun") },
        assertStepResult = { context ->
            val testAnnotationClass = getClass("org.junit.jupiter.api.Test")

            assertThat(context.usedClasses)
                .doesNotContain(testAnnotationClass)

            assertThat(context.usedReferences)
                .contains(testAnnotationClass)
        }
    )

    // endregion

    // region chained calls

    @Test
    fun `on chained target fun, collect calls chain`() = executeCollectContextTest(
        prepareProject = { copyDirToProject("collect/chain/simple") },
        createStep = { createPipelineStep(targetFunction = "project.Target.chainedFun") },
        assertStepResult = { context ->
            assertThat(context.usedReferences)
                .containsAtLeast(
                    getClassFunction("project.TargetTest.test callerFun"),
                    getClassFunction("project.Target.callerFun"),
                    getClassFunction("project.Target.chainedFun"),
                )
        }
    )

    // endregion

    // region Branching

    // region 'if' branching

    @Test
    fun `on 'if', collect branch filter`() = executeCollectContextTest(
        prepareProject = { copyDirToProject("collect/chain/if") },
        createStep = { createPipelineStep(targetFunction = "project.Target.doElse2") },
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
        prepareProject = { copyDirToProject("collect/chain/if") },
        createStep = { createPipelineStep(targetFunction = "project.Target.doBoth") },
        assertStepResult = { context ->
            assertThat(context.branchFilters).isEmpty()
        }
    )

    @Test
    fun `on 'if' with expression and single branch used, collect expression references`() = executeCollectContextTest(
        prepareProject = { copyDirToProject("collect/chain/if") },
        createStep = { createPipelineStep(targetFunction = "project.Target.doThenOnIfWithExpression") },
        assertStepResult = { context ->
            assertThat(context.usedReferences).contains(
                getClassFunction("project.Target.ifExpression")
            )
        }
    )

    @Test
    fun `on 'if' with expression and both branches used, collect expression references`() = executeCollectContextTest(
        prepareProject = { copyDirToProject("collect/chain/if") },
        createStep = { createPipelineStep(targetFunction = "project.Target.doBothOnIfWithExpression") },
        assertStepResult = { context ->
            assertThat(context.usedReferences).contains(
                getClassFunction("project.Target.ifExpression")
            )
        }
    )

    // endregion

    // region 'when' branching

    @Test
    fun `on 'when' with entry branch, collect branch filter`() = executeCollectContextTest(
        prepareProject = { copyDirToProject("collect/chain/when") },
        createStep = { createPipelineStep(targetFunction = "project.When.doSome") },
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
        prepareProject = { copyDirToProject("collect/chain/when") },
        createStep = { createPipelineStep(targetFunction = "project.When.doElse") },
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
        prepareProject = { copyDirToProject("collect/chain/when") },
        createStep = { createPipelineStep(targetFunction = "project.When.doAll") },
        assertStepResult = { context ->
            assertThat(context.branchFilters).isEmpty()

            assertThat(context.usedReferences).containsAtLeast(
                getClassFunction("project.When.doSome"),
                getClassFunction("project.When.doAll"),
                getClassFunction("project.When.doElse"),
            )
        }
    )

    @Test
    fun `on 'when' with expression and single branch, collect expression references`() = executeCollectContextTest(
        prepareProject = { copyDirToProject("collect/chain/when") },
        createStep = { createPipelineStep(targetFunction = "project.When.doSomeWithExpression") },
        assertStepResult = { context ->
            assertThat(context.usedReferences).contains(
                getClassFunction("project.When.whenExpression")
            )
        }
    )

    @Test
    fun `on 'when' with expression and all branches, collect expression references`() = executeCollectContextTest(
        prepareProject = { copyDirToProject("collect/chain/when") },
        createStep = { createPipelineStep(targetFunction = "project.When.doAllWithExpression") },
        assertStepResult = { context ->
            assertThat(context.usedReferences).contains(
                getClassFunction("project.When.whenExpression")
            )
        }
    )

    // endregion

    // endregion

    // region Inheritance

    @Test
    fun `on inherit function, collect parent tests`() = executeCollectContextTest(
        prepareProject = { copyDirToProject("collect/inheritance/common") },
        createStep = { createPipelineStep(targetFunction = "project.Child.callParent") },
        assertStepResult = { context ->
            assertThat(context.usedReferences).containsAtLeast(
                getClassFunction("project.InheritanceTest.test child_callParent()"),
                getClassFunction("project.InheritanceTest.test parentProperty_callParent()"),
                getClassFunction("project.InheritanceTest.test parent_callParent()"),
            )
        }
    )

    @Test
    fun `on implement function, collect interface tests`() = executeCollectContextTest(
        prepareProject = { copyDirToProject("collect/inheritance/common") },
        createStep = { createPipelineStep(targetFunction = "project.Child.callInterface") },
        assertStepResult = { context ->
            assertThat(context.usedReferences).containsAtLeast(
                getClassFunction("project.InheritanceTest.test child_callInterface()"),
                getClassFunction("project.InheritanceTest.test externalInterfaceProperty_callInterface()"),
                getClassFunction("project.InheritanceTest.test externalInterface_callInterface()"),
            )
        }
    )

    @Test
    fun `on inherit function, don't collect unnecessary super type tests`() = executeCollectContextTest(
        prepareProject = { copyDirToProject("collect/inheritance/common") },
        createStep = { createPipelineStep(targetFunction = "project.Child.callParent") },
        assertStepResult = { context ->
            assertThat(context.usedReferences).doesNotContain(
                getClassFunction("project.InheritanceTest.test other")
            )
        }
    )

    @Test
    fun `on implement function, don't collect unnecessary interface tests`() = executeCollectContextTest(
        prepareProject = { copyDirToProject("collect/inheritance/common") },
        createStep = { createPipelineStep(targetFunction = "project.Child.callInterface") },
        assertStepResult = { context ->
            assertThat(context.usedReferences).doesNotContain(
                getClassFunction("project.InheritanceTest.test other")
            )
        }
    )

    @Test
    fun `on implementation fun called, mark supertype fun as used`() = executeCollectContextTest(
        prepareProject = { copyDirToProject("collect/inheritance/common") },
        createStep = { createPipelineStep(targetFunction = "project.Child.callInterfaceInTest") },
        assertStepResult = { context ->
            val callInterfaceWithoutArg = getClass("project.ExternalInterface").declarations[1]
            val callInterfaceWithArg = getClass("project.ExternalInterface").declarations[2]

            assertThat(callInterfaceWithArg.text).isEqualTo("fun callInterface(intArg: Int)")
            assertThat(callInterfaceWithoutArg.text).isEqualTo("fun callInterface()")

            assertThat(context.usedReferences).contains(callInterfaceWithArg)
            assertThat(context.usedReferences).doesNotContain(callInterfaceWithoutArg)
        }
    )

    @Test
    fun `on implementation var called, mark supertype var as used`() = executeCollectContextTest(
        prepareProject = { copyDirToProject("collect/inheritance/common") },
        createStep = { createPipelineStep(targetFunction = "project.Child.callInterfaceInTest") },
        assertStepResult = { context ->
            assertThat(context.usedReferences).contains(
                getClassProperty("project.ExternalInterface.interfaceVar"),
            )
        }
    )

    @Test
    fun `when collect implemented interface usages, collect only usages where the interface can be a target instance`() = executeCollectContextTest(
        prepareProject = { copyDirToProject("collect/inheritance/mock_in_other_test") },
        createStep = { createPipelineStep(targetFunction = "project.target.Target.targetFun") },
        assertStepResult = { context ->
            assertThat(context.usedReferences).doesNotContain(
                getClassFunction("project.client.TargetClientTest.test_doSome"),
            )
        }
    )

    // endregion

    // region Filter inaccessible test

    @Test
    fun `on condition case at 'when' branch with all 'is' conditions, skip tests that sends inaccessible args`() =
        executeCollectContextTest(
            prepareProject = { copyDirToProject("collect/filter_test/when") },
            createStep = { createPipelineStep(targetFunction = "project.Target.onEventOne") },
            assertStepResult = { context ->
                assertThat(context.tests.values.flatten()).contains(
                    getClassFunction("project.FilterTest.test onEventOne"),
                )

                assertThat(context.tests.values.flatten()).containsNoneOf(
                    getClassFunction("project.FilterTest.test onEventTwo"),
                    getClassFunction("project.FilterTest.test onEventThree"),
                    getClassFunction("project.FilterTest.test onEventFour"),
                    getClassFunction("project.FilterTest.test Ignored"),
                )
            }
        )

    @Test
    fun `on else case at 'when' branch with all 'is', skip tests that sends inaccessible args`() =
        executeCollectContextTest(
            prepareProject = { copyDirToProject("collect/filter_test/when") },
            createStep = { createPipelineStep(targetFunction = "project.Target.onElse") },
            assertStepResult = { context ->
                assertThat(context.usedReferences).containsAtLeast(
                    getClassFunction("project.FilterTest.test onEventThree"),
                    getClassFunction("project.FilterTest.test onEventFour"),
                )

                assertThat(context.usedReferences).containsNoneOf(
                    getClassFunction("project.FilterTest.test onEventOne"),
                    getClassFunction("project.FilterTest.test onEventTwo"),
                    getClassFunction("project.FilterTest.test Ignored"),
                )
            }
        )

    @Test
    fun `on else case at 'when' with not all 'is' conditions, don't use requirements (pass all tests)`() =
        executeCollectContextTest(
            prepareProject = { copyDirToProject("collect/filter_test/when") },
            createStep = { createPipelineStep(targetFunction = "project.Target.onValueElse") },
            assertStepResult = { context ->
                assertThat(context.usedReferences).containsAtLeast(
                    getClassFunction("project.FilterTest.test onValueEventOne"),
                    getClassFunction("project.FilterTest.test onValueValue"),
                )
            }
        )

    @Test
    fun `on 'is' case at 'when' with range conditions, don't use requirements (pass all tests)`() =
        executeCollectContextTest(
            prepareProject = { copyDirToProject("collect/filter_test/when") },
            createStep = { createPipelineStep(targetFunction = "project.Target.onRangeConditionEventOne") },
            assertStepResult = { context ->
                assertThat(context.usedReferences).containsAtLeast(
                    getClassFunction("project.FilterTest.test onRangeConditionEventOne"),
                    getClassFunction("project.FilterTest.test onRangeConditionValue"),
                )
            }
        )

    // endregion

    // region Object + Companion

    @Test
    fun `on met object reference, collect it as used classes`() = executeCollectContextTest(
        prepareProject = { copyDirToProject("collect/object") },
        createStep = { createPipelineStep(targetFunction = "project.TargetClass.onSome") },
        assertStepResult = { context ->
            assertThat(context.usedReferences).contains(
                getClassOrObject("project.Some.Object"),
            )
        }
    )

    @Test
    fun `on met companion object calls, collect it as used references`() = executeCollectContextTest(
        prepareProject = { copyDirToProject("collect/object") },
        createStep = { createPipelineStep(targetFunction = "project.TargetClass.createByCompanionFactory") },
        assertStepResult = { context ->
            assertThat(context.usedClasses).contains(
                getClassOrObject("project.DataClassWithCompanionFactory"),
            )

            assertThat(context.usedReferences).containsAtLeast(
                getClassOrObject("project.DataClassWithCompanionFactory.Companion"),
                getClassFunction("project.DataClassWithCompanionFactory.Companion.createInt"),
            )
        }
    )

    @Test
    fun `on met imported companion object calls, collect it as used references`() = executeCollectContextTest(
        prepareProject = { copyDirToProject("collect/object") },
        createStep = { createPipelineStep(targetFunction = "project.TargetClass.importedCall") },
        assertStepResult = { context ->
            assertThat(context.usedClasses).contains(
                getClassOrObject("project.ImportedObjectCompanion"),
            )

            assertThat(context.usedReferences).containsAtLeast(
                getClassOrObject("project.ImportedObjectCompanion.Companion"),
                getClassFunction("project.ImportedObjectCompanion.Companion.importedCall"),
            )
        }
    )

    @Test
    fun `on met nested imported companion object calls, collect it as used references`() = executeCollectContextTest(
        prepareProject = { copyDirToProject("collect/object") },
        createStep = { createPipelineStep(targetFunction = "project.TargetClass.nestedImportedCall") },
        assertStepResult = { context ->
            assertThat(context.usedClasses).contains(
                getClassOrObject("project.ImportedObjectCompanion"),
            )

            assertThat(context.usedReferences).containsAtLeast(
                getClassOrObject("project.ImportedObjectCompanion.Nested"),
                getClassOrObject("project.ImportedObjectCompanion.Nested.Companion"),
                getClassFunction("project.ImportedObjectCompanion.Nested.Companion.nestedImportedCall"),
            )
        }
    )

    @Test
    fun `on met imported companion object properties, collect it as used references`() = executeCollectContextTest(
        prepareProject = { copyDirToProject("collect/object") },
        createStep = { createPipelineStep(targetFunction = "project.TargetClass.importedConst") },
        assertStepResult = { context ->
            assertThat(context.usedClasses).contains(
                getClassOrObject("project.ImportedObjectCompanion"),
            )

            assertThat(context.usedReferences).containsAtLeast(
                getClassOrObject("project.ImportedObjectCompanion.Companion"),
                getClassProperty("project.ImportedObjectCompanion.Companion.CONST"),
            )
        }
    )

    // endregion

    // region Mention in target

    @Test
    fun `on met abstract fun reference in target function, collect it as used reference`() = executeCollectContextTest(
        prepareProject = { copyDirToProject("collect/usage_in_target/usage") },
        createStep = { createPipelineStep(targetFunction = "project.Target.targetFun") },
        assertStepResult = { context ->
            assertThat(context.usedReferences).contains(
                getClassFunction("project.Target.usedAbstractFun"),
            )
        }
    )

    @Test
    fun `on met fun reference in target function, collect it as used reference`() = executeCollectContextTest(
        prepareProject = { copyDirToProject("collect/usage_in_target/usage") },
        createStep = { createPipelineStep(targetFunction = "project.Target.targetFun") },
        assertStepResult = { context ->
            assertThat(context.usedReferences).contains(
                getClassFunction("project.Target.usedFun"),
            )
        }
    )

    @Test
    fun `on class reference in target function return, collect it as used reference`() = executeCollectContextTest(
        prepareProject = { copyDirToProject("collect/usage_in_target/return") },
        createStep = { createPipelineStep(targetFunction = "project.Target.returnLibClass") },
        assertStepResult = { context ->
            assertThat(context.usedReferences).contains(
                getClassOrObject("lib.LibClass"),
            )
        }
    )

    // endregion

    // region Class as targetDeclaration

    @Test
    fun `on collect context for class as targetDeclaration, collect all tests where class is used`() {
        executeCollectContextTest(
            prepareProject = { copyDirToProject("collect/class_body/simple") },
            createStep = { createPipelineStep(targetElement = getClassOrObject("project.TargetClass")) },
            assertStepResult = { context ->
                assertThat(context.usedClasses).contains(
                    getClassOrObject("project.TargetClass"),
                )
                assertThat(context.tests).containsExactly(
                    getClassOrObject("project.TargetClassTest"), listOf(
                        getClassFunction("project.TargetClassTest.testFun1"),
                        getClassFunction("project.TargetClassTest.testFun2"),
                    )
                )
                assertThat(context.usedReferences).containsAtLeast(
                    getClassFunction("project.TargetClass.fun1"),
                    getClassFunction("project.TargetClass.fun2"),
                    getClassFunction("project.TargetClassTest.testFun1"),
                    getClassFunction("project.TargetClassTest.testFun2"),
                )
            }
        )
    }

    //

    // endregion

    // endregion

    // region Utils + Fixtures

    private fun executeCollectContextTest(
        prepareProject: JavaCodeInsightTestFixture.() -> Unit,
        createStep: JavaCodeInsightTestFixture.() -> CollectFunctionGenerationContextPipelineStep,
        assertStepResult: JavaCodeInsightTestFixture.(FunctionGenerationContext) -> Unit,
    ) {
        runInEdtAndWait { fixture.prepareProject() }

        val dependencies = runReadAction {
            val result = fixture.createStep().executeAndWait()
            result.getOrNull()
        }

        assertThat(dependencies).isNotNull()
        runReadAction { fixture.assertStepResult(dependencies!!) }
    }

    private fun JavaCodeInsightTestFixture.createPipelineStep(
        targetFunction: String,
    ): CollectFunctionGenerationContextPipelineStep {
        return createPipelineStep(
            targetElement = getClassFunction(targetFunction),
        )
    }

    private fun createPipelineStep(
        targetElement: KtDeclaration,
    ): CollectFunctionGenerationContextPipelineStep {
        return CollectFunctionGenerationContextPipelineStep(
            targetElement = targetElement,
            targetClass = targetElement as? KtClass ?: requireNotNull(targetElement.containingClass()),
            settings = settings,
        )
    }

    private fun CodeInsightTestFixture.copyDirToProject(path: String) {
        val baseDir = File(testDataPath)
        val sourceFile = File(testDataPath, FileUtil.toSystemDependentName(path))
        sourceFile.walkBottomUp()
            .filter(File::isFile)
            .forEach { file ->
                val filePath = file.toRelativeString(baseDir)
                copyFileToProject(filePath)
            }
    }

    // endregion
}