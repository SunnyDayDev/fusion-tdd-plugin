package dev.sunnyday.fusiontdd.fusiontddplugin.idea.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.CodeBlock
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.FunctionGenerationContext
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.GenerateCodeBlockResult
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.service.PipelineStepsFactoryService
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.dialog.ModifySourceCodeDialog
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.PipelineStep
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.steps.*
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration

@Service(Service.Level.PROJECT)
internal class ProjectPipelineStepsFactoryService(
    private val project: Project,
) : PipelineStepsFactoryService {

    override fun collectTestsAndUsedReferencesForFun(
        targetElement: KtDeclaration,
        targetClass: KtClass,
    ): PipelineStep<Nothing?, FunctionGenerationContext> {
        return CollectFunctionGenerationContextPipelineStep(
            targetElement = targetElement,
            targetClass = targetClass,
            settings = project.service(),
        )
    }

    override fun prepareGenerationSourceCode(
        config: PrepareGenerationSourceCodePipelineStep.PrepareSourceConfig
    ): PipelineStep<FunctionGenerationContext, CodeBlock> {
        return PrepareGenerationSourceCodePipelineStep(
            settings = project.service(),
            config = config,
        )
    }

    override fun confirmGenerationSource(): PipelineStep<CodeBlock, CodeBlock> {
        return ConfirmGenerationSourcePipelineStep(
            settings = project.service(),
            dialogFactory = ::ModifySourceCodeDialog,
        )
    }

    override fun generateCodeSuggestion(): PipelineStep<CodeBlock, GenerateCodeBlockResult> {
        val codeGenerationServiceProvider = project.service<ProjectCodeGenerationServiceProvider>()

        return GenerateCodeSuggestionsPipelineStep(
            codeGenerationService = codeGenerationServiceProvider.getCodeGenerationService(),
        )
    }

    override fun replaceBody(function: KtDeclaration): PipelineStep<GenerateCodeBlockResult, KtDeclaration> {
        return ReplaceFunctionBodyPipelineStep(function)
    }

    override fun fixGenerationResult(): PipelineStep<GenerateCodeBlockResult, GenerateCodeBlockResult> {
        return FixGenerationResultPipelineStep(::ModifySourceCodeDialog)
    }
}