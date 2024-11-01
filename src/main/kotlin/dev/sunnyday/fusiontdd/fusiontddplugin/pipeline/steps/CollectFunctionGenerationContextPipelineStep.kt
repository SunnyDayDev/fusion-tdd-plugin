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
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex
import java.util.*

internal class CollectFunctionGenerationContextPipelineStep(
    private val targetElement: KtDeclaration,
    private val targetClass: KtClass,
    private val settings: FusionTDDSettings,
) : PipelineStep<Nothing?, FunctionGenerationContext> {

    private val logger: Logger = thisLogger()

    override fun execute(input: Nothing?, observer: (Result<FunctionGenerationContext>) -> Unit) {
        logger.debug("Pipeline: Collect tests and references for '${targetElement.name}'")

        val collectTask = CollectFunctionGenerationContextPipelineTask(targetElement, targetClass, settings)
        val result = runCatching(collectTask::execute)

        observer.invoke(result)
    }
}

private class CollectFunctionGenerationContextPipelineTask(
    private val targetElement: KtDeclaration,
    private val targetClass: KtClass,
    private val settings: FusionTDDSettings,
) {

    private val testAnnotationRegex = Regex("@.*?Test")

    private val tests = mutableMapOf<KtClass, MutableList<KtNamedFunction>>()

    private val branchPoints = IdentityHashMap<PsiElement, MutableSet<PsiElement>>()

    private val collectReferencesQueue: Deque<PsiElement> = ArrayDeque()

    private val referencedClasses = mutableSetOf<KtClassOrObject>()
    private val usedReferences = mutableSetOf<PsiElement>(targetElement)
    private val branchFilters = mutableMapOf<PsiElement, PsiElementContentFilter>()

    private val referencedClassesCollector = mutableSetOf<KtClassOrObject>()
    private val usedReferencesCollector = mutableSetOf<PsiElement>()
    private val branchFiltersCollector = mutableMapOf<PsiElement, PsiElementContentFilter>()

    private val functionUsageRequirements = mutableMapOf<KtNamedFunction, FunctionRequirement>()
    private val usageToFunction = mutableMapOf<PsiElement, KtNamedFunction>()

    private val superClassesCache = mutableMapOf<KtClassOrObject, Set<KtClass>>()

    private val receiverInstanceClassResolver = ExpressionResultInstanceClassResolver()

    private val findFunctionUsagesFactory = KotlinFindUsagesHandlerFactory(targetElement.project)
        .apply {
            findFunctionOptions.searchOverrides = true
            findClassOptions.isMethodsUsages = true
        }

    fun execute(): FunctionGenerationContext {
        ProgressManager.getInstance().runProcess(
            /* process = */ Computable(::collectTestsWithBranchPointMarking),
            /* progress = */ EmptyProgressIndicator(),
        )
        referencedClasses.addAll(tests.keys)

        collectGenerationContextUsedReferences()

        referencedClasses.add(targetClass)

        return FunctionGenerationContext(
            targetElement = targetElement,
            usedClasses = referencedClasses.toList(),
            usedReferences = usedReferences.toList(),
            tests = tests,
            branchFilters = branchFilters,
        )
    }

    private fun collectTestsWithBranchPointMarking() {
        val queue = ArrayDeque<Pair<KtNamedFunction, KtClass?>>()
        val visited = mutableSetOf(targetElement)

        val startPoints = getTargetElementCollectContextStartPoints(targetElement)
        startPoints.forEach { startPointFunction ->
            getFunctionWithOverrides(startPointFunction).let { functionOrOverride ->
                functionOrOverride.forEach { function ->
                    queue.add(function to targetClass)
                }
                visited.addAll(functionOrOverride)
            }
        }

        while (queue.isNotEmpty()) {
            val (function, functionImplementerClass) = queue.removeFirst()
            val usages = findUsages(function, functionImplementerClass)
            usages.forEach { usage -> usageToFunction[usage.element] = function }
            proceedUsages(usages, queue, visited)
        }

        // Sort by real order
        tests.forEach { (_, tests) ->
            tests.sortBy(PsiElement::getTextOffset)
        }
    }

    private fun getTargetElementCollectContextStartPoints(targetElement: KtDeclaration): List<KtNamedFunction> {
        return when (targetElement) {
            is KtClass -> targetElement.declarations.filterIsInstance<KtNamedFunction>()
            is KtNamedFunction -> listOf(targetElement)
            else -> emptyList()
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

        findFunctionUsagesFactory.createFindUsagesHandler(function, false)
            .processElementUsages(
                /* element = */ function,
                /* processor = */ collectProcessor,
                /* options = */ findFunctionUsagesFactory.findFunctionOptions,
            )

        return collectProcessor.results
    }

    private fun isPossibleUsage(usageInfo: UsageInfo, functionImplementerClass: KtClass?): Boolean {
        if (functionImplementerClass == null) return true

        val receiverClass = getUsageReceiverClass(usageInfo)

        return receiverClass === functionImplementerClass ||
                receiverClass != null && getSuperClasses(receiverClass).contains(functionImplementerClass)
    }

    // TODO: getUsageReceiverClass, other usage cases (not only KtDotQualifiedExpression)
    private fun getUsageReceiverClass(usageInfo: UsageInfo): KtClassOrObject? {
        val usageCall = usageInfo.element ?: return null

        val usageElementContext = usageCall.context

        return when (val usageContext = usageElementContext?.context) {
            is KtDotQualifiedExpression -> {
                val usageFunctionScope = usageCall.parentOfType<KtNamedFunction>()
                receiverInstanceClassResolver.resolveInstantiatingClass(
                    expression = usageContext.receiverExpression,
                    stopConditions = usageFunctionScope?.let { scopeFunction ->
                        listOf(
                            ExpressionResultInstanceClassResolver.StopCondition.FunctionParameter(scopeFunction),
                        )
                    },
                )
            }

            else -> {
                val calledFunImplementation = (usageCall as? KtNameReferenceExpression)?.mainReference?.resolve() as? KtNamedFunction
                calledFunImplementation?.containingClass()
            }
        }
    }

    private fun proceedUsages(
        usages: Collection<PsiReference>,
        queue: Deque<Pair<KtNamedFunction, KtClass?>>,
        visited: MutableSet<KtDeclaration>,
    ) {
        usages.forEach forEachUsage@{ usage ->
            val usageOwnerFunction = walkUpToCallerFunctionWithBranchPointMarking(usage.element)
                ?: return@forEachUsage

            getFunctionWithOverrides(usageOwnerFunction).forEach { functionOrOverride ->
                if (visited.add(functionOrOverride)) {
                    queue.addFirst(functionOrOverride to functionOrOverride.containingClass())

                    if (hasTestAnnotation(functionOrOverride)) {
                        functionOrOverride.containingClass()?.let { testClass ->
                            if (checkIsTestFitCallChainRequirements(functionOrOverride)) {
                                tests.getOrPut(testClass, ::mutableListOf).add(functionOrOverride)
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
        val usedInWhenParameter = receiverInstanceClassResolver.resolveInstantiationExpression(
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
        whenEntry: KtWhenEntry,
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
        condition: KtWhenConditionWithExpression,
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
        resolvedParameters: Map<KtParameter, KtClassOrObject?> = emptyMap(),
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

            val parameterExpression = argument?.getArgumentExpression() ?: parameter.defaultValue

            receiverInstanceClassResolver.resolveInstantiatingClass(
                expression = parameterExpression,
                stopConditions = listOf(
                    ExpressionResultInstanceClassResolver.StopCondition.FunctionParameter(function),
                ),
                knownResolutions = resolvedParameters,
            )
        }
    }

    private fun collectGenerationContextUsedReferences() {
        tests.values.forEach { testFunctions ->
            testFunctions.forEach { testFunction ->
                collectUsedReferences(testFunction)
            }
        }

        proceedCollectWithSeparatedCollectorAndDrain {
            collectNestedDependencies(targetElement)
        }
    }

    private fun collectUsedReferences(function: KtNamedFunction) {
        proceedCollectWithSeparatedCollectorAndDrain {
            collectReferencesQueue.add(function)

            while (collectReferencesQueue.isNotEmpty()) {
                val usedElement = collectReferencesQueue.removeFirst()

                if (usedElement === targetElement) continue
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
        }
    }

    private fun proceedCollectWithSeparatedCollectorAndDrain(collect: () -> Unit) {
        proceedCollectWithSeparatedCollector {
            collect.invoke()
            true
        }
    }

    private fun proceedCollectWithSeparatedCollector(collect: () -> Boolean) {
        val isSuccess = collect.invoke()

        if (isSuccess) {
            copyCollectorsToCollectedContext()
        }

        clearCollectors()
    }

    private fun copyCollectorsToCollectedContext() {
        usedReferences.addAll(usedReferencesCollector)
        referencedClasses.addAll(referencedClassesCollector)
        branchFilters.putAll(branchFiltersCollector)
    }

    private fun clearCollectors() {
        usedReferencesCollector.clear()
        referencedClassesCollector.clear()
        branchFiltersCollector.clear()
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
                    addUniqueUsedReferenceToCollector(annotationClass)
                }
            }
        }
    }

    private fun addUniqueUsedReferenceToCollector(usedElement: PsiElement): Boolean {
        return !usedReferences.contains(usedElement) && usedReferencesCollector.add(usedElement)
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

            when (element) {
                is KtIfExpression -> collectIfBranchFilter(element, usedBranches)
                is KtWhenExpression -> collectWhenBranchFilter(element, usedBranches)
            }
        }

        private fun collectIfBranchFilter(ifExpression: KtIfExpression, usedBranches: Set<PsiElement>) {
            if (usedBranches.size == 1) {
                pushBranchFilterStack(ifExpression, usedBranches)

                ifExpression.condition?.accept(NestedDependenciesCollector())

                branchFiltersCollector[ifExpression] = PsiElementContentFilter.If(
                    expression = ifExpression,
                    isThen = ifExpression.then == usedBranches.single().firstChild,
                )
            }
        }

        private fun collectWhenBranchFilter(whenExpression: KtWhenExpression, usedBranches: Set<PsiElement>) {
            if (usedBranches.size in 1..<whenExpression.entries.size) {
                pushBranchFilterStack(whenExpression, usedBranches)

                whenExpression.subjectExpression?.accept(NestedDependenciesCollector())

                branchFiltersCollector[whenExpression] = PsiElementContentFilter.When(
                    expression = whenExpression,
                    entries = whenExpression.entries.filter(usedBranches::contains),
                )
            }
        }

        private fun pushBranchFilterStack(branchExpression: PsiElement, usedBranches: Set<PsiElement>) {
            visitorBranchFiltersStack.add(
                VisitorBranchFilter(
                    root = branchExpression,
                    branches = usedBranches,
                )
            )
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
                addUniqueUsedReferenceToCollector(reference)
            }
        }

        private fun onConstructorReference(reference: KtConstructor<*>) {
            val constructedClass = reference.parent as? KtClass

            if (
                constructedClass != null &&
                constructedClass !== targetClass
            ) {
                addUniqueUsedReferenceToCollector(reference)
                onClassReference(constructedClass)
            }
        }

        private fun onDeclarationReference(reference: KtDeclaration) {
            if (
                (reference.context is KtClassBody || reference.context is KtFile) &&
                addUniqueUsedReferenceToCollector(reference)
            ) {
                if (reference.isScannable()) {
                    addDeclarationOwnersClassesToReferenced(reference)
                    addDeclarationSupertypeUsagesToReferenced(reference)

                    collectReferencesQueue.addFirst(reference)
                }
            }
        }

        private fun addDeclarationOwnersClassesToReferenced(reference: KtDeclaration) {
            var classOrObject = reference.parentOfType<KtClassOrObject>(withSelf = false) ?: return
            while (!classOrObject.isTopLevel()) {
                addUniqueUsedReferenceToCollector(classOrObject)
                classOrObject = classOrObject.parentOfType<KtClassOrObject>(withSelf = false) ?: return
            }

            referencedClassesCollector.add(classOrObject)
        }

        private fun addDeclarationSupertypeUsagesToReferenced(reference: KtDeclaration) {
            if (reference !is KtNamedFunction && reference !is KtProperty) {
                return
            }

            val classOrObject = reference.parentOfType<KtClassOrObject>(withSelf = false) ?: return
            iterateOverSuperTypes(classOrObject) { superClass ->
                val usedSuperTypeReference = superClass.declarations.firstOrNull { declaration ->
                    when {
                        reference is KtNamedFunction && declaration is KtNamedFunction &&
                                reference.name == declaration.name &&
                                reference.valueParameterList?.text == declaration.valueParameterList?.text -> true

                        reference is KtProperty && declaration is KtProperty &&
                                reference.name == declaration.name -> true

                        else -> false
                    }
                } ?: return@iterateOverSuperTypes

                addUniqueUsedReferenceToCollector(usedSuperTypeReference)
            }
        }

        private inline fun iterateOverSuperTypes(classOrObject: KtClassOrObject, action: (superClass: KtClass) -> Unit) {
            val queue = ArrayDeque<KtSuperTypeListEntry>()
            queue.addAll(classOrObject.superTypeListEntries)

            while (queue.isNotEmpty()) {
                val superTypeEntry = queue.removeFirst()
                val klass: KtClass = superTypeEntry.resolveClass() ?: continue
                queue.addAll(klass.superTypeListEntries)
                action.invoke(klass)
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