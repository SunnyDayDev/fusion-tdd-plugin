package dev.sunnyday.fusiontdd.fusiontddplugin.idea.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.editor.Caret
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.util.Either
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.util.flatMapLeft
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings.FusionTDDSettings
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction

internal object ActionEventUtils {

    fun getPsiElementBelowCaret(event: AnActionEvent): Either<PsiElement, String> {
        val file: PsiFile = event.dataContext.getData(CommonDataKeys.PSI_FILE)
            ?: return Either.Right("isn't a file")

        val caret: Caret = event.dataContext.getData(LangDataKeys.CARET)
            ?: return Either.Right("has not selection")

        val element = file.findElementAt(caret.offset)
            ?: return Either.Right("has not selection")

        return Either.Left(element)
    }

    fun getFunctionBelowCaretOrReason(event: AnActionEvent): Either<KtNamedFunction, String> {
        return getPsiElementBelowCaret(event).flatMapLeft { element ->
            val targetFunction = PsiTreeUtil.getParentOfType(element, KtNamedFunction::class.java)
                ?: return@flatMapLeft Either.Right("isn't a function")

            Either.Left(targetFunction)
        }
    }

    fun getTargetElementThatSupportsGeneration(event: AnActionEvent): Either<KtDeclaration, String> {
        return getPsiElementBelowCaret(event)
            .flatMapLeft { elementBelowCaret ->
                val functionBelowCaret = elementBelowCaret.parentOfType<KtNamedFunction>()
                val parentOfElementBelowCaret = elementBelowCaret.parent

                val targetElement: KtDeclaration? = when {
                    functionBelowCaret != null -> functionBelowCaret
                    parentOfElementBelowCaret is KtClass -> parentOfElementBelowCaret
                    parentOfElementBelowCaret is KtClassBody -> parentOfElementBelowCaret.parentOfType<KtClass>()
                    else -> null
                }

                if (targetElement == null) {
                    return@flatMapLeft Either.Right("Can't resolve target element")
                }

                Either.Left(targetElement)
            }
    }

    fun checkIsSettingsReadyForGenerate(settings: FusionTDDSettings): Boolean {
        return settings.starcoderModel != null &&
                settings.authToken != null &&
                settings.projectPackage != null
    }
}