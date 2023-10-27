package dev.sunnyday.fusiontdd.fusiontddplugin.idea.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.util.Either
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.psi.FusionTDDPsiUtils
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings.FusionTDDSettings
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction

internal object ActionEventUtils {

    fun getFunctionBelowCaretOrReason(event: AnActionEvent): Either<KtNamedFunction, String> {
        val file: PsiFile = event.dataContext.getData(CommonDataKeys.PSI_FILE)
            ?: return Either.Right("isn't a file")

        val caret: Caret = event.dataContext.getData(LangDataKeys.CARET)
            ?: return Either.Right("has not selection")

        val element = file.findElementAt(caret.offset)
            ?: return Either.Right("has not selection")

        val targetFunction = PsiTreeUtil.getParentOfType(element, KtNamedFunction::class.java)
            ?: return Either.Right("isn't a function")

        return Either.Left(targetFunction)
    }

    fun checkClassHasTests(project: Project, klass: KtClass): Boolean {
        val testClass = FusionTDDPsiUtils.getTestClass(project, klass)
        return testClass != null
    }

    fun checkIsSettingsReadyForGenerate(settings: FusionTDDSettings): Boolean {
        return settings.starcoderModel != null &&
                settings.authToken != null &&
                settings.projectPackage != null
    }
}