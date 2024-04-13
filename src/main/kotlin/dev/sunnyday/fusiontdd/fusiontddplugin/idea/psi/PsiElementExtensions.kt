package dev.sunnyday.fusiontdd.fusiontddplugin.idea.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils

internal fun KtSuperTypeListEntry.resolveClass(): KtClass? {
    return typeAsUserType?.referenceExpression?.mainReference?.resolve() as? KtClass
}

internal fun KtFunction.hasOverrideModifier(): Boolean {
    return modifierList?.getModifier(KtTokens.OVERRIDE_KEYWORD) != null
}

internal fun KtTypeReference.resolve(): KtClassOrObject? {
    val bindingContext = analyze()
    val type = bindingContext[BindingContext.TYPE, this]

    val classDescriptor = type?.constructor?.declarationDescriptor as? ClassDescriptor
        ?: return null

    val psiElement = DescriptorToSourceUtils.descriptorToDeclaration(classDescriptor)
    return psiElement as? KtClassOrObject
}

internal fun PsiElement.acceptAll(check: (element: PsiElement) -> Boolean): Boolean {
    var isPass = true

    accept(object : PsiRecursiveElementWalkingVisitor() {
        override fun visitElement(element: PsiElement) {
            if (check.invoke(element)) {
                super.visitElement(element)
            } else {
                stopWalking()
                isPass = false
            }
        }
    })

    return isPass
}