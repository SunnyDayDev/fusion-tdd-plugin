package dev.sunnyday.fusiontdd.fusiontddplugin.idea.action

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Caret
import com.intellij.psi.PsiFile
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import com.intellij.testFramework.registerServiceInstance
import com.intellij.util.application
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.CodeBlock
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.FunctionTestDependencies
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.GenerateCodeBlockResult
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.service.PipelineStepsFactoryService
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.editor.GeneratingFunctionHighlightAnimator
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.service.GeneratingFunctionHighlightAnimatorProvider
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings.FusionTDDSettings
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.PipelineStep
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.execute
import dev.sunnyday.fusiontdd.fusiontddplugin.test.getClass
import dev.sunnyday.fusiontdd.fusiontddplugin.test.getClassFunction
import dev.sunnyday.fusiontdd.fusiontddplugin.test.getNamedFunction
import io.mockk.*
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.properties.Delegates

class CodeGenerateActionTest : LightJavaCodeInsightFixtureTestCase5() {

    private val settings = mockk<FusionTDDSettings>()
    private val pipelineFactory = mockk<PipelineStepsFactoryService>()
    private val generatingFunctionHighlightAnimatorProvider =
        mockk<GeneratingFunctionHighlightAnimatorProvider>(relaxed = true)

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

        application.registerServiceInstance(
            GeneratingFunctionHighlightAnimatorProvider::class.java,
            generatingFunctionHighlightAnimatorProvider,
        )
        fixture.project.registerServiceInstance(PipelineStepsFactoryService::class.java, pipelineFactory)

        caret.apply {
            every { offset } returns 101
        }

        dataContext.apply {
            every { getData(CommonDataKeys.PSI_FILE) } answers { targetClassFile }
            every { getData(LangDataKeys.CARET) } answers { caret }
            every { getData(CommonDataKeys.EDITOR) } answers { fixture.editor }
        }

        actionEvent.apply {
            every { project } answers { fixture.project }
            every { dataContext } returns this@CodeGenerateActionTest.dataContext
            every { presentation } answers { this@CodeGenerateActionTest.presentation }
        }
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
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

        val (
            collectDependenciesPipelineStep,
            prepareSourcePipelineStep,
            generateFunctionPipelineStep,
            replaceFunctionPipelineStep,
        ) = configurePipelineMock(
            collectDependenciesResult = { Result.success(functionTestDependencies) },
            prepareSourceResult = { Result.success(sourceForGeneration) },
            generateFunctionResult = { Result.success(generationResult) },
            replaceFunctionResult = { Result.success(targetFunction) },
        )

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
        val (
            _,
            _,
            generateFunctionPipelineStep,
            replaceFunctionPipelineStep,
        ) = configurePipelineMock(
            replaceFunctionResult = { Result.failure(Error("Simulated error")) },
        )

        runReadAction { action.actionPerformed(actionEvent) }

        verify(exactly = 4) {
            generateFunctionPipelineStep.execute(any(), any())
            replaceFunctionPipelineStep.execute(any(), any())
        }
    }

    @RunsInEdt
    @Test
    fun `on perform, highlight function while executing generation pipeline`() {
        val animator = mockk<GeneratingFunctionHighlightAnimator>()
        val animatorDisposable = mockk<Disposable>(relaxed = true)
        every { animator.animate(any(), any()) } returns animatorDisposable
        every { generatingFunctionHighlightAnimatorProvider.getGeneratingFunctionHighlightAnimator() } returns animator

        fixture.openFileInEditor(targetClassFile.virtualFile)

        val (firstPipelineStep, _, _, lastPipelineStep) = configurePipelineMock()

        action.actionPerformed(actionEvent)

        verifyOrder {
            generatingFunctionHighlightAnimatorProvider.getGeneratingFunctionHighlightAnimator()
            animator.animate(
                fixture.getClassFunction("project.TargetClass.targetFunction"),
                fixture.editor,
            )

            firstPipelineStep.execute(any())
            lastPipelineStep.execute(any(), any())

            animatorDisposable.dispose()
        }
    }

    private fun configurePipelineMock(
        collectDependenciesResult: () -> Result<FunctionTestDependencies> = { Result.success(mockk()) },
        prepareSourceResult: () -> Result<CodeBlock> = { Result.success(mockk(relaxed = true)) },
        generateFunctionResult: () -> Result<GenerateCodeBlockResult> = { Result.success(mockk()) },
        replaceFunctionResult: () -> Result<KtNamedFunction> = { Result.success(mockk()) },
    ): PipelineMock {
        val collectDependencies = mockk<PipelineStep<Nothing?, FunctionTestDependencies>> {
            every { execute(null, any()) } answers {
                secondArg<(Result<FunctionTestDependencies>) -> Unit>().invoke(collectDependenciesResult.invoke())
            }
        }
        val prepareSource = mockPipelineStep<FunctionTestDependencies, CodeBlock>(prepareSourceResult)
        val generateFunction = mockPipelineStep<CodeBlock, GenerateCodeBlockResult>(generateFunctionResult)
        val replaceFunction = mockPipelineStep<GenerateCodeBlockResult, KtNamedFunction>(replaceFunctionResult)

        pipelineFactory.apply {
            every { collectTestsAndUsedReferencesForFun(any(), any(), any()) } returns collectDependencies
            every { prepareGenerationSourceCode() } returns prepareSource
            every { generateCodeSuggestion() } returns generateFunction
            every { replaceFunctionBody(any()) } returns replaceFunction
        }

        return PipelineMock(
            collectDependenciesPipelineStep = collectDependencies,
            prepareSourcePipelineStep = prepareSource,
            generateFunctionPipelineStep = generateFunction,
            replaceFunctionPipelineStep = replaceFunction,
        )
    }

    private inline fun <reified I, O> mockPipelineStep(noinline resultProvider: () -> Result<O>): PipelineStep<I, O> {
        return mockk<PipelineStep<I, O>> {
            every { execute(any(), any()) } answers {
                secondArg<(Result<O>) -> Unit>().invoke(resultProvider.invoke())
            }
        }
    }

    private data class PipelineMock(
        val collectDependenciesPipelineStep: PipelineStep<Nothing?, FunctionTestDependencies>,
        val prepareSourcePipelineStep: PipelineStep<FunctionTestDependencies, CodeBlock>,
        val generateFunctionPipelineStep: PipelineStep<CodeBlock, GenerateCodeBlockResult>,
        val replaceFunctionPipelineStep: PipelineStep<GenerateCodeBlockResult, KtNamedFunction>,
    )
}