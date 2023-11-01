package dev.sunnyday.fusiontdd.fusiontddplugin.test

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.psi.findKotlinClass
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.elements.FakeFileForLightClass
import org.jetbrains.kotlin.idea.search.projectScope
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.findFunctionByName
import org.jetbrains.kotlin.psi.psiUtil.findPropertyByName

fun JavaCodeInsightTestFixture.getClass(name: String): KtClass {
    return javaFacade.getClass(name)
}

fun JavaCodeInsightTestFixture.getClassFunction(fqName: String): KtNamedFunction {
    val className = fqName.substringBeforeLast('.')
    val functionName = fqName.substringAfterLast('.')
    return javaFacade.getClass(className).getNamedFunction(functionName)
}

fun JavaCodeInsightTestFixture.getHighLevelFun(fileClass: String, name: String): KtNamedFunction {
    return javaFacade.getHighLevelFun(fileClass, name)
}

fun JavaPsiFacade.getClass(name: String): KtClass {
    return findKotlinClass(name)
        ?: error("Can't find required class '$name'")
}

fun JavaPsiFacade.getHighLevelFun(fileClass: String, name: String): KtNamedFunction {
    val klass = findClass(fileClass, project.projectScope())
        ?.let { it as? KtLightClassForFacade }
        ?: error("Can't find required class '$name'")

    val file = (klass.containingFile as? FakeFileForLightClass)?.ktFile
        ?: error("Can't get file of '$name'")

    var function: KtNamedFunction? = null
    file.accept(object : PsiRecursiveElementWalkingVisitor() {

        override fun visitElement(element: PsiElement) {
            if (element is KtNamedFunction && element.name == name) {
                function = element
                stopWalking()
            } else {
                super.visitElement(element)
            }
        }
    })

    return function ?: error("Can't find required function '$name'")
}

fun KtClass.getNamedFunction(name: String): KtNamedFunction {
    return findFunctionByName(name) as? KtNamedFunction
        ?: error("Can't find required function '$name'")
}

fun KtClass.getProperty(name: String): KtProperty {
    return findPropertyByName(name) as? KtProperty
        ?: error("Can't find required function '$name'")
}