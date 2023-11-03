package dev.sunnyday.fusiontdd.fusiontddplugin.idea.dialog

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import java.awt.Dimension
import javax.swing.Action
import javax.swing.JComponent

internal class ModifyGenerationSourceDialog : DialogWrapper(true) {

    private val codeTextArea = JBTextArea().apply {
        name = "codeArea"
    }

    init {
        title = "Confirm Source"
        init()
    }

    override fun getDimensionServiceKey(): String {
        return "dev.sunnyday.fusiontdd.fusiontddplugin.ConfirmGenerationSourceDialog"
    }

    fun setCodeBlock(rawCode: String) {
        codeTextArea.text = rawCode
    }

    fun getCodeBlock(): String {
        return codeTextArea.text
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row { label("This source will be used for generation:") }
            row {
                cell(JBScrollPane(codeTextArea))
                    .applyToComponent { preferredSize = Dimension(800, 600) }
                    .align(Align.FILL)
            }
        }
    }

    override fun createActions(): Array<Action> {
        return arrayOf(cancelAction, okAction)
    }
}