package dev.sunnyday.fusiontdd.fusiontddplugin.idea.dialog

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import java.awt.Dimension
import javax.swing.Action
import javax.swing.JComponent

internal class ModifySourceCodeDialog : DialogWrapper(true) {

    private val codeBlockTextArea = JBTextArea().apply {
        name = "codeArea"
    }

    private val descriptionLabel = JBLabel("This source will be used for generation:").apply {
        name = "descriptionLabel"
    }

    init {
        title = "Confirm Source"
        init()
    }

    override fun getDimensionServiceKey(): String {
        return "dev.sunnyday.fusiontdd.fusiontddplugin.ConfirmGenerationSourceDialog"
    }

    fun setDescription(text: String) {
        descriptionLabel.text = text
    }

    fun setCodeBlock(rawCode: String) {
        codeBlockTextArea.text = rawCode
    }

    fun getCodeBlock(): String {
        return codeBlockTextArea.text
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row { cell(descriptionLabel) }
            row {
                cell(JBScrollPane(codeBlockTextArea))
                    .applyToComponent { preferredSize = Dimension(800, 600) }
                    .align(Align.FILL)
            }
        }
    }

    override fun createActions(): Array<Action> {
        return arrayOf(cancelAction, okAction)
    }
}