package dev.sunnyday.fusiontdd.fusiontddplugin.idea.action

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import com.intellij.testFramework.registerServiceInstance
import com.intellij.testFramework.runInEdtAndGet
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings.FusionTDDSettings
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.properties.Delegates

class CodeGenerateActionGroupTest : LightJavaCodeInsightFixtureTestCase5() {

    private val settings = mockk<FusionTDDSettings>()

    private val dataContext = mockk<DataContext>()
    private val action = mockk<AnActionEvent>()
    private val caret = mockk<Caret>()

    private var targetClassFile: PsiFile by Delegates.notNull()

    private val actionsGroup = CodeGenerateActionGroup()

    override fun getTestDataPath(): String = "testdata"

    @BeforeEach
    fun setUp() {
        targetClassFile = fixture.copyFileToProject("action/enabled/NoTestClass.kt").let { file ->
            runReadAction {
                file.toPsiFile(fixture.project)
                    .let(::requireNotNull)
            }
        }

        fixture.project.registerServiceInstance(FusionTDDSettings::class.java, settings)
        settings.apply {
            every { projectPackage } returns "project"
            every { authToken } returns "asgfasdk898suhdf"
            every { starcoderModel } returns "starcoder"
        }

        caret.apply {
            every { offset } returns 101
        }

        dataContext.apply {
            every { getData<Project>(DataKey.create("project")) } answers { fixture.project }
            every { getData("project") } answers { fixture.project }
            every { getData(CommonDataKeys.PSI_FILE) } answers { targetClassFile }
            every { getData(LangDataKeys.CARET) } answers { caret }
        }

        action.apply {
            every { dataContext } returns this@CodeGenerateActionGroupTest.dataContext
        }
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `if action triggered in file with caret at function, show actions`() {
        val actions = runReadAction { actionsGroup.getChildren(action) }

        assertThat(actions).isNotEmpty()
    }

    @Test
    fun `if no action, hide actions`() {
        val actions = actionsGroup.getChildren(null)

        assertThat(actions).isEmpty()
    }

    @Test
    fun `if no project, hide actions`() {
        every { dataContext.getData("project") } returns null
        every { dataContext.getData<Project>(DataKey.create("project")) } returns null

        val actions = runReadAction { actionsGroup.getChildren(action) }

        assertThat(actions).isEmpty()
    }

    @Test
    fun `if authToken isn't defined in settings, hide actions`() {
        every { settings.authToken } returns null

        val actions = runReadAction { actionsGroup.getChildren(action) }

        assertThat(actions).isEmpty()
    }

    @Test
    fun `if project package isn't defined in settings, hide actions`() {
        every { settings.projectPackage } returns null

        val actions = runReadAction { actionsGroup.getChildren(action) }

        assertThat(actions).isEmpty()
    }

    @Test
    fun `if starcoder model isn't defined in settings, hide actions`() {
        every { settings.starcoderModel } returns null

        val actions = runReadAction { actionsGroup.getChildren(action) }

        assertThat(actions).isEmpty()
    }

    @Test
    fun `if no file, hide actions`() {
        every { dataContext.getData(CommonDataKeys.PSI_FILE) } returns null

        val actions = runReadAction { actionsGroup.getChildren(action) }

        assertThat(actions).isEmpty()
    }

    @Test
    fun `if no class function, hide actions`() {
        targetClassFile = runInEdtAndGet {
            fixture.copyFileToProject("action/enabled/NoClass.kt")
                .toPsiFile(fixture.project)
                .let(::requireNotNull)
        }

        val actions = runReadAction { actionsGroup.getChildren(action) }

        assertThat(actions).isEmpty()
    }

    @Test
    fun `if no caret, hide actions`() {
        every { dataContext.getData(LangDataKeys.CARET) } returns null

        val actions = runReadAction { actionsGroup.getChildren(action) }

        assertThat(actions).isEmpty()
    }

    @Test
    fun `if no element below caret, hide actions`() {
        every { caret.offset } returns 1_000

        val actions = runReadAction { actionsGroup.getChildren(action) }

        assertThat(actions).isEmpty()
    }
}