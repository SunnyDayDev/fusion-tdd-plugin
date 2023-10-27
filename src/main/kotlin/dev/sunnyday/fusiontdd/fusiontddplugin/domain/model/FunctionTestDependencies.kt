package dev.sunnyday.fusiontdd.fusiontddplugin.domain.model

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction

data class FunctionTestDependencies(
    val function: KtNamedFunction,
    val testClass: KtClass,
    val usedClasses: List<KtClass>,
    val usedReferences: List<PsiElement>
)