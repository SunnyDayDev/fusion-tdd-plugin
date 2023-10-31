package dev.sunnyday.fusiontdd.fusiontddplugin.idea.action

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Caret
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import com.intellij.testFramework.registerServiceInstance
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.CodeBlock
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.FunctionTestDependencies
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.GenerateCodeBlockResult
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.service.PipelineStepsFactoryService
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings.FusionTDDSettings
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.PipelineStep
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.execute
import dev.sunnyday.fusiontdd.fusiontddplugin.test.getClass
import dev.sunnyday.fusiontdd.fusiontddplugin.test.getNamedFunction
import io.mockk.*
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.properties.Delegates

class CodeGenerateActionTest : LightJavaCodeInsightFixtureTestCase5() {

    private val settings = mockk<FusionTDDSettings>()
    private val pipelineFactory = mockk<PipelineStepsFactoryService>()

    private val dataContext = mockk<DataContext>()
    private val actionEvent = mockk<AnActionEvent>()
    private val caret = mockk<Caret>()

    private val presentation = mockk<Presentation>(relaxed = true)

    private var targetClassFile: PsiFile by Delegates.notNull()

    private val action = CodeGenerateAction()

    override fun getTestDataPath(): String = "testdata"

    @BeforeEach
    fun setUp() {
        targetClassFile = fixture.copyFileToProject("action/enabled/TargetClass.kt").let { file ->
            runReadAction {
                file.toPsiFile(fixture.project)
                    .let(::requireNotNull)
            }
        }

        fixture.copyFileToProject("action/enabled/TargetClassTest.kt")

        fixture.project.registerServiceInstance(FusionTDDSettings::class.java, settings)
        settings.apply {
            every { projectPackage } returns "project"
            every { authToken } returns "asgfasdk898suhdf"
            every { starcoderModel } returns "starcoder"
        }

        fixture.project.registerServiceInstance(PipelineStepsFactoryService::class.java, pipelineFactory)

        caret.apply {
            every { offset } returns 101
        }

        dataContext.apply {
            every { getData(CommonDataKeys.PSI_FILE) } answers { targetClassFile }
            every { getData(LangDataKeys.CARET) } answers { caret }
        }

        actionEvent.apply {
            every { project } answers { fixture.project }
            every { dataContext } returns this@CodeGenerateActionTest.dataContext
            every { presentation } answers { this@CodeGenerateActionTest.presentation }
        }
    }

    @Test
    fun `update action in background thread`() {
        val threadType = action.actionUpdateThread

        assertThat(threadType).isEqualTo(ActionUpdateThread.BGT)
    }

    @Test
    fun `on update if action is not in file, hide action`() {
        every { dataContext.getData(CommonDataKeys.PSI_FILE) } returns null

        runReadAction { action.update(actionEvent) }

        verify { presentation.isEnabled = false }
    }

    @Test
    fun `on update if action has not in caret, hide action`() {
        every { dataContext.getData(LangDataKeys.CARET) } returns null

        runReadAction { action.update(actionEvent) }

        verify { presentation.isEnabled = false }
    }

    @Test
    fun `on update if no element below caret, hide action`() {
        every { caret.offset } returns 1_000

        runReadAction { action.update(actionEvent) }

        verify { presentation.isEnabled = false }
    }

    @Test
    fun `on update if action is not in function, hide action`() {
        every { caret.offset } returns 40

        runReadAction { action.update(actionEvent) }

        verify { presentation.isEnabled = false }
    }

    @Test
    fun `on update in other cases, show action`() {
        runReadAction { action.update(actionEvent) }

        verify { presentation.isEnabled = true }
    }

    @Test
    fun `on perform if action is not in project, do nothing`() {
        every { actionEvent.project } returns null

        runReadAction { action.actionPerformed(actionEvent) }

        confirmVerified(pipelineFactory)
    }

    @Test
    fun `on perform if action is not in file, do nothing`() {
        every { dataContext.getData(CommonDataKeys.PSI_FILE) } returns null

        runReadAction { action.actionPerformed(actionEvent) }

        confirmVerified(pipelineFactory)
    }

    @Test
    fun `on perform if action has not in caret, do nothing`() {
        every { dataContext.getData(LangDataKeys.CARET) } returns null

        runReadAction { action.actionPerformed(actionEvent) }

        confirmVerified(pipelineFactory)
    }

    @Test
    fun `on perform if no element below caret, do nothing`() {
        every { caret.offset } returns 1_000

        runReadAction { action.actionPerformed(actionEvent) }

        confirmVerified(pipelineFactory)
    }

    @Test
    fun `on perform if action is not in function, do nothing`() {
        every { caret.offset } returns 40

        runReadAction { action.actionPerformed(actionEvent) }

        confirmVerified(pipelineFactory)
    }

    @Test
    fun `on perform if action is not in class, do nothing`() {
        targetClassFile = fixture.copyFileToProject("action/enabled/NoClass.kt").let { file ->
            runReadAction {
                file.toPsiFile(fixture.project)
                    .let(::requireNotNull)
            }
        }

        runReadAction { action.actionPerformed(actionEvent) }

        confirmVerified(pipelineFactory)
    }

    @Test
    fun `on perform if action is in class without test, do nothing`() {
        targetClassFile = fixture.copyFileToProject("action/enabled/NoTestClass.kt").let { file ->
            runReadAction {
                file.toPsiFile(fixture.project)
                    .let(::requireNotNull)
            }
        }

        runReadAction { action.actionPerformed(actionEvent) }

        confirmVerified(pipelineFactory)
    }

    @Test
    fun `on perform if all requirements filled, run generate pipeline`() {
        val targetClass = runReadAction { fixture.getClass("project.TargetClass") }
        val testClass = runReadAction { fixture.getClass("project.TargetClassTest") }
        val targetFunction = runReadAction { targetClass.getNamedFunction("targetFunction") }

        val functionTestDependencies = mockk<FunctionTestDependencies>()
        val sourceForGeneration = mockk<CodeBlock>(relaxed = true)
        val generationResult = mockk<GenerateCodeBlockResult>()

        val collectDependenciesPipelineStep = mockk<PipelineStep<Nothing?, FunctionTestDependencies>> {
            every { execute(null, any()) } answers {
                secondArg<(Result<FunctionTestDependencies>) -> Unit>().invoke(Result.success(functionTestDependencies))
            }
        }
        val prepareSourcePipelineStep = mockk<PipelineStep<FunctionTestDependencies, CodeBlock>> {
            every { execute(any(), any()) } answers {
                secondArg<(Result<CodeBlock>) -> Unit>().invoke(Result.success(sourceForGeneration))
            }
        }
        val generateFunctionPipelineStep = mockk<PipelineStep<CodeBlock, GenerateCodeBlockResult>> {
            every { execute(any(), any()) } answers {
                secondArg<(Result<GenerateCodeBlockResult>) -> Unit>().invoke(Result.success(generationResult))
            }
        }
        val replaceFunctionPipelineStep = mockk<PipelineStep<GenerateCodeBlockResult, KtNamedFunction>> {
            every { execute(any(), any()) } answers {
                secondArg<(Result<KtNamedFunction>) -> Unit>().invoke(Result.success(targetFunction))
            }
        }

        pipelineFactory.apply {
            every { collectTestsAndUsedReferencesForFun(any(), any(), any()) } returns collectDependenciesPipelineStep
            every { prepareGenerationSourceCode() } returns prepareSourcePipelineStep
            every { generateCodeSuggestion() } returns generateFunctionPipelineStep
            every { replaceFunctionBody(any()) } returns replaceFunctionPipelineStep
        }

        runReadAction { action.actionPerformed(actionEvent) }

        verifyOrder {
            pipelineFactory.collectTestsAndUsedReferencesForFun(
                targetFunction = refEq(targetFunction),
                targetClass = refEq(targetClass),
                testClass = refEq(testClass),
            )
            pipelineFactory.prepareGenerationSourceCode()
            pipelineFactory.generateCodeSuggestion()
            pipelineFactory.replaceFunctionBody(refEq(targetFunction))

            collectDependenciesPipelineStep.execute(outputObserver = any())
            prepareSourcePipelineStep.execute(refEq(functionTestDependencies), any())
            generateFunctionPipelineStep.execute(sourceForGeneration, any())
            replaceFunctionPipelineStep.execute(refEq(generationResult), any())
        }
    }

    @Test
    fun `on perform if error happened, try to retry 3 times`() {
        val functionTestDependencies = mockk<FunctionTestDependencies>()
        val sourceForGeneration = mockk<CodeBlock>(relaxed = true)
        val generationResult = mockk<GenerateCodeBlockResult>()

        val collectDependenciesPipelineStep = mockk<PipelineStep<Nothing?, FunctionTestDependencies>> {
            every { execute(null, any()) } answers {
                secondArg<(Result<FunctionTestDependencies>) -> Unit>().invoke(Result.success(functionTestDependencies))
            }
        }
        val prepareSourcePipelineStep = mockk<PipelineStep<FunctionTestDependencies, CodeBlock>> {
            every { execute(any(), any()) } answers {
                secondArg<(Result<CodeBlock>) -> Unit>().invoke(Result.success(sourceForGeneration))
            }
        }
        val generateFunctionPipelineStep = mockk<PipelineStep<CodeBlock, GenerateCodeBlockResult>> {
            every { execute(any(), any()) } answers {
                secondArg<(Result<GenerateCodeBlockResult>) -> Unit>().invoke(Result.success(generationResult))
            }
        }
        val replaceFunctionPipelineStep = mockk<PipelineStep<GenerateCodeBlockResult, KtNamedFunction>> {
            every { execute(any(), any()) } answers {
                secondArg<(Result<KtNamedFunction>) -> Unit>().invoke(Result.failure(Error("Simulated error")))
            }
        }

        pipelineFactory.apply {
            every { collectTestsAndUsedReferencesForFun(any(), any(), any()) } returns collectDependenciesPipelineStep
            every { prepareGenerationSourceCode() } returns prepareSourcePipelineStep
            every { generateCodeSuggestion() } returns generateFunctionPipelineStep
            every { replaceFunctionBody(any()) } returns replaceFunctionPipelineStep
        }

        runReadAction { action.actionPerformed(actionEvent) }

        verify(exactly = 4) {
            generateFunctionPipelineStep.execute(any(), any())
            replaceFunctionPipelineStep.execute(any(), any())
        }
    }
}