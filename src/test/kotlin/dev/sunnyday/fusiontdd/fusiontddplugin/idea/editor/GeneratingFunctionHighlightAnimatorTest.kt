package dev.sunnyday.fusiontdd.fusiontddplugin.idea.editor

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.use
import com.intellij.psi.PsiFile
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import dev.sunnyday.fusiontdd.fusiontddplugin.test.getClassFunction
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.awt.Graphics

class GeneratingFunctionHighlightAnimatorTest : LightJavaCodeInsightFixtureTestCase5() {

    private val animator = GeneratingFunctionHighlightAnimator()

    override fun getTestDataPath() = "testdata"

    @RunsInEdt
    @Test
    fun `on animate add highlight component`() {
        addAndOpenTargetClassFile()
        val targetFun = fixture.getClassFunction("TargetClass.targetFun")
        val currentComponents = fixture.editor.contentComponent.components.toSet()

        val newComponents = animator.animate(targetFun, fixture.editor).use {
            fixture.editor.contentComponent.components.filterNot(currentComponents::contains)
        }

        assertThat(newComponents).hasSize(1)
    }

    @RunsInEdt
    @Test
    fun `on dispose animation remove highlight component`() {
        addAndOpenTargetClassFile()
        val targetFun = fixture.getClassFunction("TargetClass.targetFun")
        val currentComponents = fixture.editor.contentComponent.components.toSet()

        animator.animate(targetFun, fixture.editor).dispose()

        val newComponents = fixture.editor.contentComponent.components.filterNot(currentComponents::contains)

        assertThat(newComponents).isEmpty()
    }

    @RunsInEdt
    @Test
    fun `on animate, highlight function body`() {
        addAndOpenTargetClassFile()
        val targetFun = fixture.getClassFunction("TargetClass.targetFun")
        val currentComponents = fixture.editor.contentComponent.components.toSet()
        val graphics = mockk<Graphics>(relaxed = true) {
            every { create() } returns this
            every { create(any(), any(), any(), any()) } returns this
        }

        val highlight = animator.animate(targetFun, fixture.editor).use {
            fixture.editor.contentComponent.components.first { it !in currentComponents }
        }

        highlight.paint(graphics)

        verify {
            graphics.drawRoundRect(
                4 * CHAR_WIDTH, fixture.editor.lineHeight * 3,
                17 * CHAR_WIDTH, fixture.editor.lineHeight,
                10, 10
            )
        }
    }

    private fun addAndOpenTargetClassFile(): PsiFile {
        val file = fixture.addFileToProject(
            "src/main/kotlin/project/TargetClass.kt",
            """
                class TargetClass {
                
                    fun targetFun() {
                        // do nothing
                    }
                }
            """.trimIndent()
        )

        fixture.openFileInEditor(file.virtualFile)

        return file
    }

    private companion object {

        const val CHAR_WIDTH = 8
    }
}