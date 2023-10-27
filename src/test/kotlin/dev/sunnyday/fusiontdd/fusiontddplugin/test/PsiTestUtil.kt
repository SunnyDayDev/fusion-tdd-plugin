package dev.sunnyday.fusiontdd.fusiontddplugin.test

import com.intellij.psi.JavaPsiFacade
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.psi.findKotlinClass
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.findFunctionByName

fun JavaCodeInsightTestFixture.getClass(name: String): KtClass {
    return javaFacade.getClass(name)
}

fun JavaPsiFacade.getClass(name: String): KtClass {
    return findKotlinClass(name)
        ?: error("Can't find required class")
}

fun KtClass.getNamedFunction(name: String): KtNamedFunction {
    return findFunctionByName(name) as? KtNamedFunction
        ?: error("Can't find required function")
}