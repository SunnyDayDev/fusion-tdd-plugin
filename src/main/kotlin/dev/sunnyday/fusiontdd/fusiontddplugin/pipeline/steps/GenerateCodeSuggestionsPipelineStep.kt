package dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.steps

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.CodeBlock
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.GenerateCodeBlockResult
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.service.CodeGenerationService
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.PipelineCoroutineScope
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.PipelineStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class GenerateCodeSuggestionsPipelineStep(
    private val codeGenerationService: CodeGenerationService,
    private val coroutineScope: CoroutineScope = PipelineCoroutineScope()
) : PipelineStep<CodeBlock, GenerateCodeBlockResult> {

    private val logger: Logger = thisLogger()

    override fun execute(input: CodeBlock, observer: (Result<GenerateCodeBlockResult>) -> Unit) {
        logger.debug("Pipeline: Generate code started.")

        coroutineScope.launch {
            val result = runCatching { codeGenerationService.generate(input) }
            observer.invoke(result)
        }
    }
}