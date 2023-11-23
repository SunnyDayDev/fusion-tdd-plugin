package dev.sunnyday.fusiontdd.fusiontddplugin.domain.model

import org.jetbrains.kotlin.psi.KtIfExpression

internal sealed interface PsiElementContentFilter {

    data class If(val expression: KtIfExpression, val isThen: Boolean) : PsiElementContentFilter
}