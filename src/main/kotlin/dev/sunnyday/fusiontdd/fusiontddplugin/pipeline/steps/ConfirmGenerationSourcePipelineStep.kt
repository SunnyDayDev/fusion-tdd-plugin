package dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.steps

import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.CodeBlock
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.dialog.ModifySourceCodeDialog
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings.FusionTDDSettings
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.steps.common.ModifySourceCodePipelineStep

internal class ConfirmGenerationSourcePipelineStep(
    private val settings: FusionTDDSettings,
    dialogFactory: () -> ModifySourceCodeDialog,
) : ModifySourceCodePipelineStep<CodeBlock>(dialogFactory) {

    override fun getDialogCodeBlock(input: CodeBlock): String {
        return input.rawText
    }

    override fun getModifiedInput(rawInput: String): CodeBlock {
        return CodeBlock(rawInput)
    }

    override fun execute(input: CodeBlock, observer: (Result<CodeBlock>) -> Unit) {
        if (!settings.isConfirmSourceBeforeGeneration) {
            observer.invoke(Result.success(input))
        } else {
            super.execute(input, observer)
        }
    }
}