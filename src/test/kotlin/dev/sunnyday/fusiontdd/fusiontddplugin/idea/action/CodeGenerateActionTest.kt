package dev.sunnyday.fusiontdd.fusiontddplugin.idea.action

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Caret
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import com.intellij.testFramework.registerServiceInstance
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.application
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.CodeBlock
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.FunctionGenerationContext
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.GenerateCodeBlockResult
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.service.PipelineStepsFactoryService
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.editor.GeneratingFunctionHighlightAnimator
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.service.GeneratingFunctionHighlightAnimatorProvider
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings.FusionTDDSettings
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.PipelineStep
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.execute
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.steps.PrepareGenerationSourceCodePipelineStep
import dev.sunnyday.fusiontdd.fusiontddplugin.test.getClass
import dev.sunnyday.fusiontdd.fusiontddplugin.test.getClassFunction
import dev.sunnyday.fusiontdd.fusiontddplugin.test.getNamedFunction
import io.mockk.*
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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
        targetClassFile = fixture.copyFileToProject("action/enabled/NoTestClass.kt").let { file ->
            runReadAction {
                file.toPsiFile(fixture.project)
                    .let(::requireNotNull)
            }
        }

        fixture.project.registerServiceInstance(FusionTDDSettings::class.java, settings)
        settings.apply {
            every { projectPackage } returns "project"
            every { authToken } returns "asgfasdk898suhdf"
            every { starcoderModel } returns "starcoder"
            every { isFixApplyGenerationResultError } returns false
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

    @ParameterizedTest
    @ValueSource(ints = [CARET_OUT_OF_CLASS, CARET_ON_INNER_OBJECT])
    fun `on update if action is not in function or class, hide action`(caretPosition: Int) {
        every { caret.offset } returns caretPosition

        runReadAction { action.update(actionEvent) }

        verify { presentation.isEnabled = false }
    }

    @ParameterizedTest
    @ValueSource(ints = [CARET_ON_CLASS_BODY, CARET_ON_CLASS_NAME, CARET_ON_FUNCTION_BODY, CARET_ON_FUNCTION_NAME])
    fun `on update in other cases, show action`(caretPosition: Int) {
        every { caret.offset } returns caretPosition

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

    @ParameterizedTest
    @ValueSource(ints = [CARET_OUT_OF_CLASS, CARET_ON_INNER_OBJECT])
    fun `on perform if action is not in function or class, do nothing`(caretPosition: Int) {
        every { caret.offset } returns caretPosition

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

    @ParameterizedTest
    @ValueSource(ints = [CARET_ON_CLASS_BODY, CARET_ON_CLASS_NAME, CARET_ON_FUNCTION_BODY, CARET_ON_FUNCTION_NAME])
    fun `on perform if action is in function or class, perform it`(caretPosition: Int) {
        every { caret.offset } returns caretPosition
        configurePipelineMock()

        runReadAction { action.actionPerformed(actionEvent) }

        verify { pipelineFactory.collectTestsAndUsedReferencesForFun(any(), any()) }
    }

    @Test
    fun `on perform if all requirements filled, run generate pipeline`() {
        val targetClass = runReadAction { fixture.getClass("project.NoTestClass") }
        val targetFunction = runReadAction { targetClass.getNamedFunction("targetFunction") }

        val functionGenerationContext = mockk<FunctionGenerationContext>()
        val sourceForGeneration = mockk<CodeBlock>(relaxed = true)
        val confirmedSourceForGeneration = mockk<CodeBlock>(relaxed = true)
        val generationResult = mockk<GenerateCodeBlockResult>()

        val pipeline = configurePipelineMock(
            collectDependenciesResult = { Result.success(functionGenerationContext) },
            prepareSourceResult = { Result.success(sourceForGeneration) },
            confirmSourceResult = { Result.success(confirmedSourceForGeneration) },
            generateFunctionResult = { Result.success(generationResult) },
            replaceFunctionResult = { Result.success(targetFunction) },
        )

        runReadAction { action.actionPerformed(actionEvent) }

        verifyOrder {
            pipelineFactory.collectTestsAndUsedReferencesForFun(
                targetElement = refEq(targetFunction),
                targetClass = refEq(targetClass),
            )
            pipelineFactory.prepareGenerationSourceCode(any())
            pipelineFactory.confirmGenerationSource()
            pipelineFactory.generateCodeSuggestion()
            pipelineFactory.replaceBody(refEq(targetFunction))

            pipeline.collectDependencies.execute(outputObserver = any())
            pipeline.prepareSource.execute(refEq(functionGenerationContext), any())
            pipeline.confirmSource.execute(sourceForGeneration, any())
            pipeline.generateFunction.execute(confirmedSourceForGeneration, any())
            pipeline.replaceFunction.execute(refEq(generationResult), any())
        }
    }

    @Test
    fun `on perform action with forceAddComments, prepare source with forceAddComments`() {
        val action = CodeGenerateAction(isInverseAddTestCommentsBeforeGenerationSetting = true)

        configurePipelineMock()
        runReadAction { action.actionPerformed(actionEvent) }

        verify {
            pipelineFactory.prepareGenerationSourceCode(
                PrepareGenerationSourceCodePipelineStep.PrepareSourceConfig(isInverseAddTestCommentsBeforeGenerationSetting = true)
            )
        }
    }

    @Test
    fun `on perform action without forceAddComments, prepare source without forceAddComments`() {
        val action = CodeGenerateAction(isInverseAddTestCommentsBeforeGenerationSetting = false)

        configurePipelineMock()
        runReadAction { action.actionPerformed(actionEvent) }

        verify {
            pipelineFactory.prepareGenerationSourceCode(
                PrepareGenerationSourceCodePipelineStep.PrepareSourceConfig(isInverseAddTestCommentsBeforeGenerationSetting = false)
            )
        }
    }

    @Test
    fun `on perform if generation result can't be applied, try to retry 3 times`() {
        val pipeline = configurePipelineMock(
            replaceFunctionResult = { Result.failure(Error("Simulated error")) },
        )

        runReadAction { action.actionPerformed(actionEvent) }

        verify {
            // Last non retryable call
            pipeline.prepareSource.execute(any(), any())

            // First call
            pipeline.generateFunction.execute(any(), any())
            pipeline.replaceFunction.execute(any(), any())

            // 3 times retry
            repeat(3) {
                pipeline.generateFunction.execute(any(), any())
                pipeline.replaceFunction.execute(any(), any())
            }
        }
        confirmVerified(
            pipeline.prepareSource,
            pipeline.generateFunction,
            pipeline.replaceFunction,
        )
    }

    @Test
    fun `on perform if prepare source failed, don't retry`() {
        val pipeline = configurePipelineMock(
            confirmSourceResult = { Result.failure(Error("Simulated error")) },
        )

        runReadAction { action.actionPerformed(actionEvent) }

        verify(exactly = 1) {
            pipeline.confirmSource.execute(any(), any())
        }

        verify(exactly = 0) {
            pipeline.generateFunction.execute(any(), any())
        }
    }

    @Test
    fun `on perform if fix failed generation result enabled, retry replace with fixed input`() = runInEdtAndWait {
        every { settings.isFixApplyGenerationResultError } returns true
        val brokenGenerationResult = mockk<GenerateCodeBlockResult>()
        val fixedGenerationResult = mockk<GenerateCodeBlockResult>()

        val pipeline = configurePipelineMock(
            generateFunctionResult = results(brokenGenerationResult),
            replaceFunctionResult = results(
                { Result.failure(Error("Simulated error")) },
                { Result.success(mockk()) },
            ),
            fixGenerationResultResult = results(fixedGenerationResult),
        )

        action.actionPerformed(actionEvent)

        verifyOrder {
            pipeline.replaceFunction.execute(brokenGenerationResult, any())
            pipeline.fixGenerationResult.execute(brokenGenerationResult, any())
            pipeline.replaceFunction.execute(fixedGenerationResult, any())
        }
        confirmVerified(pipeline.replaceFunction, pipeline.fixGenerationResult)
    }

    @Test
    fun `on perform, highlight function while executing generation pipeline`() = runInEdtAndWait {
        val animator = mockk<GeneratingFunctionHighlightAnimator>()
        val animatorDisposable = mockk<Disposable>(relaxed = true)
        every { animator.animate(any(), any()) } returns animatorDisposable
        every { generatingFunctionHighlightAnimatorProvider.getGeneratingFunctionHighlightAnimator() } returns animator

        fixture.openFileInEditor(targetClassFile.virtualFile)

        val (firstPipelineStep, _, _, _, lastPipelineStep) = configurePipelineMock()

        action.actionPerformed(actionEvent)

        verifyOrder {
            generatingFunctionHighlightAnimatorProvider.getGeneratingFunctionHighlightAnimator()
            animator.animate(
                fixture.getClassFunction("project.NoTestClass.targetFunction"),
                fixture.editor,
            )

            firstPipelineStep.execute(any())
            lastPipelineStep.execute(any(), any())

            animatorDisposable.dispose()
        }
    }

    private fun configurePipelineMock(
        collectDependenciesResult: (Nothing?) -> Result<FunctionGenerationContext> = { Result.success(mockk()) },
        prepareSourceResult: (FunctionGenerationContext) -> Result<CodeBlock> = { Result.success(mockk(relaxed = true)) },
        confirmSourceResult: (CodeBlock) -> Result<CodeBlock> = { Result.success(it) },
        generateFunctionResult: (CodeBlock) -> Result<GenerateCodeBlockResult> = { Result.success(mockk()) },
        replaceFunctionResult: (GenerateCodeBlockResult) -> Result<KtNamedFunction> = { Result.success(mockk()) },
        fixGenerationResultResult: (GenerateCodeBlockResult) -> Result<GenerateCodeBlockResult> = { Result.success(mockk()) },
    ): PipelineMock {
        val collectDependencies = mockk<PipelineStep<Nothing?, FunctionGenerationContext>> {
            every { execute(null, any()) } answers {
                secondArg<(Result<FunctionGenerationContext>) -> Unit>().invoke(collectDependenciesResult.invoke(null))
            }
        }
        val prepareSource = mockPipelineStep<FunctionGenerationContext, CodeBlock>(prepareSourceResult)
        val confirmSource = mockPipelineStep<CodeBlock, CodeBlock>(confirmSourceResult)
        val generateFunction = mockPipelineStep<CodeBlock, GenerateCodeBlockResult>(generateFunctionResult)
        val replaceFunction = mockPipelineStep<GenerateCodeBlockResult, KtNamedFunction>(replaceFunctionResult)
        val fixGenerationResult =
            mockPipelineStep<GenerateCodeBlockResult, GenerateCodeBlockResult>(fixGenerationResultResult)

        pipelineFactory.apply {
            every { collectTestsAndUsedReferencesForFun(any(), any()) } returns collectDependencies
            every { prepareGenerationSourceCode(any()) } returns prepareSource
            every { confirmGenerationSource() } returns confirmSource
            every { generateCodeSuggestion() } returns generateFunction
            every { replaceBody(any()) } returns replaceFunction
            every { fixGenerationResult() } returns fixGenerationResult
        }

        return PipelineMock(
            collectDependencies = collectDependencies,
            prepareSource = prepareSource,
            confirmSource = confirmSource,
            generateFunction = generateFunction,
            replaceFunction = replaceFunction,
            fixGenerationResult = fixGenerationResult,
        )
    }

    private inline fun <reified I, O> mockPipelineStep(noinline resultProvider: (I) -> Result<O>): PipelineStep<I, O> {
        return mockk<PipelineStep<I, O>> {
            every { execute(any(), any()) } answers {
                secondArg<(Result<O>) -> Unit>().invoke(resultProvider.invoke(firstArg()))
            }
        }
    }

    private fun <I, T> results(vararg items: T): (I) -> Result<T> {
        val remainsResults = items.toMutableList()
        return { _: I ->
            if (remainsResults.isEmpty()) {
                Result.failure(Error("No more results"))
            } else {
                Result.success(remainsResults.removeAt(0))
            }
        }
    }

    private fun <I, T> results(vararg items: (I) -> Result<T>): (I) -> Result<T> {
        val remainsResults = items.toMutableList()
        return { input ->
            if (remainsResults.isEmpty()) {
                Result.failure(Error("No more results"))
            } else {
                remainsResults.removeAt(0).invoke(input)
            }
        }
    }

    private data class PipelineMock(
        val collectDependencies: PipelineStep<Nothing?, FunctionGenerationContext>,
        val prepareSource: PipelineStep<FunctionGenerationContext, CodeBlock>,
        val confirmSource: PipelineStep<CodeBlock, CodeBlock>,
        val generateFunction: PipelineStep<CodeBlock, GenerateCodeBlockResult>,
        val replaceFunction: PipelineStep<GenerateCodeBlockResult, KtNamedFunction>,
        val fixGenerationResult: PipelineStep<GenerateCodeBlockResult, GenerateCodeBlockResult>,
    )

    companion object {
        const val CARET_OUT_OF_CLASS = 16

        const val CARET_ON_CLASS_BODY = 37
        const val CARET_ON_CLASS_NAME = 28

        const val CARET_ON_FUNCTION_BODY = 101
        const val CARET_ON_FUNCTION_NAME = 52

        const val CARET_ON_INNER_OBJECT = 128
    }
}