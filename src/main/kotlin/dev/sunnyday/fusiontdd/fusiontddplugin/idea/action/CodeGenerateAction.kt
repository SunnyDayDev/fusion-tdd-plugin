package dev.sunnyday.fusiontdd.fusiontddplugin.idea.action

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.CodeBlock
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.GenerateCodeBlockResult
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.service.PipelineStepsFactoryService
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.util.getLeftOrNull
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.util.isRight
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.util.requireRight
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.service.GeneratingFunctionHighlightAnimatorProvider
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings.FusionTDDSettings
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.*
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.steps.PrepareGenerationSourceCodePipelineStep
import org.jetbrains.kotlin.idea.base.util.letIf
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration


internal class CodeGenerateAction(
    private val isInverseAddTestCommentsBeforeGenerationSetting: Boolean = false,
) : AnAction() {

    private val logger: Logger = thisLogger()

    init {
        if (isInverseAddTestCommentsBeforeGenerationSetting) {
            templatePresentation.description = "Generate function/class body by its tests, with inverted 'Add tests comments before generation' setting"
            templatePresentation.text = "TDD Generate - Inverse Add Comments"
        } else {
            templatePresentation.description = "Generate function/class body by its tests"
            templatePresentation.text = "TDD Generate"
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(event: AnActionEvent) {
        logger.debug("Update '${templatePresentation.text}' action")

        val presentation: Presentation = event.presentation
        val project: Project? = event.project

        val targetElementResult = ActionEventUtils.getTargetElementThatSupportsGeneration(event)

        presentation.isEnabled = !(project == null || targetElementResult.isRight)
    }

    override fun actionPerformed(event: AnActionEvent) {
        logger.debug("Action '${templatePresentation.text}' performed")

        val project = event.project
            ?: return logCantProceed("isn't a project")

        val targetElementBelowCaretOrReason = ActionEventUtils.getTargetElementThatSupportsGeneration(event)

        val targetElement = targetElementBelowCaretOrReason.getLeftOrNull()
            ?: return logCantProceed(targetElementBelowCaretOrReason.requireRight())

        val targetClass = PsiTreeUtil.getParentOfType(targetElement, KtClass::class.java, false)
            ?: return logCantProceed("isn't a class")

        proceedGenerateCodeAction(
            targetElement = targetElement,
            targetClass = targetClass,
            project = project,
            editor = event.dataContext.getData(CommonDataKeys.EDITOR),
        )
    }

    private fun logCantProceed(reason: String) {
        logger.debug("Can't proceed '${templatePresentation.text}', $reason")
    }

    private fun proceedGenerateCodeAction(
        targetElement: KtDeclaration,
        targetClass: KtClass,
        project: Project,
        editor: Editor?,
    ) {
        logger.debug("Proceed '${templatePresentation.text}' for: ${targetElement.name}")

        generateTargetFunctionByTests(targetElement, targetClass, project)
            .wrapWithProgress { highlightGeneratingFunctionWithAnimation(targetElement, editor) }
            .execute()
    }

    // TODO: extract to separated class/usecase
    // TODO: move args to Pipeline.Input
    private fun generateTargetFunctionByTests(
        targetElement: KtDeclaration,
        targetClass: KtClass,
        project: Project,
    ): PipelineStep<Nothing?, *> {
        val pipeline = project.service<PipelineStepsFactoryService>()

        return with(pipeline) {
            prepareSourceForGenerationPipeline(targetElement, targetClass)
                .andThen(generateTargetFunction(project, targetElement))
        }
    }

    private fun PipelineStepsFactoryService.prepareSourceForGenerationPipeline(
        targetElement: KtDeclaration,
        targetClass: KtClass,
    ): PipelineStep<Nothing?, CodeBlock> {
        return collectTestsAndUsedReferencesForFun(targetElement, targetClass)
            .andThen(
                prepareGenerationSourceCode(
                    PrepareGenerationSourceCodePipelineStep.PrepareSourceConfig(
                        isInverseAddTestCommentsBeforeGenerationSetting = isInverseAddTestCommentsBeforeGenerationSetting,
                    )
                )
            )
            .andThen(confirmGenerationSource())
    }

    private fun PipelineStepsFactoryService.generateTargetFunction(
        project: Project,
        targetElement: KtDeclaration,
    ): PipelineStep<CodeBlock, KtDeclaration> {
        return generateCodeSuggestion()
            .andThen(replaceFunctionBodyWithPossibleFix(project, targetElement))
            .retry(GENERATE_FUNCTION_RETRIES_COUNT)
    }

    private fun PipelineStepsFactoryService.replaceFunctionBodyWithPossibleFix(
        project: Project,
        targetElement: KtDeclaration,
    ): PipelineStep<GenerateCodeBlockResult, KtDeclaration> {
        val settings = project.service<FusionTDDSettings>()

        return replaceBody(targetElement)
            .letIf(settings.isFixApplyGenerationResultError) { it.retryWithFix(fixGenerationResult()) }
    }

    private fun highlightGeneratingFunctionWithAnimation(
        targetElement: KtDeclaration,
        editor: Editor?,
    ): Disposable {
        val highlightAnimator = service<GeneratingFunctionHighlightAnimatorProvider>()
            .getGeneratingFunctionHighlightAnimator()

        return if (editor != null) {
            highlightAnimator.animate(targetElement, editor)
        } else {
            Disposable { }
        }
    }

    companion object {

        const val ID = "dev.sunnyday.fusiontdd.action.generate"
        const val INVERSE_COMMENTS_ID = "dev.sunnyday.fusiontdd.action.generate.inversecomments"
        const val GENERATE_FUNCTION_RETRIES_COUNT = 3
    }
}