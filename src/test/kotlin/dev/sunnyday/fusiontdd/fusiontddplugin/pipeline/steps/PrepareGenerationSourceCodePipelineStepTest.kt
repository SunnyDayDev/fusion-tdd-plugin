package dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.steps

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.containers.map2Array
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.FunctionGenerationContext
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.PsiElementContentFilter
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings.FusionTDDSettings
import dev.sunnyday.fusiontdd.fusiontddplugin.test.*
import io.mockk.every
import io.mockk.mockk
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class PrepareGenerationSourceCodePipelineStepTest : LightJavaCodeInsightFixtureTestCase5() {

    // region Fixtures, Declarations, Setup

    override fun getTestDataPath(): String = "testdata"

    private val settings = mockk<FusionTDDSettings>()

    private var prepareSourceConfig: PrepareGenerationSourceCodePipelineStep.PrepareSourceConfig = PrepareGenerationSourceCodePipelineStep.PrepareSourceConfig()

    private val step by lazy(LazyThreadSafetyMode.NONE) {
        PrepareGenerationSourceCodePipelineStep(
            settings = settings,
            config = prepareSourceConfig,
        )
    }

    @BeforeEach
    fun setUp() {
        settings.apply {
            every { projectPackage } returns "project"
            every { isAddTestCommentsBeforeGeneration } returns false
        }
    }

    // endregion

    // region Imports

    @Test
    fun `on import, keep only imports of referenced classes`() {
        executePrepareGenerationSourceCodeTest(
            prepareFixture = baseCommonCaseFixtureBuilder(),
            buildContext = {
                setUsedClasses(getClass("project.Class1"), getClass("project.Class2"))
                setUsedReferences(
                    getClass("other.ref.UsedDep1"),
                    getClass("other.ref.UsedDep2"),
                )
            },
            expectedOutput = """
                import other.ref.UsedDep1
                import other.ref.UsedDep2
                
                
                class Class1
                
                class Class2
            """.trimIndent(),
        )
    }

    // endregion

    // region Class, general

    @Test
    fun `on class, keep only used classes and functions`() {
        executePrepareGenerationSourceCodeTest(
            prepareFixture = baseCommonCaseFixtureBuilder(),
            buildContext = {
                setUsedClasses(getClass("project.TestClass"))
                setUsedReferences(getClassFunction("project.TestClass.test target fun"))
            },
            expectedOutput = """
                class TestClass {
                
                    @Test
                    fun `test target fun`() {
                        TargetClass().targetFun()
                    }
                }
            """.trimIndent(),
        )
    }

    @Test
    fun `on local class, print it with same rules as normal class`() {
        executePrepareGenerationSourceCodeTest(
            prepareFixture = {
                addFileToProject(
                    "Owner.kt",
                    """
                        class Owner(val local: Local) {

                            internal class Local(val value: Int) {
                                fun unusedFun() = Unit
                            }
                        }
                    """.trimIndent()
                )
            },
            buildContext = simpleClassContextBuilder("Owner") {
                setUsedReferences(getClass("Owner.Local"))
            },
            expectedOutput = """
                class Owner(val local: Local) {
                
                    class Local(val value: Int)
                }
            """.trimIndent(),
        )
    }

    // region Modifiers

    @Test
    fun `on enum class, keep 'enum' modifier`() {
        executePrepareGenerationSourceCodeTest(
            prepareFixture = {
                addFileToProject(
                    "EmptyEnum.kt",
                    "internal enum class EmptyEnum { }",
                )
            },
            buildContext = simpleClassContextBuilder("EmptyEnum"),
            expectedOutput = "enum class EmptyEnum",
        )
    }

    @Test
    fun `on enum class, keep only used values`() {
        executePrepareGenerationSourceCodeTest(
            prepareFixture = {
                addFileToProject(
                    "SomeEnum.kt",
                    """
                        enum class SomeEnum {
                            Used,
                            Unused,
                        }
                    """.trimIndent(),
                )
            },
            buildContext = simpleClassContextBuilder("SomeEnum") { enumClass ->
                setUsedReferences(enumClass.getEnumEntry("Used"))
            },
            expectedOutput = """
                enum class SomeEnum {
                    Used,
                }
            """.trimIndent(),
        )
    }

    @Test
    fun `on data class, keep 'data' modifier`() {
        executePrepareGenerationSourceCodeTest(
            prepareFixture = {
                addFileToProject(
                    "Data.kt",
                    "internal data class Data(val value: Int)",
                )
            },
            buildContext = simpleClassContextBuilder("Data"),
            expectedOutput = "data class Data(val value: Int)",
        )
    }

    @Test
    fun `on value class, keep 'value' modifier`() {
        executePrepareGenerationSourceCodeTest(
            prepareFixture = {
                addFileToProject(
                    "Value.kt",
                    "internal value class Value(val value: Int)",
                )
            },
            buildContext = simpleClassContextBuilder("Value"),
            expectedOutput = "value class Value(val value: Int)",
        )
    }

    @Test
    fun `on sealed, keep 'sealed' modifier`() {
        executePrepareGenerationSourceCodeTest(
            prepareFixture = {
                addFileToProject(
                    "Sealed.kt",
                    "internal sealed interface Sealed",
                )
            },
            buildContext = simpleClassContextBuilder("Sealed"),
            expectedOutput = "sealed interface Sealed",
        )
    }

    @Test
    fun `on private top level class, remove 'private' modifier`() {
        executePrepareGenerationSourceCodeTest(
            prepareFixture = {
                addFileToProject(
                    "SomeClass.kt",
                    "private class SomeClass",
                )
            },
            buildContext = simpleClassContextBuilder("SomeClass"),
            expectedOutput = "class SomeClass",
        )
    }

    @Test
    fun `on private local class, keep 'private' modifier`() {
        executePrepareGenerationSourceCodeTest(
            prepareFixture = {
                addFileToProject(
                    "Owner.kt",
                    """
                        class Owner {
                
                            private class Local
                        }
                    """.trimIndent(),
                )
            },
            buildContext = simpleClassContextBuilder("Owner") {
                setUsedReferences(getClass("Owner.Local"))
            },
            expectedOutput = """
                class Owner {
                
                    private class Local
                }
            """.trimIndent(),
        )
    }

    @Test
    fun `on protected local class, keep 'protected' modifier`() {
        executePrepareGenerationSourceCodeTest(
            prepareFixture = {
                addFileToProject(
                    "Owner.kt",
                    """
                        class Owner {
                
                            protected class Local
                        }
                    """.trimIndent(),
                )
            },
            buildContext = simpleClassContextBuilder("Owner") {
                setUsedReferences(getClass("Owner.Local"))
            },
            expectedOutput = """
                class Owner {
                
                    protected class Local
                }
            """.trimIndent(),
        )
    }

    @Test
    fun `on multi modifiers local class, keep all allowed modifiers`() {
        executePrepareGenerationSourceCodeTest(
            prepareFixture = {
                addFileToProject(
                    "Owner.kt",
                    """
                        class Owner {
                
                            private enum class Local
                        }
                    """.trimIndent(),
                )
            },
            buildContext = simpleClassContextBuilder("Owner") {
                setUsedReferences(getClass("Owner.Local"))
            },
            expectedOutput = """
                class Owner {
                
                    private enum class Local
                }
            """.trimIndent(),
        )
    }

    // endregion

    // endregion

    // region Object, Companion

    @Test
    fun `on nested object in references, print it`() {
        executePrepareGenerationSourceCodeTest(
            prepareFixture = {
                addFileToProject(
                    "Some.kt",
                    """
                        sealed interface Some {
                
                            data object Value : Some
                            data object Other : Some
                        }
                    """.trimIndent(),
                )
            },
            buildContext = simpleClassContextBuilder("Some") {
                setUsedReferences(getClassOrObject("Some.Value"))
            },
            expectedOutput = """
                sealed interface Some {
                
                    data object Value : Some
                }
            """.trimIndent(),
        )
    }

    @Test
    fun `on companion factory in references, print it`() {
        executePrepareGenerationSourceCodeTest(
            prepareFixture = {
                addFileToProject(
                    "Some.kt",
                    """
                        data class Some<T>(val value: T) {
                            companion object {
                                fun createInt(value: Int): Some<Int>
                                fun createOther(value: String): Some<String>
                            }
                        }
                    """.trimIndent(),
                )
            },
            buildContext = simpleClassContextBuilder("Some") {
                setUsedReferences(
                    getClassOrObject("Some.Companion"),
                    getClassFunction("Some.Companion.createInt"),
                )
            },
            expectedOutput = """
                data class Some<T>(val value: T) {
                
                    companion object {
                
                        fun createInt(value: Int): Some<Int>
                    }
                }
            """.trimIndent(),
        )
    }

    // endregion

    // region Target function

    @Test
    fun `on target function, replace body with 'generate' tag`() {
        executePrepareGenerationSourceCodeTest(
            prepareFixture = baseCommonCaseFixtureBuilder(),
            buildContext = {
                setUsedClasses(getClass("project.TargetClass"))
                setTargetFunction(getClassFunction("project.TargetClass.targetFunction"))
                setUsedReferences(getClassFunction("project.TargetClass.targetFunction"))
            },
            expectedOutput = """
                class TargetClass {
                
                    fun targetFunction() {
                        -GENERATE_HERE-
                    }
                }
            """.trimIndent(),
        )
    }

    @Test
    fun `on target fun if isAddTestCommentsBeforeGeneration enabled, print target function comment extracted from test titles`() {
        every { settings.isAddTestCommentsBeforeGeneration } returns true

        executePrepareGenerationSourceCodeTest(
            prepareFixture = baseCommonCaseFixtureBuilder(),
            buildContext = {
                setUsedClasses(getClass("project.TargetClass"))
                setTargetFunction(getClassFunction("project.TargetClass.targetFunction"))
                setUsedReferences(getClassFunction("project.TargetClass.targetFunction"))
                setTests(
                    getClass("project.TestClass") to listOf(
                        getClassFunction("project.TestClass.test target fun"),
                    )
                )
            },
            expectedOutput = """
                class TargetClass {
                
                    /**
                     * test target fun
                     */
                    fun targetFunction() {
                        -GENERATE_HERE-
                    }
                }
            """.trimIndent(),
        )
    }

    @Test
    fun `on target fun if isAddTestCommentsBeforeGeneration disabled and toggled in config, print target function with tests comments`() {
        every { settings.isAddTestCommentsBeforeGeneration } returns false
        prepareSourceConfig = PrepareGenerationSourceCodePipelineStep.PrepareSourceConfig(
            isInverseAddTestCommentsBeforeGenerationSetting = true,
        )

        executePrepareGenerationSourceCodeTest(
            prepareFixture = baseCommonCaseFixtureBuilder(),
            buildContext = {
                setUsedClasses(getClass("project.TargetClass"))
                setTargetFunction(getClassFunction("project.TargetClass.targetFunction"))
                setUsedReferences(getClassFunction("project.TargetClass.targetFunction"))
                setTests(
                    getClass("project.TestClass") to listOf(
                        getClassFunction("project.TestClass.test target fun"),
                    )
                )
            },
            expectedOutput = """
                class TargetClass {
                
                    /**
                     * test target fun
                     */
                    fun targetFunction() {
                        -GENERATE_HERE-
                    }
                }
            """.trimIndent(),
        )
    }

    @Test
    fun `on target fun if isAddTestCommentsBeforeGeneration enabled and toggled in config, print target function without test comments`() {
        every { settings.isAddTestCommentsBeforeGeneration } returns true
        prepareSourceConfig = PrepareGenerationSourceCodePipelineStep.PrepareSourceConfig(
            isInverseAddTestCommentsBeforeGenerationSetting = true,
        )

        executePrepareGenerationSourceCodeTest(
            prepareFixture = baseCommonCaseFixtureBuilder(),
            buildContext = {
                setUsedClasses(getClass("project.TargetClass"))
                setTargetFunction(getClassFunction("project.TargetClass.targetFunction"))
                setUsedReferences(getClassFunction("project.TargetClass.targetFunction"))
                setTests(
                    getClass("project.TestClass") to listOf(
                        getClassFunction("project.TestClass.test target fun"),
                    )
                )
            },
            expectedOutput = """
                class TargetClass {
                
                    fun targetFunction() {
                        -GENERATE_HERE-
                    }
                }
            """.trimIndent(),
        )
    }

    @Test
    fun `on target fun if isAddTestCommentsBeforeGeneration enabled and real comment exists, append comments extracted from test titles`() {
        every { settings.isAddTestCommentsBeforeGeneration } returns true

        executePrepareGenerationSourceCodeTest(
            prepareFixture = baseCommonCaseFixtureBuilder(),
            buildContext = {
                setUsedClasses(getClass("project.TargetClass"))
                setTargetFunction(getClassFunction("project.TargetClass.targetFunctionWithComment"))
                setUsedReferences(getClassFunction("project.TargetClass.targetFunctionWithComment"))
                setTests(
                    getClass("project.TestClass") to listOf(
                        getClassFunction("project.TestClass.test target fun with comment")
                    )
                )
            },
            expectedOutput = """
                class TargetClass {
                
                    /**
                     * Function real comment.
                     *
                     * test target fun with comment
                     */
                    fun targetFunctionWithComment() {
                        -GENERATE_HERE-
                    }
                }
            """.trimIndent(),
        )
    }

    // endregion

    // region Branch filters

    // region If branching

    @Test
    fun `on if with 'then' filter, print only used 'then' branch`() {
        executePrepareGenerationSourceCodeTest(
            prepareFixture = {
                addFileToProject(
                    "If.kt",
                    """
                    class If {
                        fun check() {
                            if (true) {
                                doThen()
                            } else {
                                doElse()
                            }
                        }
                        private fun doThen() = Unit
                        private fun doElse() = Unit
                    }
                """.trimIndent(),
                )
            },
            buildContext = simpleClassContextBuilder("If", "check", "doThen") { ifClass ->
                val ifExpression = ifClass.getFirstIfExpression()
                setBranchFilters(ifExpression to PsiElementContentFilter.If(ifExpression, isThen = true))
            },
            expectedOutput = """
                class If {
                
                    fun check() {
                        if (true) {
                            doThen()
                        }
                    }
                
                    private fun doThen() = Unit
                }
            """.trimIndent(),
        )
    }

    @Test
    fun `on if with 'else' filter, print only used 'else' branch`() {
        executePrepareGenerationSourceCodeTest(
            prepareFixture = {
                addFileToProject(
                    "If.kt",
                    """
                    class If {
                        fun check() {
                            if (true) {
                                doThen()
                            } else {
                                doElse()
                            }
                        }
                        private fun doThen() = Unit
                        private fun doElse() = Unit
                    }
                """.trimIndent(),
                )
            },
            buildContext = simpleClassContextBuilder("If", "check", "doElse") { ifClass ->
                val ifExpression = ifClass.getFirstIfExpression()
                setBranchFilters(ifExpression to PsiElementContentFilter.If(ifExpression, isThen = false))
            },
            expectedOutput = """
                class If {
                
                    fun check() {
                        if (!true) {
                            doElse()
                        }
                    }
                
                    private fun doElse() = Unit
                }
            """.trimIndent(),
        )
    }

    @Test
    fun `on if with 'else' filter, negotiate complex expression`() {
        executePrepareGenerationSourceCodeTest(
            prepareFixture = {
                addFileToProject(
                    "If.kt",
                    """
                    class If {
                        fun check() {
                            if (true || true) {
                                doThen()
                            } else {
                                doElse()
                            }
                        }
                        private fun doThen() = Unit
                        private fun doElse() = Unit
                    }
                """.trimIndent(),
                )
            },
            buildContext = simpleClassContextBuilder("If", "check", "doElse") { ifClass ->
                val ifExpression = ifClass.getFirstIfExpression()
                setBranchFilters(ifExpression to PsiElementContentFilter.If(ifExpression, isThen = false))
            },
            expectedOutput = """
                class If {
                
                    fun check() {
                        if (!(true || true)) {
                            doElse()
                        }
                    }
                
                    private fun doElse() = Unit
                }
            """.trimIndent(),
        )
    }

    @Test
    fun `on if with 'else' filter, don't negotiate twice negotiated simple expression`() {
        executePrepareGenerationSourceCodeTest(
            prepareFixture = {
                addFileToProject(
                    "If.kt",
                    """
                    class If {
                        fun check() {
                            if (!true) {
                                doThen()
                            } else {
                                doElse()
                            }
                        }
                        private fun doThen() = Unit
                        private fun doElse() = Unit
                    }
                """.trimIndent(),
                )
            },
            buildContext = simpleClassContextBuilder("If", "check", "doElse") { ifClass ->
                val ifExpression = ifClass.getFirstIfExpression()
                setBranchFilters(ifExpression to PsiElementContentFilter.If(ifExpression, isThen = false))
            },
            expectedOutput = """
                class If {
                
                    fun check() {
                        if (true) {
                            doElse()
                        }
                    }
                
                    private fun doElse() = Unit
                }
            """.trimIndent(),
        )
    }

    @Test
    fun `on if with 'else' filter, don't negotiate twice negotiated expression with parenthesis`() {
        executePrepareGenerationSourceCodeTest(
            prepareFixture = {
                addFileToProject(
                    "If.kt", """
                    class If {
                        fun check() {
                            if (!(true || true)) {
                                doThen()
                            } else {
                                doElse()
                            }
                        }
                        private fun doThen() = Unit
                        private fun doElse() = Unit
                    }
                """.trimIndent()
                )
            },
            buildContext = simpleClassContextBuilder("If", "check", "doElse") { ifClass ->
                val ifExpression = ifClass.getFirstIfExpression()
                setBranchFilters(ifExpression to PsiElementContentFilter.If(ifExpression, isThen = false))
            },
            expectedOutput = """
                class If {
                
                    fun check() {
                        if (true || true) {
                            doElse()
                        }
                    }
                
                    private fun doElse() = Unit
                }
            """.trimIndent(),
        )
    }

    // endregion

    // region When branching

    @Test
    fun `on 'when' filter, keep only filtered branches`() {
        executePrepareGenerationSourceCodeTest(
            prepareFixture = {
                addFileToProject(
                    "When.kt", """
                    class When {
                        fun check(case: Int) {
                            when (case) {
                                1, 2 -> doSome()
                                3 -> doElse()
                                else -> doElse()
                            }
                        }
                        private fun doSome() = Unit
                        private fun doElse() = Unit
                    }
                """.trimIndent()
                )
            },
            buildContext = simpleClassContextBuilder("When", "check", "doSome") { whenClass ->
                val whenExpression = whenClass.getFirstWhenExpression()
                setBranchFilters(
                    whenExpression to PsiElementContentFilter.When(
                        whenExpression, listOf(whenExpression.entries[0])
                    )
                )
            },
            expectedOutput = """
                class When {
                
                    fun check(case: Int) {
                        when (case) {
                            1, 2 -> doSome()
                            else -> error("skip")
                        }
                    }
                
                    private fun doSome() = Unit
                }
            """.trimIndent(),
        )
    }

    @Test
    fun `on 'when' filter with else branch, mark other branches as skip`() {
        executePrepareGenerationSourceCodeTest(
            prepareFixture = {
                addFileToProject(
                    "When.kt", """
                    class When {
                        fun check(case: Int) {
                            when (case) {
                                1, 2 -> doSome()
                                3 -> doSome()
                                else -> doElse()
                            }
                        }
                        private fun doSome() = Unit
                        private fun doElse() = Unit
                    }
                """.trimIndent()
                )
            },
            buildContext = simpleClassContextBuilder("When", "check", "doElse") { whenClass ->
                val whenExpression = whenClass.getFirstWhenExpression()
                setBranchFilters(
                    whenExpression to PsiElementContentFilter.When(
                        whenExpression, listOf(whenExpression.elseExpression?.parent as KtWhenEntry)
                    )
                )
            },
            expectedOutput = """
                class When {
                
                    fun check(case: Int) {
                        when (case) {
                            1, 2 -> error("skip")
                            3 -> error("skip")
                            else -> doElse()
                        }
                    }
                
                    private fun doElse() = Unit
                }
            """.trimIndent(),
        )
    }

    // endregion

    // endregion

    // region Fixtures, Builders, Utils

    /**
     * Fill project with common files.
     * - [References](testdata/print/References.kt)
     * - [TargetClass](testdata/print/TargetClass.kt)
     * - [Class1](testdata/print/Class1.kt)
     * - [Class2](testdata/print/Class2.kt)
     * - [TestClass](testdata/print/TestClass.kt)
     */
    private fun baseCommonCaseFixtureBuilder(
        additionalBuilder: JavaCodeInsightTestFixture.() -> Unit = {},
    ): JavaCodeInsightTestFixture.() -> Unit = {
        copyFileToProject("print/References.kt")
        copyFileToProject("print/TargetClass.kt")
        copyFileToProject("print/Class1.kt")
        copyFileToProject("print/Class2.kt")
        copyFileToProject("print/TestClass.kt")

        additionalBuilder()
    }

    private fun simpleClassContextBuilder(
        className: String,
        vararg usedFunctions: String = emptyArray(),
        buildContext: FunctionContextBuilder.(klass: KtClass) -> Unit = {}
    ): FunctionContextBuilder.(fixture: JavaCodeInsightTestFixture) -> Unit {
        return {
            val klass = getClass(className)
            setUsedClasses(klass)
            setUsedReferences(*usedFunctions.map2Array(klass::getNamedFunction))

            buildContext(klass)
        }
    }

    private fun executePrepareGenerationSourceCodeTest(
        prepareFixture: JavaCodeInsightTestFixture.() -> Unit = {},
        buildContext: FunctionContextBuilder.(fixture: JavaCodeInsightTestFixture) -> Unit = {},
        expectedOutput: String
    ) {
        runInEdtAndWait { prepareFixture.invoke(fixture) }

        val functionContext = runReadAction {
            var targetFun: KtFunction? = null

            var usedClasses = emptyList<KtClass>()
            var usedReferences = emptyList<PsiElement>()
            var usedTests = emptyMap<KtClass, List<KtNamedFunction>>()
            var branchFilters = emptyMap<PsiElement, PsiElementContentFilter>()

            val builder = object : FunctionContextBuilder, JavaCodeInsightTestFixture by fixture {

                override fun setTargetFunction(function: KtNamedFunction?) {
                    targetFun = function
                }

                override fun setUsedClasses(vararg classes: KtClass) {
                    usedClasses = classes.toList()
                }

                override fun setUsedReferences(vararg references: PsiElement) {
                    usedReferences = references.toList()
                }

                override fun setTests(vararg tests: Pair<KtClass, List<KtNamedFunction>>) {
                    usedTests = mapOf(*tests)
                }

                override fun setBranchFilters(vararg filters: Pair<PsiElement, PsiElementContentFilter>) {
                    branchFilters = mapOf(*filters)
                }
            }

            builder.buildContext(fixture)

            FunctionGenerationContext(
                targetFunction = targetFun,
                usedClasses = usedClasses.distinct(),
                usedReferences = usedReferences.distinct(),
                tests = usedTests,
                branchFilters = branchFilters,
            )
        }

        val result = runReadAction { step.executeAndWait(functionContext) }

        assertThat(result.getOrThrow().rawText).isEqualTo(expectedOutput)
    }

    private interface FunctionContextBuilder : JavaCodeInsightTestFixture {

        fun setTargetFunction(function: KtNamedFunction?)

        fun setUsedClasses(vararg classes: KtClass)

        fun setUsedReferences(vararg references: PsiElement)

        fun setTests(vararg tests: Pair<KtClass, List<KtNamedFunction>>)

        fun setBranchFilters(vararg filters: Pair<PsiElement, PsiElementContentFilter>)
    }

    // endregion
}