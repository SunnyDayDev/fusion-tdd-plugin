package dev.sunnyday.fusiontdd.fusiontddplugin.domain.service

import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.CodeBlock
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.FunctionGenerationContext
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.GenerateCodeBlockResult
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.PipelineStep
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction

internal interface PipelineStepsFactoryService {

    fun collectTestsAndUsedReferencesForFun(
        targetFunction: KtNamedFunction,
        targetClass: KtClass,
    ): PipelineStep<Nothing?, FunctionGenerationContext>

    fun prepareGenerationSourceCode(): PipelineStep<FunctionGenerationContext, CodeBlock>

    fun confirmGenerationSource(): PipelineStep<CodeBlock, CodeBlock>

    fun generateCodeSuggestion(): PipelineStep<CodeBlock, GenerateCodeBlockResult>

    fun replaceFunctionBody(function: KtNamedFunction): PipelineStep<GenerateCodeBlockResult, KtNamedFunction>
}