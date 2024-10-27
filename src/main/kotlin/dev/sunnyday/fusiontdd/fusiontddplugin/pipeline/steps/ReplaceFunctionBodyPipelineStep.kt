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

@Suppress("UnstableApiUsage")
internal class ReplaceFunctionBodyPipelineStep(
    private val targetElement: KtDeclaration,
) : PipelineStep<GenerateCodeBlockResult, KtDeclaration> {

    private val logger = thisLogger()

    private val psiFactory: KtPsiFactory by lazy {
        KtPsiFactory(targetElement.project, markGenerated = false)
    }

    override fun execute(input: GenerateCodeBlockResult, observer: (Result<KtDeclaration>) -> Unit) {
        val result = runCatching {
            val generatedDeclarations: List<KtDeclaration> = getGeneratedDeclarations(input)
            replaceTargetFunWithGenerationResult(generatedDeclarations)
        }

        observer.invoke(result)
    }

    private fun getGeneratedDeclarations(generationResult: GenerateCodeBlockResult): List<KtDeclaration> {
        return application.runReadAction(
            Computable {
                logger.debug("Pipeline: Replace function body of ${targetElement.name}")

                val resultCode = createResultBody(generationResult)

                psiFactory.createFile(resultCode).declarations
                    // TODO: Better junk filter. Remove unnecessary classes (which already present in main source set)
                    .filter { it !is KtClassOrObject || it.name == targetElement.name }
                    .map { psiFactory.createDeclaration(it.text) }
            }
        )
    }

    private fun createResultBody(generationResult: GenerateCodeBlockResult): String {
        // TODO: describe in requirements (tests)
        val generatedBody = generationResult.variants.firstOrNull()?.rawText.orEmpty()
            .substringBefore("package")
            .substringBefore("// file")
            .substringBefore("//file")

        return buildString {
            val targetFunctionWhiteSpace = (targetElement.prevSibling as? PsiWhiteSpace)
                ?.text?.lines()?.last().orEmpty()

            append(targetFunctionWhiteSpace)

            var printCursor = targetElement.firstChild
            while (
                printCursor !is KtBlockExpression &&
                printCursor !is KtClassBody &&
                printCursor.tokenType != KtTokens.EQ
            ) {
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

    private fun replaceTargetFunWithGenerationResult(generatedDeclarations: List<KtDeclaration>): KtDeclaration {
        lateinit var result: KtDeclaration

        application.invokeAndWait {
            WriteCommandAction.runWriteCommandAction(targetElement.project) {
                result = when (targetElement) {
                    is KtClass -> replaceClass(
                        targetElement = targetElement,
                        generatedDeclarations = generatedDeclarations,
                    )

                    is KtNamedFunction -> replaceFunction(
                        targetElement = targetElement,
                        klass = targetElement.containingClass() as KtClassOrObject,
                        generatedDeclarations = generatedDeclarations,
                    )

                    else -> error("Unexpected target element type: ${targetElement::class.simpleName}")
                }
            }
        }

        return result
    }

    private fun replaceClass(
        targetElement: KtClass,
        generatedDeclarations: List<KtDeclaration>,
    ): KtDeclaration {
        val generatedBody = requireNotNull((generatedDeclarations.first() as KtClassOrObject).body)
        val body = application.runReadAction(Computable { targetElement.getOrCreateBody() })
        body.replace(generatedBody)
        generatedBody.reformat(true)

        return targetElement
    }

    private fun replaceFunction(
        targetElement: KtNamedFunction,
        klass: KtClassOrObject,
        generatedDeclarations: List<KtDeclaration>,
    ): KtDeclaration {
        val replacedFunction = targetElement.replace(generatedDeclarations.first()) as KtDeclaration
        replacedFunction.reformat(true)

        addDeclarationsAfter(klass, replacedFunction, generatedDeclarations.drop(1))

        return replacedFunction
    }

    private fun addDeclarationsAfter(
        klass: KtClassOrObject,
        anchorDeclaration: KtDeclaration,
        declarations: List<KtDeclaration>,
    ) {
        declarations.fold(anchorDeclaration) { previous, declaration ->
            klass.addDeclarationAfter(declaration, previous).apply {
                reformat(true)
            }
        }
    }
}