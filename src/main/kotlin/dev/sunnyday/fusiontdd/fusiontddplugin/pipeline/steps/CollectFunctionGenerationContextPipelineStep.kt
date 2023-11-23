package dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.steps

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.FunctionGenerationContext
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.PsiElementContentFilter
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings.FusionTDDSettings
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.PipelineStep
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.util.projectScope
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

        val collectTask = CollectTask()
        val result = runCatching(collectTask::execute)

        observer.invoke(result)
    }

    private inner class CollectTask {

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

            while (queue.isNotEmpty()) {
                val usages = ReferencesSearch.search(queue.removeFirst(), targetFunction.project.projectScope())
                    .findAll()

                usages.forEach { usage ->
                    val callerFunction = walkUpToCallerFunctionWithBranchPointMarking(usage.element)
                    if (callerFunction != null && visited.add(callerFunction)) {
                        queue.addFirst(callerFunction)

                        if (hasTestAnnotation(callerFunction)) {
                            callerFunction.containingClass()?.let { testClass ->
                                tests.getOrPut(testClass, ::mutableListOf).add(callerFunction)
                            }
                        }
                    }
                }
            }

            // Sort by real order
            tests.forEach { (_, tests) ->
                tests.sortBy(PsiElement::getTextOffset)
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
                    is KtIfExpression -> {
                        if (usedBranches.size == 1) {
                            branchFilters[element] = PsiElementContentFilter.If(
                                expression = element,
                                isThen = element.then == usedBranches.single().firstChild,
                            )
                        }
                    }
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
                if (reference.isScannable()) {
                    if (reference.isTopLevel()) {
                        referencedClasses.add(reference)
                    }
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

                    if (constructedClass.isScannable() && constructedClass.isTopLevel()) {
                        referencedClasses.add(constructedClass)
                    } else {
                        usedReferences.add(constructedClass)
                    }
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
    }

    private class VisitorBranchFilter(
        val root: PsiElement,
        val branches: Set<PsiElement>,
        var activeBranch: PsiElement? = null,
    )
}