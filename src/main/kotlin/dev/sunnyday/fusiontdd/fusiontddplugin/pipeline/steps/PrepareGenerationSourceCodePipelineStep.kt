package dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.steps

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.psi.PsiWhiteSpace
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.CodeBlock
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.FunctionTestDependencies
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings.FusionTDDSettings
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.PipelineStep
import org.jetbrains.kotlin.idea.base.utils.fqname.getKotlinFqName
import org.jetbrains.kotlin.psi.*

internal class PrepareGenerationSourceCodePipelineStep(
    private val settings: FusionTDDSettings,
) : PipelineStep<FunctionTestDependencies, CodeBlock> {

    private val testAnnotationRegex = Regex("@.*?Test")

    private val logger = thisLogger()

    override fun execute(input: FunctionTestDependencies, observer: (Result<CodeBlock>) -> Unit) {
        logger.debug("Pipeline: Prepare generation source for ${input.function.name}")

        val result = runCatching {
            val testClassString = buildString {
                printImports(input)
                printClasses(input)
            }

            CodeBlock(testClassString)
        }

        observer.invoke(result)
    }

    private fun StringBuilder.printImports(input: FunctionTestDependencies) {
        val usedImports = input.usedReferences.mapNotNullTo(mutableSetOf()) { reference ->
            reference.getKotlinFqName()?.toString()
                ?.takeIf { !it.startsWith(settings.projectPackage.orEmpty()) }
        }

        val visitedFiles = mutableSetOf<KtFile>()
        input.usedClasses.forEach { klass ->
            val file = klass.containingKtFile
            if (visitedFiles.add(file)) {
                file.importDirectives.forEach { directive ->
                    if (directive.importPath?.fqName?.toString() in usedImports) {
                        appendLine(directive.text)
                    }
                }
            }
        }
    }

    private fun StringBuilder.printClasses(input: FunctionTestDependencies) {
        input.usedClasses.forEach { klass ->
            if (isNotEmpty()) {
                appendLine()
                appendLine()
            }

            printClass(klass, input)
        }
    }

    private fun StringBuilder.printClass(klass: KtClass, input: FunctionTestDependencies) {
        printClassTitle(klass)

        appendLine("{")
        var isEmpty = true

        klass.declarations.forEach { declaration ->
            if (declaration in input.usedReferences) {
                isEmpty = false

                appendLine()

                if (declaration.prevSibling is PsiWhiteSpace) {
                    append(declaration.prevSibling.text.substringAfterLast('\n'))
                }

                if (declaration == input.function) {
                    printTargetFunction(input)
                } else {
                    appendLine(declaration.text)
                }
            }
        }

        if (isEmpty) {
            deleteAt(lastIndex)
        }

        append("}")
    }

    private fun StringBuilder.printClassTitle(klass: KtClass) {
        var titleElement = klass.firstChild
        while (titleElement !== klass.body) {
            if (
                titleElement !is KtDeclarationModifierList &&
                titleElement.prevSibling !is KtDeclarationModifierList
            ) {
                append(titleElement.text)
            }
            titleElement = titleElement.nextSibling
        }
    }

    private fun StringBuilder.printTargetFunction(input: FunctionTestDependencies) {
        if (settings.isAddTestCommentsBeforeGeneration) {
            printTargetFunctionComment(input)
        }

        var functionElement = input.function.firstChild
        while (functionElement != null && functionElement !is KtBlockExpression) {
            append(functionElement.text)

            functionElement = functionElement.nextSibling
        }

        if (functionElement !is KtBlockExpression) {
            return
        }

        appendLine(functionElement.lBrace?.text.orEmpty())

        val indentWhiteSpace = (functionElement.lBrace?.nextSibling as? PsiWhiteSpace)
            ?.text?.substringAfterLast('\n')

        append(indentWhiteSpace)
        appendLine(CodeBlock.GENERATE_HERE_TAG)

        val rBraceIndentWhiteSpace =
            (functionElement.rBrace?.prevSibling as? PsiWhiteSpace)
                ?.text?.substringAfterLast('\n')
        append(rBraceIndentWhiteSpace)
        appendLine(functionElement.rBrace?.text.orEmpty())
    }

    private fun StringBuilder.printTargetFunctionComment(
        input: FunctionTestDependencies,
    ) {
        val functionIndentWhiteSpace = (input.function.prevSibling as? PsiWhiteSpace)
            ?.text?.substringAfterLast('\n')

        val usedReferencesSet = input.usedReferences.toSet()

        val testTitles = input.testClass.declarations
            .filter { it is KtNamedFunction && it in usedReferencesSet && hasTestAnnotation(it) }
            .map { it.name }

        if (testTitles.isNotEmpty()) {
            appendLine("/**")

            testTitles.forEach { testTitle ->
                append(functionIndentWhiteSpace)
                append(" * ")
                appendLine(testTitle)
            }

            append(functionIndentWhiteSpace)
            appendLine(" */")
            append(functionIndentWhiteSpace)
        }
    }

    private fun hasTestAnnotation(function: KtNamedFunction): Boolean {
        return function.annotationEntries.any {
            val annotationText = it.text
            testAnnotationRegex.matches(annotationText)
        }
    }
}