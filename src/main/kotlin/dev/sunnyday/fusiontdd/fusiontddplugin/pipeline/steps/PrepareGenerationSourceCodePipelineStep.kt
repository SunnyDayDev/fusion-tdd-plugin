package dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.steps

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.PsiWhiteSpace
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.CodeBlock
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.FunctionGenerationContext
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.PsiElementContentFilter
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings.FusionTDDSettings
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.PipelineStep
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.util.CommentSaver.Companion.tokenType
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

internal class PrepareGenerationSourceCodePipelineStep(
    private val settings: FusionTDDSettings,
) : PipelineStep<FunctionGenerationContext, CodeBlock> {

    private val testAnnotationRegex = Regex("@.*?Test")

    private val logger = thisLogger()

    override fun execute(input: FunctionGenerationContext, observer: (Result<CodeBlock>) -> Unit) {
        logger.debug("Pipeline: Prepare generation source for ${input.targetFunction?.name}")

        val result = runCatching {
            val generationSourceString = buildString {
                printImports(input)
                printUsedClasses(input)
            }

            CodeBlock(generationSourceString)
        }

        observer.invoke(result)
    }

    private fun StringBuilder.printImports(input: FunctionGenerationContext) {
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

    private fun StringBuilder.printUsedClasses(input: FunctionGenerationContext) {
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
        input: FunctionGenerationContext,
    ) {
        printClassTitleWithPrimaryConstructor(klass)

        var isEmpty = true

        klass.declarations.forEach { declaration ->
            if (declaration in input.usedReferences) {
                if (isEmpty) {
                    isEmpty = false
                    appendLine("{")
                }

                if (declaration !is KtEnumEntry) appendLine()

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
            when (titleElement) {
                is KtDeclarationModifierList -> printClassTitleDeclarationModifierList(titleElement)
                is PsiWhiteSpace -> if (!last().isWhitespace()) append(titleElement.text)
                else -> append(titleElement.text)
            }

            titleElement = titleElement.nextSibling
        }
    }

    private fun StringBuilder.printClassTitleDeclarationModifierList(modifierList: KtModifierList) {
        when {
            modifierList.hasModifier(KtTokens.ENUM_KEYWORD) -> append("enum")
            modifierList.hasModifier(KtTokens.DATA_KEYWORD) -> append("data")
            modifierList.hasModifier(KtTokens.VALUE_KEYWORD) -> append("value")
        }
    }

    private fun StringBuilder.printClassDeclarationItem(declaration: KtDeclaration, input: FunctionGenerationContext) {
        when (declaration) {
            is KtClass -> {
                printUsedClass(declaration, input)
                appendLine()
            }

            else -> {
                if (declaration == input.targetFunction) {
                    printTargetFunction(input.targetFunction, input)
                } else {
                    printFilterableElement(declaration, input)
                }
            }
        }
    }

    private fun StringBuilder.printTargetFunction(targetFunction: KtFunction, input: FunctionGenerationContext) {
        var isTestCommentsAdded = !settings.isAddTestCommentsBeforeGeneration
        var functionElement = targetFunction.firstChild

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
        input: FunctionGenerationContext,
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
        input: FunctionGenerationContext,
        firstFunDeclarationElement: PsiElement,
    ) {
        val functionIndentWhiteSpace = input.targetFunction?.getPreviousWhiteSpaceIndent().orEmpty()

        val testTitles = input.tests.values
            .flatten()
            .mapNotNull(KtNamedFunction::getName)

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

    private fun StringBuilder.printTargetFunctionBody(functionElement: KtBlockExpression) {
        appendLine(functionElement.lBrace?.text.orEmpty())

        val indentWhiteSpace = (functionElement.lBrace?.nextSibling as? PsiWhiteSpace)
            ?.text?.substringAfterLast('\n')

        append(indentWhiteSpace)
        appendLine(CodeBlock.GENERATE_HERE_TAG)

        append(functionElement.rBrace?.getPreviousWhiteSpaceIndent().orEmpty())
        appendLine(functionElement.rBrace?.text.orEmpty())
    }

    private fun StringBuilder.printFilterableElement(
        filterableElement: PsiElement,
        input: FunctionGenerationContext,
    ) {
        filterableElement.accept(PrintFilterableElementVisitor(this, input))
        appendLine()
    }

    private class PrintFilterableElementVisitor(
        private val builder: StringBuilder,
        private val input: FunctionGenerationContext,
    ) : PrintLeafVisitor(builder) {

        override fun onVisitPrintableElement(element: PsiElement) {
            val filter = input.branchFilters[element]

            if (filter != null) {
                onPsiElementContentFilter(filter)
                skipElement()
            }
        }

        private fun onPsiElementContentFilter(filter: PsiElementContentFilter) {
            when (filter) {
                is PsiElementContentFilter.If -> onIfBranchFilter(filter)
            }
        }

        private fun onIfBranchFilter(filter: PsiElementContentFilter.If) {
            requireNotNull(filter.expression).accept(IfBranchFilterVisitor(builder, filter, input))
            skipElement()
        }
    }

    private class IfBranchFilterVisitor(
        private val builder: StringBuilder,
        private val filter: PsiElementContentFilter.If,
        private val input: FunctionGenerationContext,
    ) : PrintLeafVisitor(builder) {

        private val filterUsedBranch = if (filter.isThen) filter.expression.then else filter.expression.`else`

        override fun onVisitPrintableElement(element: PsiElement) {
            when (element) {
                // Transform condition
                filter.expression.condition -> {
                    printFilteredIfCondition()
                    skipElement()
                }

                // Print used branch as other
                filterUsedBranch -> {
                    element.accept(PrintFilterableElementVisitor(builder, input))
                    skipElement()
                }

                // Skip else keyword
                filter.expression.elseKeyword -> skipElement()

                // Skip other branch
                filter.expression.then,
                filter.expression.`else` -> {
                    skipElement()
                }

                // Skip unnecessary whitespaces
                is PsiWhiteSpace -> {
                    if (
                        builder.endsWith(' ') ||
                        element.nextSibling === filter.expression.elseKeyword ||
                        element.prevSibling === filter.expression.elseKeyword
                    ) {
                        skipElement()
                    }
                }
            }
        }

        // TODO: use PsiElement api instead of String
        // TODO: optimize expression else branch transformations (like `some > other` -> `some <= other`)
        private fun printFilteredIfCondition() {
            val condition = requireNotNull(filter.expression.condition)
            val conditionString = buildString { condition.accept(PrintLeafVisitor(this)) }
                .replace("\n", "")
                .replace(Regex("! +"), "!")
                .trim()

            if (filter.isThen) {
                builder.append(conditionString)
            } else {
                if (conditionString.startsWith("!")) {
                    if (conditionString[1] == '(') {
                        builder.append(conditionString.substring(2, conditionString.lastIndex))
                    } else {
                        builder.append(conditionString.substring(1, conditionString.length))
                    }
                } else {
                    builder.append('!')
                    if (condition.children.size > 1) {
                        builder.append('(')
                        builder.append(conditionString)
                        builder.append(')')
                    } else {
                        builder.append(conditionString)
                    }
                }
            }
        }
    }

    private open class PrintLeafVisitor(
        private val builder: StringBuilder
    ) : PsiRecursiveElementWalkingVisitor() {

        private var skipElement: PsiElement? = null

        private var currentElement: PsiElement? = null

        protected fun skipElement() {
            skipElement = currentElement
        }

        override fun visitElement(element: PsiElement) {
            currentElement = element

            if (skipElement != null) {
                super.visitElement(element)
                return
            }

            onVisitPrintableElement(element)

            if (skipElement != null) {
                super.visitElement(element)
                return
            }

            if (element.firstChild == null) {
                builder.append(element.text)
            }

            super.visitElement(element)
        }

        protected open fun onVisitPrintableElement(element: PsiElement) = Unit

        override fun elementFinished(element: PsiElement?) {
            super.elementFinished(element)

            if (element === skipElement) {
                skipElement = null
            }
        }
    }

    private fun PsiElement.getPreviousWhiteSpaceIndent(): String {
        return (prevSibling as? PsiWhiteSpace)?.text?.substringAfterLast('\n').orEmpty()
    }
}