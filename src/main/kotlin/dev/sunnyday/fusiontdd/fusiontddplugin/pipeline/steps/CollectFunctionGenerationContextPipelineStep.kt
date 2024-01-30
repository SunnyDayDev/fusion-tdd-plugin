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
import com.intellij.psi.util.parentOfType
import com.intellij.usageView.UsageInfo
import com.intellij.util.CommonProcessors
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.FunctionGenerationContext
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.PsiElementContentFilter
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.psi.*
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings.FusionTDDSettings
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.PipelineStep
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinFindUsagesHandlerFactory
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinFunctionFindUsagesOptions
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex
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

    private val referencedClasses = mutableSetOf<KtClassOrObject>()
    private val usedReferences = mutableSetOf<PsiElement>(targetFunction)
    private val branchFilters = mutableMapOf<PsiElement, PsiElementContentFilter>()

    private val referencedClassesCollector = mutableSetOf<KtClassOrObject>()
    private val usedReferencesCollector = mutableSetOf<PsiElement>()
    private val branchFiltersCollector = mutableMapOf<PsiElement, PsiElementContentFilter>()

    private val functionUsageRequirements = mutableMapOf<KtNamedFunction, FunctionRequirement>()
    private val usageToFunction = mutableMapOf<PsiElement, KtNamedFunction>()

    private val superClassesCache = mutableMapOf<KtClassOrObject, Set<KtClass>>()

    private val receiverInstanceClassResolver = ExpressionResultInstanceClassResolver()

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
        val queue = ArrayDeque<Pair<KtNamedFunction, KtClass?>>()
        val visited = mutableSetOf(targetFunction)

        getFunctionWithOverrides(targetFunction).let { functionWithOverrides ->
            functionWithOverrides.forEach { function ->
                queue.add(function to targetClass)
            }
            visited.addAll(functionWithOverrides)
        }

        while (queue.isNotEmpty()) {
            val (function, functionClass) = queue.removeFirst()
            val usages = findUsages(function, functionClass)
            usages.forEach { usage -> usageToFunction[usage.element] = function }
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
                    if (declaration is KtNamedFunction && isEqualSignature(declaration, function)) {
                        functionWithOverrides.add(declaration)
                    }
                }
            }

            functionWithOverrides
        } else {
            listOf(function)
        }
    }

    private fun isEqualSignature(function1: KtNamedFunction, function2: KtNamedFunction): Boolean {
        return function1.name == function2.name &&
                function1.valueParameters == function2.valueParameters
    }

    private fun getSuperClasses(klass: KtClassOrObject): Set<KtClass> {
        return superClassesCache.getOrPut(klass) {
            buildSet {
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
        }
    }

    private fun findUsages(function: KtNamedFunction, functionClass: KtClass?): List<PsiReference> {
        return findAnyUsages(function).mapNotNull { usageInfo ->
            usageInfo.reference.takeIf {
                isPossibleUsage(usageInfo, functionClass)
            }
        }
    }

    private fun findAnyUsages(function: KtNamedFunction): Collection<UsageInfo> {
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

        return collectProcessor.results
    }

    private fun isPossibleUsage(usageInfo: UsageInfo, functionClass: KtClass?): Boolean {
        if (functionClass == null) return true

        val receiverClass = getUsageReceiverClass(usageInfo)
        return receiverClass == null ||
                receiverClass === functionClass ||
                getSuperClasses(receiverClass).contains(functionClass)
    }

    // TODO: getUsageReceiverClass, other usage cases (not only KtDotQualifiedExpression)
    private fun getUsageReceiverClass(usageInfo: UsageInfo): KtClassOrObject? {
        val usageElementContext = usageInfo.element?.context

        return when (val usageContext = usageElementContext?.context) {
            is KtDotQualifiedExpression -> {
                val usageFunctionScope = usageInfo.element?.parentOfType<KtNamedFunction>()
                receiverInstanceClassResolver.resolveClass(
                    expression = usageContext.receiverExpression,
                    stopConditions = usageFunctionScope?.let { scopeFunction ->
                        listOf(
                            ExpressionResultInstanceClassResolver.StopCondition.FunctionParameter(scopeFunction),
                        )
                    },
                )
            }

            else -> null
        }
    }

    private fun proceedUsages(
        usages: Collection<PsiReference>,
        queue: Deque<Pair<KtNamedFunction, KtClass?>>,
        visited: MutableSet<KtNamedFunction>,
    ) {
        usages.forEach forEachUsage@{ usage ->
            val usageOwnerFunction = walkUpToCallerFunctionWithBranchPointMarking(usage.element)
                ?: return@forEachUsage

            getFunctionWithOverrides(usageOwnerFunction).forEach { function ->
                if (visited.add(function)) {
                    queue.addFirst(function to function.containingClass())

                    if (hasTestAnnotation(function)) {
                        function.containingClass()?.let { testClass ->
                            if (checkIsTestFitCallChainRequirements(function)) {
                                tests.getOrPut(testClass, ::mutableListOf).add(function)
                            }
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
                is KtIfExpression -> onWalkUpToCallerFunctionMeetIfExpression(parent, usagePathCursor)
                is KtWhenExpression -> onWalkUpToCallerFunctionMeetWhenExpression(parent, usagePathCursor)
            }

            usagePathCursor = parent
        }

        return usagePathCursor as? KtNamedFunction
    }

    private fun onWalkUpToCallerFunctionMeetIfExpression(ifExpression: KtIfExpression, branch: PsiElement) {
        branchPoints.getOrPut(ifExpression, ::mutableSetOf)
            .add(branch)
    }

    private fun onWalkUpToCallerFunctionMeetWhenExpression(whenExpression: KtWhenExpression, branch: PsiElement) {
        val whenEntry = branch as KtWhenEntry

        addRequirementIfHas(whenExpression, whenEntry)

        branchPoints.getOrPut(whenExpression, ::mutableSetOf)
            .add(whenEntry)
    }

    private fun addRequirementIfHas(whenExpression: KtWhenExpression, whenEntry: KtWhenEntry) {
        val whenFunctionScope = whenExpression.parentOfType<KtNamedFunction>() ?: return
        val usedInWhenParameter = receiverInstanceClassResolver.resolveExpression(
            expression = whenExpression.subjectExpression,
            stopConditions = listOf(
                ExpressionResultInstanceClassResolver.StopCondition.FunctionParameter(whenFunctionScope),
            ),
        )

        if (usedInWhenParameter is KtParameter && usedInWhenParameter.ownerFunction === whenFunctionScope) {
            val parameterRequirement = if (whenEntry.elseKeyword != null) {
                collectWhenElseCaseRequiredClassOrObjectsWithNegotiation(whenExpression)
            } else {
                collectWhenCaseEntryRequiredClassOrObjectsWithNegotiation(whenEntry)
            }

            if (parameterRequirement == null) {
                return
            }

            val functionRequirement = functionUsageRequirements.getOrPut(whenFunctionScope) {
                FunctionRequirement()
            }

            functionRequirement.addParameterRequirement(usedInWhenParameter, parameterRequirement)
        }
    }

    private fun collectWhenElseCaseRequiredClassOrObjectsWithNegotiation(
        whenExpression: KtWhenExpression,
    ): ParameterRequirement? {
        if (!isAllEntriesHasRequirements(whenExpression)) {
            return null
        }

        val negatedTypeRequirements = mutableListOf<TypeRequirement>()
        val nonNegatedTypeRequirements = mutableListOf<TypeRequirement>()

        whenExpression.entries.forEach { whenEntry ->
            val positiveRequirements = collectWhenCaseEntryRequiredClassOrObjectsWithNegotiation(whenEntry)
                ?.requirements
                ?: return null

            positiveRequirements.map { (requiredClassOrObject, isNegated) ->
                val requirement = TypeRequirement(requiredClassOrObject, !isNegated)
                if (requirement.isNegated) {
                    negatedTypeRequirements.add(requirement)
                } else {
                    nonNegatedTypeRequirements.add(requirement)
                }
            }
        }

        return ParameterRequirement.Composite(
            buildList {
                if (negatedTypeRequirements.isNotEmpty()) {
                    add(ParameterRequirement.All(negatedTypeRequirements))
                }
                if (nonNegatedTypeRequirements.isNotEmpty()) {
                    add(ParameterRequirement.AnyOf(nonNegatedTypeRequirements))
                }
            }
        )
    }

    private fun isAllEntriesHasRequirements(whenExpression: KtWhenExpression): Boolean {
        return whenExpression.entries.all { whenEntry ->
            whenEntry.conditions.all { condition ->
                condition is KtWhenConditionIsPattern || condition is KtWhenConditionWithExpression
            }
        }
    }

    private fun collectWhenCaseEntryRequiredClassOrObjectsWithNegotiation(
        whenEntry: KtWhenEntry
    ): ParameterRequirement.AnyOf? {
        val typeRequirements = whenEntry.conditions.map { condition ->
            val typeRequirement = when (condition) {
                is KtWhenConditionIsPattern -> getKtWhenConditionIsPatternTypeRequirement(condition)
                is KtWhenConditionWithExpression -> getKtWhenConditionWithExpressionTypeRequirement(condition)
                else -> null
            }

            // Only if all conditions can be transformed to requirements use them
            typeRequirement ?: return null
        }

        return ParameterRequirement.AnyOf(typeRequirements)
    }

    private fun getKtWhenConditionIsPatternTypeRequirement(condition: KtWhenConditionIsPattern): TypeRequirement? {
        return condition.typeReference?.resolve()?.let { classOrObject ->
            TypeRequirement(classOrObject, condition.isNegated)
        }
    }

    private fun getKtWhenConditionWithExpressionTypeRequirement(
        condition: KtWhenConditionWithExpression
    ): TypeRequirement? {
        var expression = condition.expression
        while (expression is KtDotQualifiedExpression) {
            expression = expression.selectorExpression
        }

        if (expression is KtConstructor<*>) {
            return null
        }

        return (expression?.mainReference?.resolve() as? KtClassOrObject)
            ?.let { TypeRequirement(it, false) }
    }

    private fun checkIsTestFitCallChainRequirements(
        function: KtNamedFunction,
        resolvedParameters: Map<KtParameter, KtClassOrObject?> = emptyMap()
    ): Boolean {
        if (!checkPassRequirements(function, resolvedParameters)) {
            return false
        }

        val functionBody = function.bodyExpression
            ?: return true

        return functionBody.acceptAll { element ->
            val callExpression = element as? KtCallExpression
                ?: return@acceptAll true

            val calledFunction = (callExpression.calleeExpression as? KtNameReferenceExpression)
                ?.mainReference?.resolve() as? KtNamedFunction
                ?: return@acceptAll true

            val callResolvedParameters = resolveFunctionCallParameters(
                callExpression = callExpression,
                function = calledFunction,
                resolvedParameters = resolvedParameters,
            )

            checkIsTestFitCallChainRequirements(
                function = calledFunction,
                resolvedParameters = callResolvedParameters,
            )
        }
    }

    private fun checkPassRequirements(
        function: KtNamedFunction,
        resolvedParameters: Map<KtParameter, KtClassOrObject?>,
    ): Boolean {
        val functionRequirement = functionUsageRequirements[function] ?: return true

        return function.valueParameters.all { parameter ->
            val resolvedParameterClassOrObject = resolvedParameters[parameter]
                ?: return@all true

            val parameterRequirements = functionRequirement.getParameterRequirements(parameter)
                ?: return@all true

            isParameterClassPassRequirements(resolvedParameterClassOrObject, parameterRequirements)
        }
    }

    private fun isParameterClassPassRequirements(
        parameterClass: KtClassOrObject,
        parameterRequirements: List<ParameterRequirement>,
    ): Boolean {
        val parameterClasses = buildSet {
            add(parameterClass)
            addAll(getSuperClasses(parameterClass))
        }

        return isParameterClassPassRequirements(parameterClasses, parameterRequirements)
    }

    private fun isParameterClassPassRequirements(
        parameterClasses: Set<KtClassOrObject>,
        parameterRequirements: List<ParameterRequirement>,
    ): Boolean {
        return parameterRequirements.all { requirement ->
            when (requirement) {
                is ParameterRequirement.All -> isParameterClassesPassAllRequirement(parameterClasses, requirement)
                is ParameterRequirement.AnyOf -> isParameterClassesPassAnyOfRequirement(parameterClasses, requirement)

                is ParameterRequirement.Composite -> isParameterClassPassRequirements(
                    parameterClasses = parameterClasses,
                    parameterRequirements = requirement.requirements,
                )
            }
        }
    }

    private fun isParameterClassesPassAllRequirement(
        parameterClasses: Set<KtClassOrObject>,
        parameterRequirement: ParameterRequirement.All,
    ): Boolean {
        return parameterRequirement.requirements.all { typeRequirement ->
            isParameterClassesPassTypeRequirement(
                parameterClasses = parameterClasses,
                typeRequirement = typeRequirement,
            )
        }
    }

    private fun isParameterClassesPassAnyOfRequirement(
        parameterClasses: Set<KtClassOrObject>,
        parameterRequirement: ParameterRequirement.AnyOf,
    ): Boolean {
        return parameterRequirement.requirements.any { typeRequirement ->
            isParameterClassesPassTypeRequirement(
                parameterClasses = parameterClasses,
                typeRequirement = typeRequirement,
            )
        }
    }

    private fun isParameterClassesPassTypeRequirement(
        parameterClasses: Set<KtClassOrObject>,
        typeRequirement: TypeRequirement,
    ): Boolean {
        val parameterIsInstanceOfRequiredType = typeRequirement.classOrObject in parameterClasses
        return parameterIsInstanceOfRequiredType xor typeRequirement.isNegated
    }

    private fun resolveFunctionCallParameters(
        callExpression: KtCallExpression,
        function: KtNamedFunction,
        resolvedParameters: Map<KtParameter, KtClassOrObject?>,
    ): Map<KtParameter, KtClassOrObject?> {
        val namedArgs = callExpression.valueArguments.associateBy { argument ->
            argument.getArgumentName()?.name
        }

        return function.valueParameters.associateWith { parameter ->
            val argument = namedArgs[parameter.name]
                ?: callExpression.valueArguments.getOrNull(parameter.parameterIndex())
                    ?.takeIf { arg -> arg.getArgumentName()?.name == null }

            val parameterExpression = requireNotNull(argument?.getArgumentExpression() ?: parameter.defaultValue)

            receiverInstanceClassResolver.resolveClass(
                expression = parameterExpression,
                stopConditions = listOf(
                    ExpressionResultInstanceClassResolver.StopCondition.FunctionParameter(function),
                ),
                knownResolutions = resolvedParameters,
            )
        }
    }

    private fun collectReferencesStartsFromTests() {
        tests.values.forEach { testFunctions ->
            testFunctions.forEach { testFunction ->
                collectReferencesStartsFromTest(testFunction)
            }
        }
    }

    private fun collectReferencesStartsFromTest(testFunction: KtNamedFunction) {
        collectReferencesQueue.add(testFunction)

        while (collectReferencesQueue.isNotEmpty()) {
            val usedElement = collectReferencesQueue.removeFirst()

            if (usedElement === targetFunction) continue
            usedReferencesCollector.add(usedElement)

            val ownerClass = PsiTreeUtil.getParentOfType(usedElement, KtClass::class.java)
            ownerClass?.let { usedClass ->
                if (usedClass !in tests && usedClass !== targetClass) {
                    if (usedClass.isScannable()) {
                        if (usedClass.isTopLevel()) {
                            referencedClassesCollector.add(usedClass)
                        }
                    }
                }
            }

            if (usedElement.isScannable()) {
                collectNestedDependencies(usedElement)
            }
        }

        usedReferencesCollector.drainTo(usedReferences)
        referencedClassesCollector.drainTo(referencedClasses)
        branchFiltersCollector.drainTo(branchFilters)
    }

    private fun collectNestedDependencies(usedElement: PsiElement) {
        collectAnnotationReferences(usedElement)
        val collector = NestedDependenciesCollector()
        usedElement.accept(collector)
    }

    private fun collectAnnotationReferences(usedElement: PsiElement) {
        when (usedElement) {
            is KtNamedFunction -> {
                usedElement.annotationEntries.forEach { annotationEntry ->
                    val userType = (annotationEntry.typeReference?.typeElement as? KtUserType)
                        ?: return@forEach
                    val annotationClass = userType.referenceExpression?.mainReference?.resolve()
                        ?: return@forEach
                    usedReferencesCollector.add(annotationClass)
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
                branchFiltersCollector[ifExpression] = PsiElementContentFilter.If(
                    expression = ifExpression,
                    isThen = ifExpression.then == usedBranches.single().firstChild,
                )
            }
        }

        private fun collectWhenBranchFilter(whenExpression: KtWhenExpression, usedBranches: Set<PsiElement>) {
            if (usedBranches.size in 1..<whenExpression.entries.size) {
                branchFiltersCollector[whenExpression] = PsiElementContentFilter.When(
                    expression = whenExpression,
                    entries = whenExpression.entries.filter(usedBranches::contains),
                )
            }
        }

        private fun onReferenceExpression(element: KtNameReferenceExpression) {
            when (val reference = element.mainReference.resolve()) {
                targetClass, null -> Unit // no-op

                is KtClassOrObject -> onClassReference(reference)
                is KtConstructor<*> -> onConstructorReference(reference)
                is KtProperty -> onDeclarationReference(reference)
                is KtNamedFunction -> onDeclarationReference(reference)
            }
        }

        private fun onClassReference(reference: KtClassOrObject) {
            if (reference.isScannable() && reference.isTopLevel()) {
                referencedClassesCollector.add(reference)
            } else {
                usedReferencesCollector.add(reference)
            }
        }

        private fun onConstructorReference(reference: KtConstructor<*>) {
            val constructedClass = reference.parent as? KtClass

            if (
                constructedClass != null &&
                constructedClass !== targetClass
            ) {
                usedReferencesCollector.add(reference)
                onClassReference(constructedClass)
            }
        }

        private fun onDeclarationReference(reference: KtDeclaration) {
            if (
                (reference.context is KtClassBody || reference.context is KtFile) &&
                reference !in usedReferences &&
                usedReferencesCollector.add(reference)
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

    private fun <T> MutableCollection<T>.drainTo(other: MutableCollection<T>) {
        other.addAll(this)
        clear()
    }

    private fun <K, V> MutableMap<K, V>.drainTo(other: MutableMap<K, V>) {
        other.putAll(this)
        clear()
    }

    private class VisitorBranchFilter(
        val root: PsiElement,
        val branches: Set<PsiElement>,
        var activeBranch: PsiElement? = null,
    )

    private class FunctionRequirement {

        private val parameterRequirements: MutableMap<KtParameter, MutableList<ParameterRequirement>> = mutableMapOf()

        fun addParameterRequirement(param: KtParameter, requirement: ParameterRequirement) {
            parameterRequirements.getOrPut(param, ::mutableListOf)
                .add(requirement)
        }

        fun getParameterRequirements(parameter: KtParameter): List<ParameterRequirement>? {
            return parameterRequirements[parameter]
        }
    }

    private data class TypeRequirement(
        val classOrObject: KtClassOrObject,
        val isNegated: Boolean,
    )

    private sealed interface ParameterRequirement {

        class AnyOf(val requirements: List<TypeRequirement>) : ParameterRequirement

        class All(val requirements: List<TypeRequirement>) : ParameterRequirement

        class Composite(val requirements: List<ParameterRequirement>) : ParameterRequirement
    }
}