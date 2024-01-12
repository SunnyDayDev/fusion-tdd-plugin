package dev.sunnyday.fusiontdd.fusiontddplugin.idea.psi

import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*

internal class ExpressionResultInstanceClassResolver {

    fun resolve(expression: KtExpression?): KtClass? {
        return when (expression) {
            null -> null
            is KtCallExpression -> resolve(expression.calleeExpression)
            is KtReferenceExpression -> resolveReferenceExpression(expression)
            is KtBlockExpression -> resolve(expression.statements.lastOrNull())
            is KtReturnExpression -> resolve(expression.returnedExpression)
            is KtIfExpression -> resolveIfExpression(expression)
            is KtWhenExpression -> resolveWhenExpression(expression)
            else -> null
        }
    }

    private fun resolveReferenceExpression(referenceExpression: KtReferenceExpression): KtClass? {
        return when (val reference = referenceExpression.mainReference.resolve()) {
            is KtClass -> return reference
            is KtProperty -> resolveReferencedPropertyExpression(reference)
            is KtFunction -> resolve(reference.bodyExpression)
            else -> null
        }
    }

    private fun resolveReferencedPropertyExpression(property: KtProperty): KtClass? {
        val instanceExpression = property.initializer
            ?: property.getter?.bodyExpression
            ?: property.getter?.bodyBlockExpression

        return resolve(instanceExpression)
    }

    private fun resolveIfExpression(expression: KtIfExpression): KtClass? {
        val thenClass = resolve(expression.then)
        val elseClass = resolve(expression.`else`)

        if (thenClass == null || elseClass == null) {
            return thenClass ?: elseClass
        }

        val thenInheritanceChain = getInheritanceChain(thenClass)
        val elseInheritanceChain = getInheritanceChain(elseClass)

        return findLastCommon(listOf(thenInheritanceChain, elseInheritanceChain))
    }

    private fun resolveWhenExpression(expression: KtWhenExpression): KtClass? {
        val expressionsInheritanceChains = expression.entries.mapNotNullTo(mutableListOf()) { whenEntry ->
            resolve(whenEntry.expression)
                ?.let(::getInheritanceChain)
        }

        return findLastCommon(expressionsInheritanceChains)
    }

    private fun getInheritanceChain(klass: KtClass): List<KtClass> {
        return buildList {
            var currentClass: KtClass? = klass
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