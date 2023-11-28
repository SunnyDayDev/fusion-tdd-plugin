package dev.sunnyday.fusiontdd.fusiontddplugin.domain.model

import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.KtWhenExpression

internal sealed interface PsiElementContentFilter {

    data class If(val expression: KtIfExpression, val isThen: Boolean) : PsiElementContentFilter

    data class When(val expression: KtWhenExpression, val entries: List<KtWhenEntry>) : PsiElementContentFilter
}