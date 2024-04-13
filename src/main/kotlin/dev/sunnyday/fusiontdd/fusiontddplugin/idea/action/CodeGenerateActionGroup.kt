package dev.sunnyday.fusiontdd.fusiontddplugin.idea.action

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.CommonDataKeys.PSI_FILE
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.util.getLeftOrNull
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings.FusionTDDSettings
import org.jetbrains.kotlin.psi.KtClass


class CodeGenerateActionGroup : ActionGroup(), DumbAware {

    private val logger: Logger = thisLogger()

    override fun getChildren(event: AnActionEvent?): Array<AnAction> {
        logger.debug("Get children: $event")

        if (event == null) {
            return EMPTY_ARRAY
        }

        val project: Project = PlatformDataKeys.PROJECT.getData(event.dataContext)
            ?: return EMPTY_ARRAY

        val settings = project.service<FusionTDDSettings>()
        if (!ActionEventUtils.checkIsSettingsReadyForGenerate(settings)) {
            return EMPTY_ARRAY
        }

        event.dataContext.getData(PSI_FILE)
            ?: return EMPTY_ARRAY

        val targetFunctionOrReason = ActionEventUtils.getFunctionBelowCaretOrReason(event)
        val targetFunction = targetFunctionOrReason.getLeftOrNull()
            ?: return EMPTY_ARRAY

        if (PsiTreeUtil.getParentOfType(targetFunction, KtClass::class.java, false) == null) {
            return EMPTY_ARRAY
        }

        return arrayOf(
            getCommonGenerateCodeAction(),
            getForceCommentsGenerateAction(),
        )
    }

    private fun getCommonGenerateCodeAction(): AnAction {
        return getOrCreateCodeGenerateAction(CodeGenerateAction.ID) {
            CodeGenerateAction()
        }
    }

    private fun getForceCommentsGenerateAction(): AnAction {
        return getOrCreateCodeGenerateAction(CodeGenerateAction.INVERSE_COMMENTS_ID) {
            CodeGenerateAction(
                isInverseAddTestCommentsBeforeGenerationSetting = true,
            )
        }
    }

    private fun getOrCreateCodeGenerateAction(
        id: String,
        actionFactory: () -> CodeGenerateAction,
    ): AnAction {
        val actionManager = ActionManager.getInstance()
        return actionManager.getAction(id) ?: actionFactory.invoke().also { action ->
            actionManager.registerAction(id, action)
        }
    }
}