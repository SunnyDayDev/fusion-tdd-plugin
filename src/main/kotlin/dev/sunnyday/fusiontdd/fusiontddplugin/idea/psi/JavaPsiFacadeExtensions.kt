package dev.sunnyday.fusiontdd.fusiontddplugin.idea.psi

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.asJava.classes.KtUltraLightClass
import org.jetbrains.kotlin.idea.search.projectScope
import org.jetbrains.kotlin.psi.KtClass

fun JavaPsiFacade.findKotlinClass(name: String, searchScope: GlobalSearchScope = project.projectScope()): KtClass? {
    return findClass(name, searchScope)
        ?.let { it as? KtUltraLightClass }
        ?.kotlinOrigin as? KtClass
}