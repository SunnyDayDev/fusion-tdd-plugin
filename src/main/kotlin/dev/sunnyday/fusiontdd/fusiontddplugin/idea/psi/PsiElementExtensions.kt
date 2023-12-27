package dev.sunnyday.fusiontdd.fusiontddplugin.idea.psi

import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry

internal fun KtSuperTypeListEntry.resolveClass(): KtClass? {
    return typeAsUserType?.referenceExpression?.mainReference?.resolve() as? KtClass
}

internal fun KtFunction.hasOverrideModifier(): Boolean {
    return modifierList?.getModifier(KtTokens.OVERRIDE_KEYWORD) != null
}