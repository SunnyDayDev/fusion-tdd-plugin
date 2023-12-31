package dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.steps

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Computable
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.GenerateCodeBlockResult
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.PipelineStep
import org.jetbrains.kotlin.idea.base.util.reformatted
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory

internal class ReplaceFunctionBodyPipelineStep(
    private val targetFunction: KtNamedFunction,
) : PipelineStep<GenerateCodeBlockResult, KtNamedFunction> {

    private val logger = thisLogger()

    override fun execute(input: GenerateCodeBlockResult, observer: (Result<KtNamedFunction>) -> Unit) {
        val result = runCatching {
            val application = ApplicationManager.getApplication()

            val newBody = application.runReadAction(
                Computable {
                    logger.debug("Pipeline: Replace function body of ${targetFunction.name}")

                    val generatedBody = input.variants.firstOrNull()?.rawText.orEmpty()
                    KtPsiFactory(targetFunction.project, markGenerated = false)
                        .createBlock(generatedBody)
                }
            )

            application.invokeAndWait {
                WriteCommandAction.runWriteCommandAction(targetFunction.project) {
                    targetFunction.bodyBlockExpression?.replace(newBody)
                    targetFunction.replace(targetFunction.reformatted())
                }
            }

            targetFunction
        }

        observer.invoke(result)
    }
}