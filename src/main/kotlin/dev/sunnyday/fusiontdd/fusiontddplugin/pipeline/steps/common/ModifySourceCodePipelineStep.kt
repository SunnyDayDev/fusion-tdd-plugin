package dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.steps.common

import com.intellij.util.application
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.dialog.ModifySourceCodeDialog
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.PipelineStep
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.util.PipelineCancellationException

internal abstract class ModifySourceCodePipelineStep<Input>(
    private val dialogFactory: () -> ModifySourceCodeDialog,
) : PipelineStep<Input, Input> {

    protected abstract fun getDialogCodeBlock(input: Input): String

    protected abstract fun getModifiedInput(rawInput: String): Input

    protected open fun prepareDialog(dialog: ModifySourceCodeDialog) = Unit

    override fun execute(input: Input, observer: (Result<Input>) -> Unit) = application.invokeAndWait {
        val result = runCatching { modifyInputByModifyDialog(input) }
        observer.invoke(result)
    }

    private fun modifyInputByModifyDialog(input: Input): Input {
        val dialog = getModifyResultDialog(input)
        prepareDialog(dialog)

        if (!dialog.showAndGet()) {
            throw PipelineCancellationException()
        }

        return getModifiedInput(dialog)
    }

    private fun getModifyResultDialog(input: Input): ModifySourceCodeDialog {
        return dialogFactory.invoke().apply {
            setCodeBlock(getDialogCodeBlock(input))
        }
    }

    private fun getModifiedInput(dialog: ModifySourceCodeDialog): Input {
        return getModifiedInput(dialog.getCodeBlock())
    }
}