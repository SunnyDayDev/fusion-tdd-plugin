package dev.sunnyday.fusiontdd.fusiontddplugin.idea.service

import com.intellij.openapi.components.Service
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.editor.GeneratingFunctionHighlightAnimator

@Service(Service.Level.APP)
internal class GeneratingFunctionHighlightAnimatorProvider {

    fun getGeneratingFunctionHighlightAnimator(): GeneratingFunctionHighlightAnimator {
        return GeneratingFunctionHighlightAnimator()
    }
}