package dev.sunnyday.fusiontdd.fusiontddplugin.domain.service

import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.CodeBlock
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.FunctionGenerationContext
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.GenerateCodeBlockResult
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.PipelineStep
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.steps.PrepareGenerationSourceCodePipelineStep
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration

internal interface PipelineStepsFactoryService {

    fun collectTestsAndUsedReferencesForFun(
        targetElement: KtDeclaration,
        targetClass: KtClass,
    ): PipelineStep<Nothing?, FunctionGenerationContext>

    fun prepareGenerationSourceCode(
        config: PrepareGenerationSourceCodePipelineStep.PrepareSourceConfig = PrepareGenerationSourceCodePipelineStep.PrepareSourceConfig(),
    ): PipelineStep<FunctionGenerationContext, CodeBlock>

    fun confirmGenerationSource(): PipelineStep<CodeBlock, CodeBlock>

    fun generateCodeSuggestion(): PipelineStep<CodeBlock, GenerateCodeBlockResult>

    fun replaceBody(function: KtDeclaration): PipelineStep<GenerateCodeBlockResult, KtDeclaration>

    fun fixGenerationResult(): PipelineStep<GenerateCodeBlockResult, GenerateCodeBlockResult>
}