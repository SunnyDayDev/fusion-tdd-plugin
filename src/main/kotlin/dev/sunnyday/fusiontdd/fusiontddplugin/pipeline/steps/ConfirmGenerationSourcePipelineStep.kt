package dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.steps

import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.CodeBlock
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.dialog.ModifyGenerationSourceDialog
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings.FusionTDDSettings
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.PipelineStep
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.util.PipelineCancellationException

internal class ConfirmGenerationSourcePipelineStep(
    private val settings: FusionTDDSettings,
) : PipelineStep<CodeBlock, CodeBlock> {

    override fun execute(input: CodeBlock, observer: (Result<CodeBlock>) -> Unit) {
        if (!settings.isConfirmSourceBeforeGeneration) {
            observer.invoke(Result.success(input))
        } else {
            val confirmDialog = ModifyGenerationSourceDialog().apply {
                setCodeBlock(input.rawText)
            }

            if (confirmDialog.showAndGet()) {
                val confirmedCodeBlock = CodeBlock(confirmDialog.getCodeBlock())
                observer.invoke(Result.success(confirmedCodeBlock))
            } else {
                observer.invoke(Result.failure(PipelineCancellationException()))
            }
        }
    }
}