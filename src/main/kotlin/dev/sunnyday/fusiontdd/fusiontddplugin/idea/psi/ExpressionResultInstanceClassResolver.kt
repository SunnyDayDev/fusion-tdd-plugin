package dev.sunnyday.fusiontdd.fusiontddplugin.idea.psi

import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.argumentIndex
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex

// TODO: make ExpressionResultInstanceClassResolver thread safe?
internal class ExpressionResultInstanceClassResolver {

    private val callArgumentsListStack = mutableListOf<KtValueArgumentList?>()

    private var scopeExpression: KtExpression? = null
    private var knownResolutions: Map<out KtExpression, KtExpression?> = emptyMap()

    fun resolveClass(
        expression: KtExpression?,
        scopeExpression: KtExpression? = null,
        knownResolutions: Map<out KtExpression, KtExpression?> = emptyMap(),
    ): KtClassOrObject? {
        return withScope(scopeExpression, knownResolutions) {
            internalResolveClass(expression)
        }
    }

    fun resolveExpression(
        expression: KtExpression?,
        scopeExpression: KtExpression? = null,
        knownResolutions: Map<out KtExpression, KtExpression?> = emptyMap(),
    ): KtExpression? {
        return withScope(scopeExpression, knownResolutions) {
            internalResolveExpression(expression)
        }
    }

    private inline fun <T> withScope(
        expression: KtExpression?,
        resolutions: Map<out KtExpression, KtExpression?>,
        action: () -> T
    ): T {
        scopeExpression = expression
        knownResolutions = resolutions

        val result = action.invoke()

        scopeExpression = null
        knownResolutions = emptyMap()

        return result
    }

    private fun internalResolveClass(expression: KtExpression?): KtClassOrObject? {
        return when (expression) {
            null -> null

            is KtClassOrObject -> expression
            is KtIfExpression -> resolveIfExpression(expression)
            is KtWhenExpression -> resolveWhenExpression(expression)

            else -> {
                val resolvedExpression = internalResolveExpression(expression)

                if (resolvedExpression === expression) {
                    null
                } else {
                    internalResolveClass(resolvedExpression)
                }
            }
        }
    }

    // TODO: KtConstantExpression, KtStringTemplateExpression ...
    private fun internalResolveExpression(expression: KtExpression?): KtExpression? {
        return when (expression) {
            null -> null
            is KtClassOrObject -> return expression

            is KtProperty -> resolveReferencedPropertyExpression(expression)
            is KtConstructor<*> -> internalResolveExpression(expression.getContainingClassOrObject())
            is KtFunction -> internalResolveExpression(expression.bodyExpression)
            is KtParameter -> resolveParameterReference(expression)

            is KtDotQualifiedExpression -> internalResolveExpression(expression.selectorExpression)
            is KtConstructorCalleeExpression -> internalResolveExpression(expression.constructorReferenceExpression)
            is KtBlockExpression -> internalResolveExpression(expression.statements.lastOrNull())
            is KtReturnExpression -> internalResolveExpression(expression.returnedExpression)
            is KtCallExpression -> resoleCallExpression(expression)
            is KtReferenceExpression -> resolveReferenceExpression(expression)

            in knownResolutions -> internalResolveExpression(knownResolutions[expression])

            else -> expression
        }
    }

    private fun resoleCallExpression(expression: KtCallExpression): KtExpression? {
        val calleeExpression = expression.calleeExpression ?: return expression
        val valueArgumentList = expression.valueArgumentList

        callArgumentsListStack.add(valueArgumentList)

        return internalResolveExpression(calleeExpression)
            .also { callArgumentsListStack.removeLast() }
    }

    private fun resolveReferenceExpression(referenceExpression: KtReferenceExpression): KtExpression? {
        return when (val reference = referenceExpression.mainReference.resolve()) {
            is KtExpression -> internalResolveExpression(reference)
            else -> null
        }
    }

    private fun resolveReferencedPropertyExpression(property: KtProperty): KtExpression? {
        val instanceExpression = property.initializer
            ?: property.getter?.bodyExpression

        return internalResolveExpression(instanceExpression)
    }
    private fun resolveParameterReference(parameter: KtParameter): KtExpression? {
        if (parameter.ownerFunction === scopeExpression) {
            return parameter
        }

        val valueArguments = callArgumentsListStack.lastOrNull()?.arguments.orEmpty()
        val valueArgument = valueArguments.firstOrNull { arg -> isArgumentForParameter(arg, parameter) }

        return if (valueArgument != null) {
            internalResolveExpression(valueArgument.getArgumentExpression())
        } else {
            internalResolveExpression(parameter.defaultValue)
        }
    }

    private fun isArgumentForParameter(argument: KtValueArgument, parameter: KtParameter): Boolean {
        val name = argument.getArgumentName()?.name

        return name == null && argument.argumentIndex == parameter.parameterIndex() ||
                name == parameter.name
    }


    private fun resolveIfExpression(expression: KtIfExpression): KtClassOrObject? {
        val thenClass = internalResolveClass(expression.then)
        val elseClass = internalResolveClass(expression.`else`)

        if (thenClass == null || elseClass == null) {
            return thenClass ?: elseClass
        }

        val thenInheritanceChain = getInheritanceChain(thenClass)
        val elseInheritanceChain = getInheritanceChain(elseClass)

        return findLastCommon(listOf(thenInheritanceChain, elseInheritanceChain))
    }

    private fun resolveWhenExpression(expression: KtWhenExpression): KtClassOrObject? {
        val expressionsInheritanceChains = expression.entries.mapNotNullTo(mutableListOf()) { whenEntry ->
            internalResolveClass(whenEntry.expression)
                ?.let(::getInheritanceChain)
        }

        return findLastCommon(expressionsInheritanceChains)
    }

    private fun getInheritanceChain(klass: KtClassOrObject): List<KtClassOrObject> {
        return buildList {
            var currentClass: KtClassOrObject? = klass
            while (currentClass != null) {
                add(currentClass)
                currentClass = currentClass.superTypeListEntries.firstOrNull()?.resolveClass()
            }

            reverse()
        }
    }

    private fun <T : Any> findLastCommon(collections: List<Collection<T>>): T? {
        if (collections.size < 2) {
            return collections.firstOrNull()?.lastOrNull()
        }

        val iterators = collections.map { it.iterator() }

        var result: T? = null
        var hasNext = iterators.all { it.hasNext() }

        while (hasNext) {
            var currentCommon: T? = null

            iterators.forEach { iterator ->
                val item = iterator.next()

                if (currentCommon == null) {
                    currentCommon = item
                }

                if (item != currentCommon) {
                    return result
                }

                hasNext = hasNext && iterator.hasNext()
            }

            result = currentCommon
        }

        return result
    }
}