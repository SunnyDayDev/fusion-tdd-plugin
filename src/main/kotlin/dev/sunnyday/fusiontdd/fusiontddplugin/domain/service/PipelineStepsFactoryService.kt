package dev.sunnyday.fusiontdd.fusiontddplugin.domain.service

import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.CodeBlock
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.FunctionTestDependencies
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.GenerateCodeBlockResult
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.PipelineStep
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction

internal interface PipelineStepsFactoryService {

    fun collectTestsAndUsedReferencesForFun(
        targetFunction: KtNamedFunction,
        targetClass: KtClass,
        testClass: KtClass,
    ): PipelineStep<Nothing?, FunctionTestDependencies>

    fun prepareGenerationSourceCode(): PipelineStep<FunctionTestDependencies, CodeBlock>

    fun generateCodeSuggestion(): PipelineStep<CodeBlock, GenerateCodeBlockResult>

    fun replaceFunctionBody(function: KtNamedFunction): PipelineStep<GenerateCodeBlockResult, KtNamedFunction>
}