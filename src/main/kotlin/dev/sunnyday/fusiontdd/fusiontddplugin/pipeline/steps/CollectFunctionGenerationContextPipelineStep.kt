package dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.steps

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.CommonProcessors
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.FunctionGenerationContext
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.PsiElementContentFilter
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.psi.hasOverrideModifier
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.psi.resolveClass
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings.FusionTDDSettings
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.PipelineStep
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinFindUsagesHandlerFactory
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinFunctionFindUsagesOptions
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import java.util.*

internal class CollectFunctionGenerationContextPipelineStep(
    private val targetFunction: KtNamedFunction,
    private val targetClass: KtClass,
    private val settings: FusionTDDSettings,
) : PipelineStep<Nothing?, FunctionGenerationContext> {

    private val logger: Logger = thisLogger()

    override fun execute(input: Nothing?, observer: (Result<FunctionGenerationContext>) -> Unit) {
        logger.debug("Pipeline: Collect tests and references for '${targetFunction.name}'")

        val collectTask = CollectFunctionGenerationContextPipelineTask(targetFunction, targetClass, settings)
        val result = runCatching(collectTask::execute)

        observer.invoke(result)
    }
}

private class CollectFunctionGenerationContextPipelineTask(
    private val targetFunction: KtNamedFunction,
    private val targetClass: KtClass,
    private val settings: FusionTDDSettings,
) {

    private val testAnnotationRegex = Regex("@.*?Test")

    private val tests = mutableMapOf<KtClass, MutableList<KtNamedFunction>>()

    private val branchPoints = IdentityHashMap<PsiElement, MutableSet<PsiElement>>()

    private val collectReferencesQueue: Deque<PsiElement> = ArrayDeque()

    private val referencedClasses = mutableSetOf<KtClass>()
    private val usedReferences = mutableSetOf<PsiElement>(targetFunction)
    private val branchFilters = mutableMapOf<PsiElement, PsiElementContentFilter>()

    fun execute(): FunctionGenerationContext {
        ProgressManager.getInstance().runProcess(
            /* process = */ Computable(::collectTestsWithBranchPointMarking),
            /* progress = */ EmptyProgressIndicator(),
        )
        referencedClasses.addAll(tests.keys)

        collectReferencesStartsFromTests()

        referencedClasses.add(targetClass)

        return FunctionGenerationContext(
            targetFunction = targetFunction,
            usedClasses = referencedClasses.toList(),
            usedReferences = usedReferences.toList(),
            tests = tests,
            branchFilters = branchFilters,
        )
    }

    private fun collectTestsWithBranchPointMarking() {
        val queue = ArrayDeque(listOf(targetFunction))
        val visited = mutableSetOf(targetFunction)

        getFunctionWithOverrides(targetFunction).let { functionWithOverrides ->
            queue.addAll(functionWithOverrides)
            visited.addAll(functionWithOverrides)
        }

        while (queue.isNotEmpty()) {
            val usages = findUsages(queue.removeFirst())
            proceedUsages(usages, queue, visited)
        }

        // Sort by real order
        tests.forEach { (_, tests) ->
            tests.sortBy(PsiElement::getTextOffset)
        }
    }

    private fun getFunctionWithOverrides(function: KtNamedFunction): List<KtNamedFunction> {
        return if (function.hasOverrideModifier()) {
            val functionWithOverrides = mutableListOf(function)

            val klass = requireNotNull(function.containingClass())
            val superClasses = getSuperClasses(klass)
            superClasses.forEach { superClass ->
                superClass.declarations.forEach { declaration ->
                    if (
                        declaration is KtNamedFunction &&
                        declaration.name == function.name &&
                        declaration.valueParameters == function.valueParameters
                    ) {
                        functionWithOverrides.add(declaration)
                    }
                }
            }

            functionWithOverrides
        } else {
            listOf(function)
        }
    }

    private fun getSuperClasses(klass: KtClass): List<KtClass> = buildList {
        val queue = ArrayDeque(listOf(klass))

        while (queue.isNotEmpty()) {
            val currentClass = queue.removeFirst()
            currentClass.superTypeListEntries.forEach { superTypeEntry ->
                superTypeEntry.resolveClass()?.let { superClass ->
                    add(superClass)
                    queue.add(superClass)
                }
            }
        }
    }

    private fun findUsages(function: KtNamedFunction): Collection<PsiReference> {
        val collectProcessor = CommonProcessors.CollectProcessor<UsageInfo>()

        KotlinFindUsagesHandlerFactory(function.project)
            .createFindUsagesHandler(function, false)
            .processElementUsages(
                /* element = */ function,
                /* processor = */ collectProcessor,
                /* options = */
                KotlinFunctionFindUsagesOptions(function.project).apply {
                    searchOverrides = false
                },
            )

        return collectProcessor.results.mapNotNull { it.reference }
    }

    private fun proceedUsages(
        usages: Collection<PsiReference>,
        queue: Deque<KtNamedFunction>,
        visited: MutableSet<KtNamedFunction>,
    ) {
        usages.forEach forEachUsage@{ usage ->
            val usageOwnerFunction = walkUpToCallerFunctionWithBranchPointMarking(usage.element)
                ?: return@forEachUsage

            getFunctionWithOverrides(usageOwnerFunction).forEach { function ->
                if (visited.add(function)) {
                    queue.addFirst(function)

                    if (hasTestAnnotation(function)) {
                        function.containingClass()?.let { testClass ->
                            tests.getOrPut(testClass, ::mutableListOf).add(function)
                        }
                    }
                }
            }
        }
    }

    private fun walkUpToCallerFunctionWithBranchPointMarking(element: PsiElement): KtNamedFunction? {
        var usagePathCursor = element.parent

        while (usagePathCursor != null && !usagePathCursor.isClassOrTopLevelFunction()) {
            val parent = usagePathCursor.parent

            when (parent) {
                is KtIfExpression -> {
                    branchPoints.getOrPut(parent, ::mutableSetOf)
                        .add(usagePathCursor)
                }

                is KtWhenExpression -> {
                    branchPoints.getOrPut(parent, ::mutableSetOf)
                        .add(usagePathCursor)
                }
            }

            usagePathCursor = parent
        }

        return usagePathCursor as? KtNamedFunction
    }

    private fun collectReferencesStartsFromTests() {
        collectReferencesQueue.addAll(tests.values.flatten())

        while (collectReferencesQueue.isNotEmpty()) {
            val usedElement = collectReferencesQueue.removeFirst()

            if (usedElement === targetFunction) continue
            usedReferences.add(usedElement)

            val ownerClass = PsiTreeUtil.getParentOfType(usedElement, KtClass::class.java)
            ownerClass?.let { usedClass ->
                if (usedClass !in tests && usedClass !== targetClass) {
                    if (usedClass.isScannable()) {
                        if (usedClass.isTopLevel()) {
                            referencedClasses.add(usedClass)
                        }
                    }
                }
            }

            if (usedElement.isScannable()) {
                collectNestedDependencies(usedElement)
            }
        }
    }

    private fun collectNestedDependencies(usedElement: PsiElement) {
        collectAnnotationReferences(usedElement)
        usedElement.accept(NestedDependenciesCollector())
    }

    private fun collectAnnotationReferences(usedElement: PsiElement) {
        when (usedElement) {
            is KtNamedFunction -> {
                usedElement.annotationEntries.forEach { annotationEntry ->
                    val userType = (annotationEntry.typeReference?.typeElement as? KtUserType) ?: return@forEach
                    val annotationClass =
                        userType.referenceExpression?.mainReference?.resolve() ?: return@forEach
                    usedReferences.add(annotationClass)
                }
            }
        }
    }

    private inner class NestedDependenciesCollector : PsiRecursiveElementWalkingVisitor() {

        private val visitorBranchFiltersStack = mutableListOf<VisitorBranchFilter>()

        private val hasActiveVisitorBranchFilter: Boolean
            get() = visitorBranchFiltersStack.isNotEmpty()

        private val isCurrentVisitorBranchActive: Boolean
            get() = visitorBranchFiltersStack.lastOrNull()?.activeBranch != null

        override fun visitElement(element: PsiElement) {
            if (hasActiveVisitorBranchFilter && !isCurrentVisitorBranchActive) {
                val branchFilter = visitorBranchFiltersStack.last()
                if (element in branchFilter.branches) {
                    branchFilter.activeBranch = element
                }
            }

            if (hasActiveVisitorBranchFilter && !isCurrentVisitorBranchActive) {
                super.visitElement(element)
                return
            }

            when (element) {
                in branchPoints -> onBranchPointElement(element)
                is KtNameReferenceExpression -> onReferenceExpression(element)
            }

            super.visitElement(element)
        }

        private fun onBranchPointElement(element: PsiElement) {
            val usedBranches = branchPoints[element] ?: return

            visitorBranchFiltersStack.add(
                VisitorBranchFilter(
                    root = element,
                    branches = usedBranches,
                )
            )

            when (element) {
                is KtIfExpression -> collectIfBranchFilter(element, usedBranches)
                is KtWhenExpression -> collectWhenBranchFilter(element, usedBranches)
            }
        }

        private fun collectIfBranchFilter(ifExpression: KtIfExpression, usedBranches: Set<PsiElement>) {
            if (usedBranches.size == 1) {
                branchFilters[ifExpression] = PsiElementContentFilter.If(
                    expression = ifExpression,
                    isThen = ifExpression.then == usedBranches.single().firstChild,
                )
            }
        }

        private fun collectWhenBranchFilter(whenExpression: KtWhenExpression, usedBranches: Set<PsiElement>) {
            if (usedBranches.size in 1..<whenExpression.entries.size) {
                branchFilters[whenExpression] = PsiElementContentFilter.When(
                    expression = whenExpression,
                    entries = whenExpression.entries.filter(usedBranches::contains),
                )
            }
        }

        private fun onReferenceExpression(element: KtNameReferenceExpression) {
            when (val reference = element.mainReference.resolve()) {
                targetClass, null -> Unit // no-op

                is KtClass -> onClassReference(reference)
                is KtConstructor<*> -> onConstructorReference(reference)
                is KtProperty -> onDeclarationReference(reference)
                is KtNamedFunction -> onDeclarationReference(reference)
            }
        }

        private fun onClassReference(reference: KtClass) {
            if (reference.isScannable() && reference.isTopLevel()) {
                referencedClasses.add(reference)
            } else {
                usedReferences.add(reference)
            }
        }

        private fun onConstructorReference(reference: KtConstructor<*>) {
            val constructedClass = reference.parent as? KtClass

            if (
                constructedClass != null &&
                constructedClass !== targetClass
            ) {
                usedReferences.add(reference)
                onClassReference(constructedClass)
            }
        }

        private fun onDeclarationReference(reference: KtDeclaration) {
            if (
                (reference.context is KtClassBody || reference.context is KtFile) &&
                usedReferences.add(reference)
            ) {
                if (reference.isScannable()) {
                    collectReferencesQueue.addFirst(reference)
                }
            }
        }

        override fun elementFinished(element: PsiElement?) {
            super.elementFinished(element)

            if (hasActiveVisitorBranchFilter) {
                val branchFilter = visitorBranchFiltersStack.last()
                when (element) {
                    branchFilter.root -> visitorBranchFiltersStack.removeLast()
                    branchFilter.activeBranch -> branchFilter.activeBranch = null
                }
            }
        }
    }

    private fun PsiElement?.isScannable(): Boolean {
        val fqName = this?.kotlinFqName?.toString().orEmpty()
        return fqName.startsWith(settings.projectPackage.orEmpty())
    }

    private fun PsiElement?.isClassOrTopLevelFunction(): Boolean {
        return this is KtNamedFunction && (context is KtClassBody || context is KtFile)
    }

    private fun hasTestAnnotation(function: KtNamedFunction): Boolean {
        return function.annotationEntries.any {
            val annotationText = it.text
            testAnnotationRegex.matches(annotationText)
        }
    }

    private class VisitorBranchFilter(
        val root: PsiElement,
        val branches: Set<PsiElement>,
        var activeBranch: PsiElement? = null,
    )
}