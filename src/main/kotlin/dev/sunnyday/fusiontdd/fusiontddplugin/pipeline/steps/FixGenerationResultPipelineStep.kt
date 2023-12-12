package dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.steps

import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.CodeBlock
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.GenerateCodeBlockResult
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.dialog.ModifySourceCodeDialog
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.steps.common.ModifySourceCodePipelineStep

internal class FixGenerationResultPipelineStep(
    dialogFactory: () -> ModifySourceCodeDialog,
) : ModifySourceCodePipelineStep<GenerateCodeBlockResult>(dialogFactory) {

    override fun prepareDialog(dialog: ModifySourceCodeDialog) {
        super.prepareDialog(dialog)

        dialog.title = "Fix Generated Result"
        dialog.setDescription("The generated result can't be applied to the function, fix it:")
    }

    override fun getDialogCodeBlock(input: GenerateCodeBlockResult): String {
        return input.variants.firstOrNull()?.rawText.orEmpty()
    }

    override fun getModifiedInput(rawInput: String): GenerateCodeBlockResult {
        return GenerateCodeBlockResult(variants = listOf(CodeBlock(rawInput)))
    }
}