package dev.sunnyday.fusiontdd.fusiontddplugin.idea.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import org.jetbrains.kotlin.psi.KtClass

internal object FusionTDDPsiUtils {

    fun getTestClass(project: Project, klass: KtClass): KtClass? {
        return JavaPsiFacade.getInstance(project)
            .findKotlinClass("${klass.fqName}Test")
    }
}