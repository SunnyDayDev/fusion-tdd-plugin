package dev.sunnyday.fusiontdd.fusiontddplugin.idea.psi

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.asJava.classes.KtUltraLightClass
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.psi.KtClassOrObject

fun JavaPsiFacade.findKotlinClass(
    name: String,
    searchScope: GlobalSearchScope = project.projectScope(),
): KtClassOrObject? {
    return findClass(name, searchScope)
        ?.let { it as? KtUltraLightClass }
        ?.kotlinOrigin
}