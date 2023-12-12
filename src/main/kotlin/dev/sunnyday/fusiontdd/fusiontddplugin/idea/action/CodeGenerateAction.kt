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
import org.jetbrains.kotlin.idea.base.util.letIf
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction


class CodeGenerateAction : AnAction() {

    private val logger: Logger = thisLogger()

    init {
        templatePresentation.description = "Generate function body by its tests"
        templatePresentation.text = "TDD Generate"
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(event: AnActionEvent) {
        logger.debug("Update '${templatePresentation.text}' action")

        val presentation: Presentation = event.presentation
        val project: Project? = event.project

        val targetFunctionOrReason = ActionEventUtils.getFunctionBelowCaretOrReason(event)

        presentation.isEnabled = !(project == null || targetFunctionOrReason.isRight)
    }

    override fun actionPerformed(event: AnActionEvent) {
        logger.debug("Action '${templatePresentation.text}' performed")

        val project = event.project
            ?: return logCantProceed("isn't a project")

        val targetFunctionOrReason = ActionEventUtils.getFunctionBelowCaretOrReason(event)
        val targetFunction = targetFunctionOrReason.getLeftOrNull()
            ?: return logCantProceed(targetFunctionOrReason.requireRight())

        val targetClass = PsiTreeUtil.getParentOfType(targetFunction, KtClass::class.java, false)
            ?: return logCantProceed("isn't a class")

        proceedGenerateCodeAction(
            targetFunction = targetFunction,
            targetClass = targetClass,
            project = project,
            editor = event.dataContext.getData(CommonDataKeys.EDITOR),
        )
    }

    private fun logCantProceed(reason: String) {
        logger.debug("Can't proceed '${templatePresentation.text}', $reason")
    }

    private fun proceedGenerateCodeAction(
        targetFunction: KtNamedFunction,
        targetClass: KtClass,
        project: Project,
        editor: Editor?,
    ) {
        logger.debug("Proceed '${templatePresentation.text}' for: ${targetFunction.name}")

        generateTargetFunctionByTests(targetFunction, targetClass, project)
            .wrapWithProgress { highlightGeneratingFunctionWithAnimation(targetFunction, editor) }
            .execute()
    }

    // TODO: extract to separated class/usecase
    // TODO: move args to Pipeline.Input
    private fun generateTargetFunctionByTests(
        targetFunction: KtNamedFunction,
        targetClass: KtClass,
        project: Project,
    ): PipelineStep<Nothing?, *> {
        val pipeline = project.service<PipelineStepsFactoryService>()

        return with(pipeline) {
            prepareSourceForGenerationPipeline(targetFunction, targetClass)
                .andThen(generateTargetFunction(project, targetFunction))
        }
    }

    private fun PipelineStepsFactoryService.prepareSourceForGenerationPipeline(
        targetFunction: KtNamedFunction,
        targetClass: KtClass,
    ): PipelineStep<Nothing?, CodeBlock> {
        return collectTestsAndUsedReferencesForFun(targetFunction, targetClass)
            .andThen(prepareGenerationSourceCode())
            .andThen(confirmGenerationSource())
    }

    private fun PipelineStepsFactoryService.generateTargetFunction(
        project: Project,
        targetFunction: KtNamedFunction,
    ): PipelineStep<CodeBlock, KtNamedFunction> {
        return generateCodeSuggestion()
            .andThen(replaceFunctionBodyWithPossibleFix(project, targetFunction))
            .retry(GENERATE_FUNCTION_RETRIES_COUNT)
    }

    private fun PipelineStepsFactoryService.replaceFunctionBodyWithPossibleFix(
        project: Project,
        targetFunction: KtNamedFunction,
    ): PipelineStep<GenerateCodeBlockResult, KtNamedFunction> {
        val settings = project.service<FusionTDDSettings>()

        return replaceFunctionBody(targetFunction)
            .letIf(settings.isFixApplyGenerationResultError) { it.retryWithFix(fixGenerationResult()) }
    }

    private fun highlightGeneratingFunctionWithAnimation(
        targetFunction: KtNamedFunction,
        editor: Editor?,
    ): Disposable {
        val highlightAnimator = service<GeneratingFunctionHighlightAnimatorProvider>()
            .getGeneratingFunctionHighlightAnimator()

        return if (editor != null) {
            highlightAnimator.animate(targetFunction, editor)
        } else {
            Disposable { }
        }
    }

    companion object {

        const val ID = "dev.sunnyday.fusiontdd.action.generate"
        const val GENERATE_FUNCTION_RETRIES_COUNT = 3
    }
}