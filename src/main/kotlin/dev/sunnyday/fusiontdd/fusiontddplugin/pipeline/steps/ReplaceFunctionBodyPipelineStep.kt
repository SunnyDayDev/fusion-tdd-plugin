package dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.steps

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiWhiteSpace
import com.intellij.util.application
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.GenerateCodeBlockResult
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.PipelineStep
import org.jetbrains.kotlin.idea.base.util.reformat
import org.jetbrains.kotlin.idea.util.CommentSaver.Companion.tokenType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass

internal class ReplaceFunctionBodyPipelineStep(
    private val targetFunction: KtNamedFunction,
) : PipelineStep<GenerateCodeBlockResult, KtNamedFunction> {

    private val logger = thisLogger()


    private val psiFactory: KtPsiFactory by lazy {
        KtPsiFactory(targetFunction.project, markGenerated = false)
    }

    override fun execute(input: GenerateCodeBlockResult, observer: (Result<KtNamedFunction>) -> Unit) {
        val result = runCatching {
            val generatedDeclarations: List<KtDeclaration> = getGeneratedDeclarations(input)
            replaceTargetFunWithGenerationResult(generatedDeclarations)

            generatedDeclarations.first() as KtNamedFunction
        }

        observer.invoke(result)
    }

    private fun getGeneratedDeclarations(generationResult: GenerateCodeBlockResult): List<KtDeclaration> {
        return application.runReadAction(
            Computable {
                logger.debug("Pipeline: Replace function body of ${targetFunction.name}")

                val resultCode = createResultBody(generationResult)

                psiFactory.createFile(resultCode).declarations
                    .map { psiFactory.createDeclaration(it.text) }
            }
        )
    }

    private fun createResultBody(generationResult: GenerateCodeBlockResult): String {
        val generatedBody = generationResult.variants.firstOrNull()?.rawText.orEmpty()

        return buildString {
            val targetFunctionWhiteSpace = (targetFunction.prevSibling as? PsiWhiteSpace)
                ?.text?.lines()?.last().orEmpty()

            append(targetFunctionWhiteSpace)

            var printCursor = targetFunction.firstChild
            while (printCursor !is KtBlockExpression && printCursor.tokenType != KtTokens.EQ) {
                append(printCursor.text)
                printCursor = printCursor.nextSibling
            }

            append("{\n")
            append(generatedBody)
            append("\n")
            append(targetFunctionWhiteSpace)
            append("}")
        }.trimIndent()
    }

    private fun replaceTargetFunWithGenerationResult(generatedDeclarations: List<KtDeclaration>) {
        application.invokeAndWait {
            WriteCommandAction.runWriteCommandAction(targetFunction.project) {
                val klass = targetFunction.containingClass() as KtClassOrObject

                val replacedFunction = targetFunction.replace(generatedDeclarations.first()) as KtDeclaration
                replacedFunction.reformat()

                addDeclarationsAfter(klass, replacedFunction, generatedDeclarations.drop(1))
            }
        }
    }

    private fun addDeclarationsAfter(
        klass: KtClassOrObject,
        anchorDeclaration: KtDeclaration,
        declarations: List<KtDeclaration>
    ) {
        declarations.fold(anchorDeclaration) { previous, declaration ->
            klass.addDeclarationAfter(declaration, previous).apply {
                reformat()
            }
        }
    }
}