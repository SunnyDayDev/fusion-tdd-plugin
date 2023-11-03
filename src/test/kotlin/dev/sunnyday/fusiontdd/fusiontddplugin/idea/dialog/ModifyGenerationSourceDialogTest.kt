package dev.sunnyday.fusiontdd.fusiontddplugin.idea.dialog

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapperPeer
import com.intellij.openapi.ui.DialogWrapperPeerFactory
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.components.JBTextArea
import com.intellij.util.application
import dev.sunnyday.fusiontdd.fusiontddplugin.test.requireChildByName
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.spyk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.JComponent
import kotlin.properties.Delegates

class ModifyGenerationSourceDialogTest : LightJavaCodeInsightFixtureTestCase5() {

    private val contentPaneSlot = CapturingSlot<JComponent>()
    private var originalPeerFactory: DialogWrapperPeerFactory by Delegates.notNull()

    override fun getTestDataPath(): String = "testdata"

    @BeforeEach
    fun setUp() {
        originalPeerFactory = application.service<DialogWrapperPeerFactory>()

        val peerFactory = spyk(originalPeerFactory)
        every { peerFactory.createPeer(any<DialogWrapper>(), any<Project>(), any(), any()) } answers {
            spyk(invocation.originalCall.invoke() as DialogWrapperPeer) {
                every { setContentPane(capture(contentPaneSlot)) } answers { invocation.originalCall.invoke() }
            }
        }

        application.replaceService(
            serviceInterface = DialogWrapperPeerFactory::class.java,
            instance = peerFactory,
            parentDisposable = { }
        )
    }

    @Test
    fun `dialog has dimensions service key`() = runInEdtAndWait {
        val dialog = ModifyGenerationSourceDialog()
        assertThat(dialog.dimensionKey).isNotEmpty()
    }

    @Test
    fun `on set code block, fill code area`() = runInEdtAndWait {
        val dialog = ModifyGenerationSourceDialog()
        dialog.setCodeBlock(rawCode = "2 + 2")

        val textArea = contentPaneSlot.captured.requireChildByName<JBTextArea>("codeArea")

        assertThat(textArea.text).isEqualTo("2 + 2")
    }


    @Test
    fun `on get code block, get it from code area`() = runInEdtAndWait {
        val dialog = ModifyGenerationSourceDialog()
        dialog.setCodeBlock(rawCode = "2 + 2")

        val textArea = contentPaneSlot.captured.requireChildByName<JBTextArea>("codeArea")
        textArea.text = "2 * 2"

        assertThat(dialog.getCodeBlock()).isEqualTo("2 * 2")
    }
}