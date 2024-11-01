package dev.sunnyday.fusiontdd.fusiontddplugin.domain.model

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction

// TODO: List -> Set
internal data class FunctionGenerationContext(
    val targetElement: KtDeclaration?,
    val usedClasses: List<KtClassOrObject>,
    val usedReferences: List<PsiElement>,
    val tests: Map<KtClass, List<KtNamedFunction>> = emptyMap(),
    val branchFilters: Map<PsiElement, PsiElementContentFilter> = emptyMap(),
)