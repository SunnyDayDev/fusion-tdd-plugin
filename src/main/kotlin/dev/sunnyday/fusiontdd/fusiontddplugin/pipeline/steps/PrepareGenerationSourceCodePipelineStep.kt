package dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.steps

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.CodeBlock
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.FunctionTestDependencies
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings.FusionTDDSettings
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.PipelineStep
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.util.CommentSaver.Companion.tokenType
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

internal class PrepareGenerationSourceCodePipelineStep(
    private val settings: FusionTDDSettings,
) : PipelineStep<FunctionTestDependencies, CodeBlock> {

    private val testAnnotationRegex = Regex("@.*?Test")

    private val logger = thisLogger()

    override fun execute(input: FunctionTestDependencies, observer: (Result<CodeBlock>) -> Unit) {
        logger.debug("Pipeline: Prepare generation source for ${input.function.name}")

        val result = runCatching {
            val generationSourceString = buildString {
                printImports(input)
                printUsedClasses(input)
            }

            CodeBlock(generationSourceString)
        }

        observer.invoke(result)
    }

    private fun StringBuilder.printImports(input: FunctionTestDependencies) {
        val usedImports = input.usedReferences.mapNotNullTo(mutableSetOf()) { reference ->
            reference.kotlinFqName?.toString()
                ?.takeIf { !it.startsWith(settings.projectPackage.orEmpty()) }
        }

        val visitedFiles = mutableSetOf<KtFile>()
        input.usedClasses.forEach { klass ->
            val file = klass.containingKtFile
            if (visitedFiles.add(file)) {
                file.importDirectives.forEach { directive ->
                    val importPath = directive.importPath?.fqName?.toString()
                    if (importPath in usedImports) {
                        appendLine(directive.text)
                    }
                }
            }
        }
    }

    private fun StringBuilder.printUsedClasses(input: FunctionTestDependencies) {
        input.usedClasses.forEach { klass ->
            if (isNotEmpty()) {
                appendLine()
                appendLine()
            }

            printUsedClass(klass, input)
        }
    }

    private fun StringBuilder.printUsedClass(
        klass: KtClass,
        input: FunctionTestDependencies,
    ) {
        printClassTitleWithPrimaryConstructor(klass)

        var isEmpty = true

        klass.declarations.forEach { declaration ->
            if (declaration in input.usedReferences) {
                if (isEmpty) {
                    isEmpty = false
                    appendLine("{")
                }

                appendLine()
                append(declaration.getPreviousWhiteSpaceIndent())

                printClassDeclarationItem(declaration, input)
            }
        }

        if (!isEmpty) {
            append("}")
        } else {
            while (last() == ' ') {
                deleteAt(lastIndex)
            }
        }
    }

    private fun StringBuilder.printClassTitleWithPrimaryConstructor(klass: KtClass) {
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

    private fun StringBuilder.printClassDeclarationItem(declaration: KtDeclaration, input: FunctionTestDependencies) {
        when (declaration) {
            is KtClass -> {
                printUsedClass(declaration, input)
                appendLine()
            }

            else -> {
                if (declaration == input.function) {
                    printTargetFunction(input)
                } else {
                    appendLine(declaration.text)
                }
            }
        }
    }

    private fun StringBuilder.printTargetFunction(input: FunctionTestDependencies) {
        var isTestCommentsAdded = !settings.isAddTestCommentsBeforeGeneration
        var functionElement = input.function.firstChild

        while (functionElement != null && functionElement.nextSibling != null) {
            if (!isTestCommentsAdded) {
                isTestCommentsAdded = printTargetFunctionTestCommentsIfNeedIt(functionElement, input)
            }

            append(functionElement.text)

            functionElement = functionElement.nextSibling
        }

        when (functionElement) {
            is KtBlockExpression -> printTargetFunctionBody(functionElement)
        }
    }

    private fun StringBuilder.printTargetFunctionTestCommentsIfNeedIt(
        functionElement: PsiElement,
        input: FunctionTestDependencies,
    ): Boolean {
        val isFirstFunDeclarationElement = functionElement is KtModifierList ||
                functionElement.tokenType == KtTokens.FUN_KEYWORD

        return if (isFirstFunDeclarationElement && settings.isAddTestCommentsBeforeGeneration) {
            printTargetFunctionTestComments(input, functionElement)
            true
        } else {
            false
        }
    }

    private fun StringBuilder.printTargetFunctionTestComments(
        input: FunctionTestDependencies,
        firstFunDeclarationElement: PsiElement,
    ) {
        val functionIndentWhiteSpace = input.function.getPreviousWhiteSpaceIndent()

        val usedReferencesSet = input.usedReferences.toSet()

        val testTitles = input.testClass.declarations
            .filter { it is KtNamedFunction && it in usedReferencesSet && hasTestAnnotation(it) }
            .map { it.name }

        if (testTitles.isNotEmpty()) {
            if (firstFunDeclarationElement.prevSibling?.prevSibling is KDoc) {
                deleteRange(lastIndexOf("/"), length)
                appendLine()
            } else {
                appendLine("/**")
            }

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

    private fun StringBuilder.printTargetFunctionBody(functionElement: KtBlockExpression) {
        appendLine(functionElement.lBrace?.text.orEmpty())

        val indentWhiteSpace = (functionElement.lBrace?.nextSibling as? PsiWhiteSpace)
            ?.text?.substringAfterLast('\n')

        append(indentWhiteSpace)
        appendLine(CodeBlock.GENERATE_HERE_TAG)

        append(functionElement.rBrace?.getPreviousWhiteSpaceIndent().orEmpty())
        appendLine(functionElement.rBrace?.text.orEmpty())
    }

    private fun PsiElement.getPreviousWhiteSpaceIndent(): String {
        return (prevSibling as? PsiWhiteSpace)?.text?.substringAfterLast('\n').orEmpty()
    }
}